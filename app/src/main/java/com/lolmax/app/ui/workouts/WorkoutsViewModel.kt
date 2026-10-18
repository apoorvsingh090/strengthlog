package com.lolmax.app.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolmax.app.data.repository.ProfileRepository
import com.lolmax.app.data.repository.StatsRepository
import com.lolmax.app.data.repository.WorkoutRepository
import com.lolmax.app.ui.util.Units
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutListItem(
    val id: String,
    val title: String,
    val dateLabel: String,
    val durationLabel: String,
    val bodyweightLabel: String,
    val exerciseCount: Int,
    val setCount: Int,
    val volumeLabel: String,
)

data class WorkoutsUiState(
    val items: List<WorkoutListItem> = emptyList(),
    val unit: String = "lb",
    val profileName: String = "",
    val workoutCount: Int = 0,
    val isLoading: Boolean = true,
)

class WorkoutsViewModel(
    private val workoutRepository: WorkoutRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)
    private val _state = MutableStateFlow(WorkoutsUiState())
    val uiState: StateFlow<WorkoutsUiState> = _state

    init {
        viewModelScope.launch {
            combine(
                refreshTick,
                profileRepository.observeProfile(),
                workoutRepository.observeWorkouts(),
            ) { tick, profile, workouts -> Triple(tick, profile, workouts) }
                .collect { (_, profile, _) ->
                    refresh(profile.weightUnit, profile.name)
                }
        }
    }

    fun refresh() {
        refreshTick.update { it + 1 }
    }

    private suspend fun refresh(unit: String, name: String) {
        val summaries = workoutRepository.getSummaries()
        val items = summaries.map { s ->
            val w = s.workout
            val date = LocalDate.ofEpochDay(w.dateEpochDay)
            val dateLabel = when (date) {
                LocalDate.now() -> "Today"
                LocalDate.now().minusDays(1) -> "Yesterday"
                else -> {
                    val dow = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                    val mon = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
                    "$dow, ${date.dayOfMonth} $mon"
                }
            }
            WorkoutListItem(
                id = w.id,
                title = w.title,
                dateLabel = dateLabel,
                durationLabel = StatsRepository.durationMinutes(w)
                    ?.let { com.lolmax.app.ui.util.formatDuration(it) } ?: "",
                bodyweightLabel = Units.kgToDisplay(w.bodyweightKg, unit),
                exerciseCount = s.exerciseCount,
                setCount = s.setCount,
                volumeLabel = Units.formatVolume(s.volumeKg, unit),
            )
        }
        _state.update {
            it.copy(
                items = items,
                unit = unit,
                profileName = name,
                workoutCount = items.size,
                isLoading = false,
            )
        }
    }
}
