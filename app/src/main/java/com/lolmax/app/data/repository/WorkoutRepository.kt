package com.lolmax.app.data.repository

import androidx.room.withTransaction
import com.lolmax.app.data.local.LolMaxDatabase
import com.lolmax.app.data.local.dao.SetDao
import com.lolmax.app.data.local.dao.WorkoutDao
import com.lolmax.app.data.local.dao.WorkoutExerciseDao
import com.lolmax.app.data.local.entities.Workout
import com.lolmax.app.data.local.entities.WorkoutExercise
import com.lolmax.app.data.local.entities.WorkoutSet
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class WorkoutSetDraft(
    val weightKg: Double?,
    val reps: Int,
    val isWarmup: Boolean,
)

data class WorkoutExerciseDraft(
    val exerciseId: String,
    val sets: List<WorkoutSetDraft>,
)

data class WorkoutDraft(
    val id: String? = null,
    val title: String,
    val dateEpochDay: Long,
    val startMinuteOfDay: Int? = null,
    val endMinuteOfDay: Int? = null,
    val bodyweightKg: Double? = null,
    val notes: String? = null,
    val templateId: String? = null,
    val exercises: List<WorkoutExerciseDraft> = emptyList(),
)

data class WorkoutExerciseWithSets(
    val exercise: WorkoutExercise,
    val sets: List<WorkoutSet>,
)

data class WorkoutWithExercises(
    val workout: Workout,
    val exercises: List<WorkoutExerciseWithSets>,
)

class WorkoutRepository(
    private val db: LolMaxDatabase,
    private val workoutDao: WorkoutDao,
    private val workoutExerciseDao: WorkoutExerciseDao,
    private val setDao: SetDao,
    @Suppress("unused") private val exerciseRepository: ExerciseRepository,
    private val profileRepository: ProfileRepository,
) {
    fun observeWorkouts(): Flow<List<Workout>> = workoutDao.observeAll()

    fun observeWorkout(id: String): Flow<Workout?> = workoutDao.observeById(id)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWorkoutDetail(id: String): Flow<WorkoutWithExercises?> =
        combine(
            workoutDao.observeById(id),
            workoutExerciseDao.observeForWorkout(id),
        ) { workout, exercises -> workout to exercises }
            .flatMapLatest { (workout, exercises) ->
                when {
                    workout == null -> flowOf(null)
                    exercises.isEmpty() -> flowOf(WorkoutWithExercises(workout, emptyList()))
                    else -> combine(
                        exercises.map { we ->
                            setDao.observeForExercise(we.id).map { sets ->
                                WorkoutExerciseWithSets(we, sets)
                            }
                        }
                    ) { detailList ->
                        WorkoutWithExercises(workout, detailList.toList())
                    }
                }
            }

    suspend fun getWorkout(id: String): Workout? = workoutDao.getById(id)

    data class WorkoutSummary(
        val workout: Workout,
        val exerciseCount: Int,
        val setCount: Int,
        val volumeKg: Double,
    )

    suspend fun getSummaries(): List<WorkoutSummary> {
        return workoutDao.getAllOnce()
            .sortedWith(
                compareByDescending<Workout> { it.dateEpochDay }
                    .thenByDescending { it.startMinuteOfDay ?: 0 },
            )
            .map { w ->
            val wes = workoutExerciseDao.getForWorkout(w.id)
            val sets = if (wes.isEmpty()) emptyList() else setDao.getForExercises(wes.map { it.id })
            val working = sets.filter { !it.isWarmup }
            WorkoutSummary(
                workout = w,
                exerciseCount = wes.size,
                setCount = working.size,
                volumeKg = working.sumOf { (it.weightKg ?: 0.0) * it.reps },
            )
        }
    }

    suspend fun saveDraft(draft: WorkoutDraft): String {
        val id = draft.id ?: UUID.randomUUID().toString()
        val bodyweight = draft.bodyweightKg ?: profileRepository.getBodyweightKg()
        val exercises = mutableListOf<WorkoutExercise>()
        val setsByExercise = mutableMapOf<String, List<WorkoutSet>>()
        draft.exercises.forEachIndexed { exIndex, exDraft ->
            val weId = "$id-ex-$exIndex"
            exercises += WorkoutExercise(
                id = weId,
                workoutId = id,
                exerciseId = exDraft.exerciseId,
                orderInWorkout = exIndex,
            )
            setsByExercise[weId] = exDraft.sets.mapIndexed { setIndex, s ->
                WorkoutSet(
                    id = "$weId-set-$setIndex",
                    workoutExerciseId = weId,
                    weightKg = s.weightKg,
                    reps = s.reps,
                    isWarmup = s.isWarmup,
                    orderInExercise = setIndex,
                )
            }
        }
        db.withTransaction {
            workoutDao.upsert(
                Workout(
                    id = id,
                    title = draft.title,
                    dateEpochDay = draft.dateEpochDay,
                    startMinuteOfDay = draft.startMinuteOfDay,
                    endMinuteOfDay = draft.endMinuteOfDay,
                    bodyweightKg = bodyweight,
                    notes = draft.notes,
                    templateId = draft.templateId,
                )
            )
            workoutExerciseDao.deleteForWorkout(id)
            workoutExerciseDao.upsertAll(exercises)
            setDao.replaceSetsForWorkout(id, setsByExercise)
        }
        return id
    }

    suspend fun deleteWorkout(id: String) {
        db.withTransaction {
            workoutDao.deleteById(id)
        }
    }
}
