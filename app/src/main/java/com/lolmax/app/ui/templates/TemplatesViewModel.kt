package com.lolmax.app.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolmax.app.data.local.entities.Exercise
import com.lolmax.app.data.repository.ExerciseRepository
import com.lolmax.app.data.repository.TemplateDraft
import com.lolmax.app.data.repository.TemplateExerciseDraft
import com.lolmax.app.data.repository.TemplateRepository
import com.lolmax.app.data.repository.TemplateSetDraft
import com.lolmax.app.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemplateListItemUi(
    val id: String,
    val name: String,
    val exerciseCount: Int,
    val setCount: Int,
)

data class TemplatesUiState(
    val items: List<TemplateListItemUi> = emptyList(),
    val isLoading: Boolean = true,
)

class TemplatesViewModel(
    private val templateRepository: TemplateRepository,
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)

    val uiState: StateFlow<TemplatesUiState> = combine(
        templateRepository.observeTemplates(),
        refreshTick,
    ) { templates, _ ->
        TemplatesUiState(items = templates.map {
            TemplateListItemUi(it.id, it.name, 0, 0)
        }, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemplatesUiState())

    private val _details = MutableStateFlow<Map<String, Pair<Int, Int>>>(emptyMap())

    init {
        viewModelScope.launch {
            templateRepository.observeTemplates().collect { templates ->
                val map = templates.associate { t ->
                    val detail = templateRepository.getTemplateWithExercises(t.id)
                    val exCount = detail?.exercises?.size ?: 0
                    val setCount = detail?.exercises?.sumOf { it.sets.size } ?: 0
                    t.id to (exCount to setCount)
                }
                _details.value = map
            }
        }
    }

    val enrichedUiState: StateFlow<TemplatesUiState> = combine(uiState, _details) { state, details ->
        state.copy(items = state.items.map {
            val (ex, sets) = details[it.id] ?: (0 to 0)
            it.copy(exerciseCount = ex, setCount = sets)
        })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemplatesUiState())

    fun delete(id: String) {
        viewModelScope.launch { templateRepository.deleteTemplate(id) }
    }
}

data class TemplateSetInput(
    val weightText: String = "",
    val repsText: String = "",
    val isWarmup: Boolean = false,
)

data class TemplateEditExercise(
    val exerciseId: String,
    val name: String,
    val sets: MutableList<TemplateSetInput> = mutableListOf(TemplateSetInput()),
)

data class TemplateEditUiState(
    val name: String = "",
    val exercises: List<TemplateEditExercise> = emptyList(),
    val pickerOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Exercise> = emptyList(),
    val saved: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val templateId: String? = null,
)

class TemplateEditViewModel(
    private val templateRepository: TemplateRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TemplateEditUiState())
    val uiState: StateFlow<TemplateEditUiState> = _state

    private var loaded = false

    fun init(templateId: String?) {
        if (loaded) return
        loaded = true
        _state.update { it.copy(templateId = templateId) }
        if (templateId == null) return
        viewModelScope.launch {
            val detail = templateRepository.getTemplateWithExercises(templateId) ?: return@launch
            val exercises = detail.exercises.map { te ->
                val ex = exerciseRepository.getById(te.exercise.exerciseId)
                TemplateEditExercise(
                    exerciseId = te.exercise.exerciseId,
                    name = ex?.name ?: te.exercise.exerciseId,
                    sets = te.sets.map {
                        TemplateSetInput(
                            weightText = it.weightKg?.toString() ?: "",
                            repsText = it.reps.toString(),
                            isWarmup = it.isWarmup,
                        )
                    }
                        .toMutableList()
                        .apply { if (isEmpty()) add(TemplateSetInput()) },
                )
            }
            _state.update { it.copy(name = detail.template.name, exercises = exercises) }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v) }

    fun openPicker() {
        _state.update { it.copy(pickerOpen = true, searchQuery = "") }
        viewModelScope.launch {
            val all = exerciseRepository.getAll()
            _state.update { it.copy(searchResults = all.take(50)) }
        }
    }

    fun closePicker() = _state.update { it.copy(pickerOpen = false) }

    fun search(q: String) {
        viewModelScope.launch {
            val all = exerciseRepository.getAll()
            val filtered = if (q.isBlank()) all.take(50)
            else all.filter { it.name.contains(q, true) }
            _state.update { it.copy(searchQuery = q, searchResults = filtered) }
        }
    }

    fun addExercise(ex: Exercise) {
        _state.update { s ->
            s.copy(
                pickerOpen = false,
                exercises = s.exercises + TemplateEditExercise(ex.id, ex.name),
            )
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
            sets += sets.lastOrNull() ?: TemplateSetInput()
            exercises[exIndex] = ex.copy(sets = sets)
            s.copy(exercises = exercises)
        }
    }

    fun updateSet(exIndex: Int, setIndex: Int, transform: (TemplateSetInput) -> TemplateSetInput) {
        _state.update { s ->
            val exercises = s.exercises.toMutableList()
            val ex = exercises[exIndex]
            val sets = ex.sets.toMutableList()
            if (setIndex in sets.indices) {
                sets[setIndex] = transform(sets[setIndex])
            }
            exercises[exIndex] = ex.copy(sets = sets)
            s.copy(exercises = exercises)
        }
    }

    fun save() {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val s = _state.value
                val drafts = s.exercises.map { ex ->
                    TemplateExerciseDraft(
                        exerciseId = ex.exerciseId,
                        sets = ex.sets.mapNotNull { set ->
                            val reps = set.repsText.toIntOrNull() ?: return@mapNotNull null
                            if (reps <= 0) return@mapNotNull null
                            val weight = set.weightText.toDoubleOrNull()
                                ?.takeIf { it >= 0 }
                            TemplateSetDraft(weight, reps, set.isWarmup)
                        },
                    )
                }.filter { it.sets.isNotEmpty() }
                if (drafts.isEmpty()) {
                    _state.update { it.copy(saving = false, error = "Add at least one set") }
                    return@launch
                }
                templateRepository.saveDraft(
                    TemplateDraft(
                        id = s.templateId,
                        name = s.name.ifBlank { "Template" },
                        exercises = drafts,
                    )
                )
                _state.update { it.copy(saved = true, saving = false) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: "Save failed") }
            }
        }
    }
}
