package com.lolmax.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lolmax.app.data.local.entities.Exercise
import com.lolmax.app.data.local.entities.Goal
import com.lolmax.app.data.local.entities.Template
import com.lolmax.app.data.local.entities.TemplateExercise
import com.lolmax.app.data.local.entities.TemplateSet
import com.lolmax.app.data.local.entities.UserProfile
import com.lolmax.app.data.local.entities.Workout
import com.lolmax.app.data.local.entities.WorkoutExercise
import com.lolmax.app.data.local.entities.WorkoutSet
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile)
}

data class ExerciseWithFrequency(
    @androidx.room.Embedded val exercise: Exercise,
    val frequency: Int,
)

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeAll(): Flow<List<Exercise>>

    @Query(
        "SELECT e.*, (SELECT COUNT(*) FROM workout_exercises we WHERE we.exerciseId = e.id) AS frequency " +
            "FROM exercises e ORDER BY frequency DESC, e.name ASC",
    )
    fun observeWithFrequency(): Flow<List<ExerciseWithFrequency>>

    @Query("SELECT * FROM exercises ORDER BY name")
    suspend fun getAll(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): Exercise?

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<Exercise>

    @Query("SELECT * FROM exercises WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): Exercise?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<Exercise>)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int
}

@Dao
interface CustomExerciseDao {
    @Query("SELECT * FROM exercises WHERE isCustom = 1 ORDER BY name")
    fun observeCustom(): Flow<List<Exercise>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exercise: Exercise)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY dateEpochDay DESC, startMinuteOfDay DESC")
    fun observeAll(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts ORDER BY dateEpochDay ASC")
    suspend fun getAllOnce(): List<Workout>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: String): Workout?

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeById(id: String): Flow<Workout?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workout: Workout)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM workouts WHERE dateEpochDay >= :sinceEpochDay ORDER BY dateEpochDay")
    suspend fun getSince(sinceEpochDay: Long): List<Workout>
}

@Dao
interface WorkoutExerciseDao {
    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY orderInWorkout")
    fun observeForWorkout(workoutId: String): Flow<List<WorkoutExercise>>

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY orderInWorkout")
    suspend fun getForWorkout(workoutId: String): List<WorkoutExercise>

    @Query("SELECT * FROM workout_exercises WHERE exerciseId = :exerciseId")
    suspend fun getByExercise(exerciseId: String): List<WorkoutExercise>

    @Query(
        "SELECT we.* FROM workout_exercises we " +
            "INNER JOIN workouts w ON w.id = we.workoutId " +
            "WHERE we.exerciseId = :exerciseId " +
            "ORDER BY w.dateEpochDay DESC, COALESCE(w.startMinuteOfDay, 0) DESC",
    )
    suspend fun getByExerciseRecentFirst(exerciseId: String): List<WorkoutExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WorkoutExercise>)

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun deleteForWorkout(workoutId: String)
}

@Dao
interface SetDao {
    @Query("SELECT * FROM sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY orderInExercise")
    fun observeForExercise(workoutExerciseId: String): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM sets WHERE workoutExerciseId IN (:ids) ORDER BY orderInExercise")
    suspend fun getForExercises(ids: List<String>): List<WorkoutSet>

    @Query("SELECT * FROM sets s INNER JOIN workout_exercises we ON s.workoutExerciseId = we.id WHERE we.exerciseId = :exerciseId")
    suspend fun getByExerciseId(exerciseId: String): List<WorkoutSet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WorkoutSet>)

    @Query("DELETE FROM sets WHERE workoutExerciseId IN (SELECT id FROM workout_exercises WHERE workoutId = :workoutId)")
    suspend fun deleteForWorkout(workoutId: String)

    @Transaction
    suspend fun replaceSetsForWorkout(
        workoutId: String,
        setsByExercise: Map<String, List<WorkoutSet>>,
    ) {
        deleteForWorkout(workoutId)
        upsertAll(setsByExercise.values.flatten())
    }
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY orderIndex")
    fun observeAll(): Flow<List<Template>>

    @Query("SELECT * FROM templates WHERE id = :id")
    fun observeById(id: String): Flow<Template?>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: String): Template?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: Template)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM templates")
    suspend fun nextOrderIndex(): Int
}

@Dao
interface TemplateExerciseDao {
    @Query("SELECT * FROM template_exercises WHERE templateId = :templateId ORDER BY orderInTemplate")
    fun observeForTemplate(templateId: String): Flow<List<TemplateExercise>>

    @Query("SELECT * FROM template_exercises WHERE templateId = :templateId ORDER BY orderInTemplate")
    suspend fun getForTemplate(templateId: String): List<TemplateExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TemplateExercise>)

    @Query("DELETE FROM template_exercises WHERE templateId = :templateId")
    suspend fun deleteForTemplate(templateId: String)
}

@Dao
interface TemplateSetDao {
    @Query("SELECT * FROM template_sets WHERE templateExerciseId IN (:ids) ORDER BY orderInExercise")
    suspend fun getForExercises(ids: List<String>): List<TemplateSet>

    @Query("SELECT * FROM template_sets WHERE templateExerciseId IN (:ids) ORDER BY orderInExercise")
    fun observeForExercises(ids: List<String>): Flow<List<TemplateSet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TemplateSet>)

    @Query("DELETE FROM template_sets WHERE templateExerciseId IN (SELECT id FROM template_exercises WHERE templateId = :templateId)")
    suspend fun deleteForTemplate(templateId: String)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE exerciseId = :exerciseId")
    fun observeForExercise(exerciseId: String): Flow<List<Goal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: Goal)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteById(id: String)
}
