package com.lolmax.app.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolmax.app.data.local.entities.Exercise
import com.lolmax.app.data.repository.ExerciseRepository
import com.lolmax.app.data.repository.ProfileRepository
import com.lolmax.app.data.repository.TemplateRepository
import com.lolmax.app.domain.BodyPart
import com.lolmax.app.domain.EquipmentCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExercisesUiState(
    val all: List<Exercise> = emptyList(),
    val custom: List<Exercise> = emptyList(),
    val query: String = "",
    val bodyPartFilter: String? = null,
    val categoryFilter: String? = null,
    val results: List<Exercise> = emptyList(),
    val pickerMode: Boolean = false,
)

class ExercisesViewModel(
    private val exerciseRepository: ExerciseRepository,
    @Suppress("unused") private val templateRepository: TemplateRepository,
    @Suppress("unused") private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val bodyPart = MutableStateFlow<String?>(null)
    private val category = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ExercisesUiState> = combine(
        exerciseRepository.observeWithFrequency(),
        exerciseRepository.observeCustom(),
        query,
        bodyPart,
        category,
    ) { withFreq, custom, q, bp, cat ->
        val all = withFreq.map { it.exercise }
        fun matches(ex: Exercise) =
            (q.isBlank() || ex.name.contains(q, true)) &&
                (bp == null || ex.bodyPart == bp) &&
                (cat == null || ex.category == cat)
        val filtered = all.filter { matches(it) }
        val customFiltered = custom.filter { matches(it) }
        val customIds = customFiltered.mapTo(HashSet()) { it.id }
        ExercisesUiState(
            all = all,
            custom = customFiltered,
            query = q,
            bodyPartFilter = bp,
            categoryFilter = cat,
            results = filtered.filterNot { it.id in customIds },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExercisesUiState())

    fun setQuery(q: String) { query.value = q }
    fun setBodyPart(bp: String?) { bodyPart.value = bp }
    fun setCategory(cat: String?) { category.value = cat }

    fun createCustom(name: String, bodyPart: String, category: String, isBodyweight: Boolean) {
        viewModelScope.launch {
            exerciseRepository.createCustom(name, bodyPart, category, isBodyweight)
        }
    }

    val bodyParts = BodyPart.entries.map { it.name }
    val categories = EquipmentCategory.entries.map { it.name }
}
