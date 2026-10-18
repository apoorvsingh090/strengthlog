package com.lolmax.app.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolmax.app.data.repository.ExerciseRepository
import com.lolmax.app.data.repository.ProfileRepository
import com.lolmax.app.data.repository.StatsRepository
import com.lolmax.app.data.repository.StandardsRepository
import com.lolmax.app.data.repository.WorkoutRepository
import com.lolmax.app.data.repository.WorkoutWithExercises
import com.lolmax.app.domain.LiftingLevel
import com.lolmax.app.domain.OneRepMax
import com.lolmax.app.domain.StandardsEngine
import com.lolmax.app.ui.util.Units
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DetailSetRow(
    val weightLabel: String,
    val reps: Int,
    val isWarmup: Boolean,
    val pctLabel: String,
    val stars: Int,
    val level: LiftingLevel?,
    val percentile: Double?,
)

data class DetailExerciseUi(
    val rowId: String,
    val exerciseId: String,
    val name: String,
    val rows: List<DetailSetRow>,
    val summary: String,
    val bestLabel: String,
)

data class WorkoutDetailUiState(
    val title: String = "",
    val dateLabel: String = "",
    val timeLabel: String = "",
    val bodyweightLabel: String = "",
    val statsLabel: String = "",
    val exercises: List<DetailExerciseUi> = emptyList(),
    val unit: String = "lb",
    val isLoading: Boolean = true,
)

class WorkoutDetailViewModel(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    @Suppress("unused") private val statsRepository: StatsRepository,
    private val profileRepository: ProfileRepository,
    private val standardsRepository: StandardsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutDetailUiState())
    val uiState: StateFlow<WorkoutDetailUiState> = _state

    private var currentWorkoutId: String? = null

    fun load(workoutId: String) {
        if (currentWorkoutId == workoutId && !_state.value.isLoading) return
        currentWorkoutId = workoutId
        viewModelScope.launch {
            combine(
                workoutRepository.observeWorkoutDetail(workoutId),
                profileRepository.observeProfile(),
            ) { detail, profile -> detail to profile }
                .collect { (detail, profile) ->
                    _state.value = buildState(detail, profile.weightUnit, profile.gender == "MALE", profile.bodyweightKg)
                }
        }
    }

    private suspend fun buildState(
        detail: WorkoutWithExercises?,
        unit: String,
        isMale: Boolean,
        defaultBwKg: Double,
    ): WorkoutDetailUiState {
        if (detail == null) return WorkoutDetailUiState(isLoading = false)
        val w = detail.workout
        val date = LocalDate.ofEpochDay(w.dateEpochDay)
        val dateLabel =
            "${date.dayOfMonth} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.year}"
        val timeLabel = if (w.startMinuteOfDay != null && w.endMinuteOfDay != null) {
            val s = w.startMinuteOfDay!!
            val e = w.endMinuteOfDay!!
            fun fmt(m: Int): String {
                val h = m / 60
                val mm = m % 60
                val ampm = if (h >= 12) "pm" else "am"
                val h12 = when {
                    h == 0 -> 12
                    h > 12 -> h - 12
                    else -> h
                }
                return "$h12:${mm.toString().padStart(2, '0')} $ampm"
            }
            "${fmt(s)} - ${fmt(e)}"
        } else ""

        var totalSets = 0
        var totalReps = 0
        var totalVolume = 0.0
        val bw = w.bodyweightKg ?: defaultBwKg

        val exercises = detail.exercises.map { ewe ->
            val exercise = exerciseRepository.getById(ewe.exercise.exerciseId)
            val name = exercise?.name ?: ewe.exercise.exerciseId
            val working = ewe.sets.filter { !it.isWarmup }
            totalSets += working.size
            totalReps += working.sumOf { it.reps }
            totalVolume += working.sumOf { (it.weightKg ?: 0.0) * it.reps }

            val stdThresholds =
                standardsRepository.getThresholds(ewe.exercise.exerciseId, isMale, bw)
            val bestSet =
                working.maxByOrNull { OneRepMax.estimate(it.weightKg ?: 0.0, it.reps) }
            val bestLabel = if (bestSet == null) "—" else {
                val wPart =
                    if (exercise?.isBodyweight == true) "BW" else Units.kgToDisplay(bestSet.weightKg, unit)
                "$wPart x ${bestSet.reps}"
            }

            val rows = ewe.sets.map { s ->
                val orm = OneRepMax.estimate(s.weightKg ?: 0.0, s.reps)
                val level = if (s.isWarmup || stdThresholds == null) null
                else StandardsEngine.levelFor(orm, stdThresholds)
                val percentile = if (s.isWarmup || stdThresholds == null) null
                else StandardsEngine.percentileFor(orm, stdThresholds)
                val pct = if (s.isWarmup) null else OneRepMax.percentOf1rm(s.reps).toInt()
                DetailSetRow(
                    weightLabel = if (exercise?.isBodyweight == true && (s.weightKg
                            ?: 0.0) <= 0.01
                    ) "BW" else Units.kgToDisplay(s.weightKg, unit),
                    reps = s.reps,
                    isWarmup = s.isWarmup,
                    pctLabel = if (s.isWarmup) "warmup" else "$pct%",
                    stars = level?.stars ?: 0,
                    level = level,
                    percentile = percentile,
                )
            }
            val repCounts = working.map { it.reps }
            val repsLabel = when {
                repCounts.isEmpty() -> "0"
                repCounts.min() == repCounts.max() -> repCounts.first().toString()
                else -> "${repCounts.min()}-${repCounts.max()}"
            }
            DetailExerciseUi(
                rowId = ewe.exercise.id,
                exerciseId = ewe.exercise.exerciseId,
                name = name,
                rows = rows,
                summary = "${working.size} sets · $repsLabel reps",
                bestLabel = "best $bestLabel",
            )
        }

        val duration = StatsRepository.durationMinutes(w)
        val statsLabel = buildList {
            duration?.let { add(com.lolmax.app.ui.util.formatDuration(it)) }
            add("BW ${Units.kgToDisplay(w.bodyweightKg, unit)}")
            add("$totalSets sets")
            add("$totalReps reps")
            add(Units.formatVolume(totalVolume, unit))
        }.joinToString(" · ")

        return WorkoutDetailUiState(
            title = w.title,
            dateLabel = dateLabel,
            timeLabel = timeLabel,
            bodyweightLabel = Units.kgToDisplay(w.bodyweightKg, unit),
            statsLabel = statsLabel,
            exercises = exercises,
            unit = unit,
            isLoading = false,
        )
    }

    fun delete(workoutId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            workoutRepository.deleteWorkout(workoutId)
            onDone()
        }
    }
}
