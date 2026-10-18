package com.lolmax.app.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolmax.app.data.local.entities.Goal
import com.lolmax.app.data.repository.ExerciseRepository
import com.lolmax.app.data.repository.ExerciseReport
import com.lolmax.app.data.repository.ProfileRepository
import com.lolmax.app.data.repository.StatsRepository
import com.lolmax.app.data.repository.TimeWindow
import com.lolmax.app.data.repository.WorkoutRepository
import com.lolmax.app.domain.LiftingLevel
import com.lolmax.app.ui.util.Units
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutHistoryEntry(
    val workoutId: String,
    val dateLabel: String,
    val workoutTitle: String,
    val setsLabel: String,
    val repsLabel: String,
    val volumeLabel: String,
    val bestLabel: String,
    val stars: Int,
    val percentile: Double?,
    val level: LiftingLevel?,
)

data class ExerciseReportUiState(
    val exerciseName: String = "",
    val isBodyweight: Boolean = false,
    val unit: String = "lb",
    val bestOneRmLabel: String = "—",
    val stars: Int = 0,
    val level: LiftingLevel? = null,
    val percentile: Double? = null,
    val window: TimeWindow = TimeWindow.ALL_TIME,
    val chartMetric: String = "Est. 1RM",
    val chartPoints: List<com.lolmax.app.ui.components.ChartPoint> = emptyList(),
    val history: List<WorkoutHistoryEntry> = emptyList(),
    val hasStandards: Boolean = false,
    val isLoading: Boolean = true,
)

class ExerciseReportViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val statsRepository: StatsRepository,
    private val profileRepository: ProfileRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseReportUiState())
    val uiState: StateFlow<ExerciseReportUiState> = _state

    private var exerciseId: String? = null

    fun load(id: String) {
        if (exerciseId == id && !_state.value.isLoading) return
        exerciseId = id
        refresh()
    }

    fun setWindow(window: TimeWindow) {
        _state.update { it.copy(window = window) }
        refresh()
    }

    fun setMetric(metric: String) {
        _state.update { it.copy(chartMetric = metric) }
        buildChart()
    }

    private fun refresh() {
        val id = exerciseId ?: return
        viewModelScope.launch {
            val exercise = exerciseRepository.getById(id)
            val report = statsRepository.exerciseReport(id, _state.value.window)
            val profile = profileRepository.getProfile()
            val unit = profile.weightUnit
            val hasStandards = report.currentLevel != null

            val historyEntries = buildHistory(id, report, unit)

            _state.update {
                it.copy(
                    exerciseName = exercise?.name ?: id,
                    isBodyweight = exercise?.isBodyweight == true,
                    unit = unit,
                    bestOneRmLabel = if (report.bestOneRmKg > 0)
                        Units.kgToDisplay(report.bestOneRmKg, unit) else "—",
                    stars = report.currentLevel?.stars ?: 0,
                    level = report.currentLevel,
                    percentile = report.currentPercentile,
                    hasStandards = hasStandards,
                    history = historyEntries,
                    isLoading = false,
                )
            }
            buildChart()
        }
    }

    private suspend fun buildHistory(
        id: String,
        report: ExerciseReport,
        unit: String,
    ): List<WorkoutHistoryEntry> {
        return report.points.reversed().map { p ->
            val date = java.time.LocalDate.ofEpochDay(p.dateEpochDay)
            WorkoutHistoryEntry(
                workoutId = "",
                dateLabel = "${date.dayOfMonth} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }}",
                workoutTitle = "",
                setsLabel = "",
                repsLabel = "${p.totalReps} reps",
                volumeLabel = Units.formatVolume(p.volumeKg, unit),
                bestLabel = Units.kgToDisplay(p.maxWeightKg, unit) + " x ${p.maxReps}",
                stars = p.level?.stars ?: 0,
                percentile = p.percentile,
                level = p.level,
            )
        }
    }

    private fun buildChart() {
        val id = exerciseId ?: return
        viewModelScope.launch {
            val report = statsRepository.exerciseReport(id, _state.value.window)
            val metric = _state.value.chartMetric
            val points = report.points.map { p ->
                val y = when (metric) {
                    "Lifter %" -> p.percentile ?: 0.0
                    "Max Weight" -> p.maxWeightKg
                    "Max Reps" -> p.maxReps.toDouble()
                    "Total Reps" -> p.totalReps.toDouble()
                    "Volume" -> p.volumeKg
                    else -> p.oneRmKg
                }
                com.lolmax.app.ui.components.ChartPoint(
                    x = p.dateEpochDay,
                    y = y,
                    label = "",
                )
            }
            _state.update { it.copy(chartPoints = points) }
        }
    }
}
