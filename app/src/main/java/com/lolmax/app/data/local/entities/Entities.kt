package com.lolmax.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val gender: String = "MALE",
    val age: Int = 25,
    val bodyweightKg: Double = 70.0,
    val heightCm: Double = 175.0,
    val weightUnit: String = "lb",
    val themeMode: String = "system",
)

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val slug: String,
    val bodyPart: String,
    val category: String,
    val isBodyweight: Boolean = false,
    val isCustom: Boolean = false,
)

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey val id: String,
    val title: String,
    val dateEpochDay: Long,
    val startMinuteOfDay: Int? = null,
    val endMinuteOfDay: Int? = null,
    val bodyweightKg: Double? = null,
    val notes: String? = null,
    val templateId: String? = null,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        ),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExercise(
    @PrimaryKey val id: String,
    val workoutId: String,
    val exerciseId: String,
    val orderInWorkout: Int,
    val restSeconds: Int? = null,
)

@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExercise::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("workoutExerciseId")],
)
data class WorkoutSet(
    @PrimaryKey val id: String,
    val workoutExerciseId: String,
    val weightKg: Double?,
    val reps: Int,
    val isWarmup: Boolean = false,
    val orderInExercise: Int,
)

@Entity(tableName = "templates")
data class Template(
    @PrimaryKey val id: String,
    val name: String,
    val orderIndex: Int,
)

@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = Template::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT
        ),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class TemplateExercise(
    @PrimaryKey val id: String,
    val templateId: String,
    val exerciseId: String,
    val orderInTemplate: Int,
)

@Entity(
    tableName = "template_sets",
    foreignKeys = [
        ForeignKey(
            entity = TemplateExercise::class,
            parentColumns = ["id"],
            childColumns = ["templateExerciseId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("templateExerciseId")],
)
data class TemplateSet(
    @PrimaryKey val id: String,
    val templateExerciseId: String,
    val weightKg: Double?,
    val reps: Int,
    val isWarmup: Boolean = false,
    val orderInExercise: Int,
)

@Entity(
    tableName = "goals",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("exerciseId")],
)
data class Goal(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val targetWeightKg: Double?,
    val targetReps: Int,
    val startDateEpochDay: Long? = null,
    val targetDateEpochDay: Long? = null,
    val isCompleted: Boolean = false,
)
