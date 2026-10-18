package com.lolmax.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lolmax.app.data.local.dao.CustomExerciseDao
import com.lolmax.app.data.local.dao.ExerciseDao
import com.lolmax.app.data.local.dao.GoalDao
import com.lolmax.app.data.local.dao.SetDao
import com.lolmax.app.data.local.dao.TemplateDao
import com.lolmax.app.data.local.dao.TemplateExerciseDao
import com.lolmax.app.data.local.dao.TemplateSetDao
import com.lolmax.app.data.local.dao.UserProfileDao
import com.lolmax.app.data.local.dao.WorkoutDao
import com.lolmax.app.data.local.dao.WorkoutExerciseDao
import com.lolmax.app.data.local.entities.Exercise
import com.lolmax.app.data.local.entities.Goal
import com.lolmax.app.data.local.entities.Template
import com.lolmax.app.data.local.entities.TemplateExercise
import com.lolmax.app.data.local.entities.TemplateSet
import com.lolmax.app.data.local.entities.UserProfile
import com.lolmax.app.data.local.entities.Workout
import com.lolmax.app.data.local.entities.WorkoutExercise
import com.lolmax.app.data.local.entities.WorkoutSet

@Database(
    entities = [
        UserProfile::class,
        Exercise::class,
        Workout::class,
        WorkoutExercise::class,
        WorkoutSet::class,
        Template::class,
        TemplateExercise::class,
        TemplateSet::class,
        Goal::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LolMaxDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun customExerciseDao(): CustomExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutExerciseDao(): WorkoutExerciseDao
    abstract fun setDao(): SetDao
    abstract fun templateDao(): TemplateDao
    abstract fun templateExerciseDao(): TemplateExerciseDao
    abstract fun templateSetDao(): TemplateSetDao
    abstract fun goalDao(): GoalDao

    companion object {
        @Volatile
        private var instance: LolMaxDatabase? = null

        fun getInstance(context: Context): LolMaxDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }
        }

        private fun build(context: Context): LolMaxDatabase {
            val db = Room.databaseBuilder(
                context.applicationContext,
                LolMaxDatabase::class.java,
                "lolmax.db"
            )
                // Local-only app: on a schema version bump (no migrations authored yet),
                // rebuild rather than crash with IllegalStateException.
                .fallbackToDestructiveMigration()
                .build()
            // Seed exercise catalog on first launch (async, idempotent).
            SeedingCoroutine.start(context.applicationContext, db)
            return db
        }
    }
}
