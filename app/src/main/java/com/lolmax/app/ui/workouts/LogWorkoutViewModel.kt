package com.lolmax.app.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolmax.app.data.local.entities.Exercise
import com.lolmax.app.data.repository.ExerciseRepository
import com.lolmax.app.data.repository.ProfileRepository
import com.lolmax.app.data.repository.StatsRepository
import com.lolmax.app.data.repository.TemplateRepository
import com.lolmax.app.data.repository.WorkoutDraft
import com.lolmax.app.data.repository.WorkoutExerciseDraft
import com.lolmax.app.data.repository.WorkoutRepository
import com.lolmax.app.data.repository.WorkoutSetDraft
import com.lolmax.app.domain.OneRepMax
import com.lolmax.app.ui.util.Units
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetInput(
    val weightText: String = "",
    val repsText: String = "",
    val isWarmup: Boolean = false,
)

data class ExerciseInput(
    val exerciseId: String,
    val name: String,
    val isBodyweight: Boolean,
    val lastWeightText: String = "",
    val lastRepsText: String = "",
    val sets: MutableList<SetInput> = mutableListOf(),
)

data class LogWorkoutUiState(
    val title: String = "Workout",
    val date: LocalDate = LocalDate.now(),
    val bodyweightText: String = "",
    val startMinute: Int = 17 * 60,
    val endMinute: Int? = null,
    val exercises: List<ExerciseInput> = emptyList(),
    val pickerOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Exercise> = emptyList(),
    val unit: String = "lb",
    val savedId: String? = null,
    val error: String? = null,
    val saving: Boolean = false,
    val existingWorkoutId: String? = null,
    val templateId: String? = null,
)

class LogWorkoutViewModel(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val templateRepository: TemplateRepository,
    private val profileRepository: ProfileRepository,
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LogWorkoutUiState())
    val uiState: StateFlow<LogWorkoutUiState> = _state

    private var loaded = false

    fun init(workoutId: String?, templateId: String?) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val profile = profileRepository.getProfile()
            _state.update {
                it.copy(
                    unit = profile.weightUnit,
                    bodyweightText = Units.kgToNumber(profile.bodyweightKg, profile.weightUnit),
                    existingWorkoutId = workoutId,
                    templateId = templateId,
                )
            }
            templateId?.let { loadTemplate(it) }
            workoutId?.let { loadWorkout(it) }
        }
    }

    private suspend fun loadTemplate(templateId: String) {
        val t = templateRepository.getTemplateWithExercises(templateId) ?: return
        val exercises = t.exercises.map { te ->
            val ex = exerciseRepository.getById(te.exercise.exerciseId)
            val sets = te.sets.map {
                SetInput(
                    weightText = Units.kgToNumber(it.weightKg, _state.value.unit),
                    repsText = it.reps.toString(),
                    isWarmup = it.isWarmup,
                )
            }.toMutableList().apply {
                if (isEmpty()) add(SetInput())
            }
            ExerciseInput(
                exerciseId = te.exercise.exerciseId,
                name = ex?.name ?: te.exercise.exerciseId,
                isBodyweight = ex?.isBodyweight == true,
                sets = sets,
            )
        }
        _state.update {
            it.copy(
                title = t.template.name.ifBlank { it.title },
                exercises = exercises,
            )
        }
    }

    private suspend fun loadWorkout(workoutId: String) {
        val w = workoutRepository.getWorkout(workoutId) ?: return
        val detailFlow = workoutRepository.observeWorkoutDetail(workoutId)
        val first = detailFlow.first() ?: return
        val unit = _state.value.unit
        val inputs = first.exercises.map { ewe ->
            val ex = exerciseRepository.getById(ewe.exercise.exerciseId)
            ExerciseInput(
                exerciseId = ewe.exercise.exerciseId,
                name = ex?.name ?: ewe.exercise.exerciseId,
                isBodyweight = ex?.isBodyweight == true,
                sets = ewe.sets.map {
                    SetInput(
                        weightText = Units.kgToNumber(it.weightKg, unit),
                        repsText = it.reps.toString(),
                        isWarmup = it.isWarmup,
                    )
                }.toMutableList().apply { if (isEmpty()) add(SetInput()) },
            )
        }
        _state.update {
            it.copy(
                title = w.title,
                date = LocalDate.ofEpochDay(w.dateEpochDay),
                bodyweightText = Units.kgToNumber(w.bodyweightKg, unit),
                startMinute = w.startMinuteOfDay ?: it.startMinute,
                endMinute = w.endMinuteOfDay,
                exercises = inputs,
            )
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setDate(v: LocalDate) = _state.update { it.copy(date = v) }
    fun setBodyweight(v: String) = _state.update { it.copy(bodyweightText = v) }
    fun setEndNow() = _state.update {
        val now = java.time.LocalTime.now()
        it.copy(endMinute = now.hour * 60 + now.minute)
    }

    fun openPicker() {
        _state.update { it.copy(pickerOpen = true, searchQuery = "", searchResults = emptyList()) }
        viewModelScope.launch {
            val all = exerciseRepository.getAll()
            _state.update { it.copy(searchResults = all.take(50)) }
        }
    }

    fun closePicker() = _state.update { it.copy(pickerOpen = false) }

    fun search(query: String) {
        viewModelScope.launch {
            val all = exerciseRepository.getAll()
            val filtered = if (query.isBlank()) all.take(50)
            else all.filter { it.name.contains(query, ignoreCase = true) }
            _state.update { it.copy(searchQuery = query, searchResults = filtered) }
        }
    }

    fun addExercise(exercise: Exercise) {
        viewModelScope.launch {
            val last = statsRepository.lastPerformance(exercise.id)
            val best = statsRepository.bestSet(exercise.id)
            val unit = _state.value.unit
            val weightSource = best?.first ?: last?.first
            val repsSource = best?.second ?: last?.second
            val prefilled = SetInput(
                weightText = Units.kgToNumber(weightSource, unit),
                repsText = repsSource?.toString() ?: "",
            )
            _state.update { s ->
                s.copy(
                    pickerOpen = false,
                    exercises = s.exercises + ExerciseInput(
                        exerciseId = exercise.id,
                        name = exercise.name,
                        isBodyweight = exercise.isBodyweight,
                        lastWeightText = Units.kgToNumber(weightSource, unit),
                        lastRepsText = repsSource?.toString() ?: "",
                        sets = mutableListOf(prefilled),
                    ),
                )
            }
        }
    }

    fun removeExercise(index: Int) {
        _state.update { s ->
            s.copy(exercises = s.exercises.filterIndexed { i, _ -> i != index })
        }
    }

    fun addSet(exIndex: Int) {
        _state.update { s ->
            val exercises = s.exercises.toMutableList()
            val ex = exercises[exIndex]
            val sets = ex.sets.toMutableList()
            val lastWorking = sets.lastOrNull { !it.isWarmup }
            sets += SetInput(
                weightText = lastWorking?.weightText ?: ex.lastWeightText,
                repsText = lastWorking?.repsText ?: ex.lastRepsText,
            )
            exercises[exIndex] = ex.copy(sets = sets)
            s.copy(exercises = exercises)
        }
    }

    fun removeSet(exIndex: Int, SetIndex: Int) {
        _state.update { s ->
            val exercises = s.exercises.toMutableList()
            val ex = exercises[exIndex]
            if (ex.sets.size <= 1) return@update s
            val sets = ex.sets.toMutableList().apply { removeAt(SetIndex) }
            exercises[exIndex] = ex.copy(sets = sets)
            s.copy(exercises = exercises)
        }
    }

    fun updateSet(exIndex: Int, setIndex: Int, transform: (SetInput) -> SetInput) {
        _state.update { s ->
            val exercises = s.exercises.toMutableList()
            val ex = exercises[exIndex]
            val sets = ex.sets.toMutableList()
            sets[setIndex] = transform(sets[setIndex])
            exercises[exIndex] = ex.copy(sets = sets)
            s.copy(exercises = exercises)
        }
    }

    fun bestOneRmForExercise(exIndex: Int): Double {
        val ex = _state.value.exercises.getOrNull(exIndex) ?: return 0.0
        val unit = _state.value.unit
        return ex.sets.filter { !it.isWarmup }.maxOfOrNull { s ->
            val w = s.weightText.toDoubleOrNull() ?: 0.0
            val kg = Units.displayToKg(w, unit)
            val reps = s.repsText.toIntOrNull() ?: 0
            OneRepMax.estimate(kg, reps)
        } ?: 0.0
    }

    fun save() {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val s = _state.value
                val unit = s.unit
                val bwKg = s.bodyweightText.toDoubleOrNull()
                    ?.takeIf { it >= 0 }
                    ?.let { Units.displayToKg(it, unit) }
                val drafts = s.exercises.map { ex ->
                    WorkoutExerciseDraft(
                        exerciseId = ex.exerciseId,
                        sets = ex.sets.mapNotNull { set ->
                            val reps = set.repsText.toIntOrNull() ?: return@mapNotNull null
                            if (reps <= 0) return@mapNotNull null
                            val weight = set.weightText.toDoubleOrNull()
                                ?.takeIf { it >= 0 }
                                ?.let { Units.displayToKg(it, unit) }
                            WorkoutSetDraft(weightKg = weight, reps = reps, isWarmup = set.isWarmup)
                        },
                    )
                }.filter { it.sets.isNotEmpty() }
                if (drafts.isEmpty()) {
                    _state.update { it.copy(saving = false, error = "Add at least one set") }
                    return@launch
                }
                val id = workoutRepository.saveDraft(
                    WorkoutDraft(
                        id = s.existingWorkoutId,
                        title = s.title.ifBlank { "Workout" },
                        dateEpochDay = s.date.toEpochDay(),
                        startMinuteOfDay = s.startMinute,
                        endMinuteOfDay = s.endMinute,
                        bodyweightKg = bwKg,
                        templateId = s.templateId,
                        exercises = drafts,
                    )
                )
                _state.update { it.copy(savedId = id, saving = false) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: "Save failed") }
            }
        }
    }
}
