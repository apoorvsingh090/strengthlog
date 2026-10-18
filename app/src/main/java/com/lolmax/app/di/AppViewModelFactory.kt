package com.lolmax.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.lolmax.app.ui.exercises.ExerciseReportViewModel
import com.lolmax.app.ui.exercises.ExercisesViewModel
import com.lolmax.app.ui.profile.PowerliftingViewModel
import com.lolmax.app.ui.profile.ProfileViewModel
import com.lolmax.app.ui.profile.SettingsViewModel
import com.lolmax.app.ui.templates.TemplateEditViewModel
import com.lolmax.app.ui.templates.TemplatesViewModel
import com.lolmax.app.ui.workouts.LogWorkoutViewModel
import com.lolmax.app.ui.workouts.WorkoutDetailViewModel
import com.lolmax.app.ui.workouts.WorkoutsViewModel

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when {
            modelClass.isAssignableFrom(WorkoutsViewModel::class.java) ->
                WorkoutsViewModel(container.workoutRepository, container.profileRepository) as T
            modelClass.isAssignableFrom(WorkoutDetailViewModel::class.java) ->
                WorkoutDetailViewModel(container.workoutRepository, container.exerciseRepository, container.statsRepository, container.profileRepository, container.standardsRepository) as T
            modelClass.isAssignableFrom(LogWorkoutViewModel::class.java) ->
                LogWorkoutViewModel(container.workoutRepository, container.exerciseRepository, container.templateRepository, container.profileRepository, container.statsRepository) as T
            modelClass.isAssignableFrom(TemplatesViewModel::class.java) ->
                TemplatesViewModel(container.templateRepository) as T
            modelClass.isAssignableFrom(TemplateEditViewModel::class.java) ->
                TemplateEditViewModel(container.templateRepository, container.exerciseRepository) as T
            modelClass.isAssignableFrom(ExercisesViewModel::class.java) ->
                ExercisesViewModel(container.exerciseRepository, container.templateRepository, container.profileRepository) as T
            modelClass.isAssignableFrom(ExerciseReportViewModel::class.java) ->
                ExerciseReportViewModel(container.exerciseRepository, container.statsRepository, container.profileRepository, container.workoutRepository) as T
            modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
                ProfileViewModel(container.profileRepository, container.exerciseRepository, container.statsRepository) as T
            modelClass.isAssignableFrom(PowerliftingViewModel::class.java) ->
                PowerliftingViewModel(container.exerciseRepository, container.statsRepository, container.profileRepository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(container.profileRepository, container.csvExporter, container.csvImporter, container.workoutRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
