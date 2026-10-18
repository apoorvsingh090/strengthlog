package com.lolmax.app.di

import android.content.Context
import com.lolmax.app.data.csv.CsvExporter
import com.lolmax.app.data.csv.CsvImporter
import com.lolmax.app.data.local.LolMaxDatabase
import com.lolmax.app.data.repository.ExerciseRepository
import com.lolmax.app.data.repository.ProfileRepository
import com.lolmax.app.data.repository.StandardsRepository
import com.lolmax.app.data.repository.StatsRepository
import com.lolmax.app.data.repository.TemplateRepository
import com.lolmax.app.data.repository.WorkoutRepository

class AppContainer(context: Context) {
    val database: LolMaxDatabase = LolMaxDatabase.getInstance(context)
    val standardsRepository: StandardsRepository = StandardsRepository(context)
    val exerciseRepository: ExerciseRepository =
        ExerciseRepository(database.exerciseDao(), database.customExerciseDao(), standardsRepository)
    val profileRepository: ProfileRepository =
        ProfileRepository(database.userProfileDao(), context)
    val workoutRepository: WorkoutRepository =
        WorkoutRepository(
            database,
            database.workoutDao(),
            database.workoutExerciseDao(),
            database.setDao(),
            exerciseRepository,
            profileRepository
        )
    val templateRepository: TemplateRepository =
        TemplateRepository(
            database,
            database.templateDao(),
            database.templateExerciseDao(),
            database.templateSetDao(),
            exerciseRepository
        )
    val statsRepository: StatsRepository =
        StatsRepository(
            database.workoutDao(),
            database.workoutExerciseDao(),
            database.setDao(),
            exerciseRepository,
            standardsRepository,
            profileRepository
        )
    val csvExporter: CsvExporter = CsvExporter(database, exerciseRepository)
    val csvImporter: CsvImporter = CsvImporter(database, exerciseRepository)
}
