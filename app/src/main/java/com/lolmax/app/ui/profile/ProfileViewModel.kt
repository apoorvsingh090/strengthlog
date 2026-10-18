package com.lolmax.app.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lolmax.app.data.csv.CsvExporter
import com.lolmax.app.data.csv.CsvImporter
import com.lolmax.app.data.local.entities.UserProfile
import com.lolmax.app.data.repository.ExerciseRepository
import com.lolmax.app.data.repository.ProfileRepository
import com.lolmax.app.data.repository.StatsRepository
import com.lolmax.app.data.repository.WorkoutRepository
import com.lolmax.app.domain.OneRepMax
import com.lolmax.app.ui.util.Units
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    /** null until the DB emits — avoids the placeholder latching the form first. */
    val profile: UserProfile? = null,
    val unit: String = "lb",
    val bestBenchLabel: String = "—",
    val bestSquatLabel: String = "—",
    val bestDeadliftLabel: String = "—",
    val saveStatus: String? = null,
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val exerciseRepository: ExerciseRepository,
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private val lifts = MutableStateFlow(Triple("—", "—", "—"))
    private val _saveStatus = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(
        profileRepository.observeProfile(),
        lifts,
        _saveStatus,
    ) { profile, liftLabels, saveStatus ->
        ProfileUiState(
            profile = profile,
            unit = profile.weightUnit,
            bestBenchLabel = liftLabels.first,
            bestSquatLabel = liftLabels.second,
            bestDeadliftLabel = liftLabels.third,
            saveStatus = saveStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    init {
        viewModelScope.launch { refreshLifts() }
    }

    fun refresh() {
        viewModelScope.launch { refreshLifts() }
    }

    private suspend fun refreshLifts() {
        val unit = profileRepository.getProfile().weightUnit
        val bench = exerciseRepository.findBySlug("bench-press")
            ?.let { statsRepository.bestSet(it.id) }
        val squat = exerciseRepository.findBySlug("squat")
            ?.let { statsRepository.bestSet(it.id) }
        val dead = exerciseRepository.findBySlug("deadlift")
            ?.let { statsRepository.bestSet(it.id) }
        fun label(pair: Pair<Double?, Int>?): String {
            if (pair == null) return "—"
            val (w, reps) = pair
            val orm = OneRepMax.estimate(w ?: 0.0, reps)
            return "${Units.kgToDisplay(w, unit)} x $reps · 1RM ${Units.kgToDisplay(orm, unit)}"
        }
        lifts.value = Triple(label(bench), label(squat), label(dead))
    }

    fun save(
        name: String,
        gender: String,
        ageText: String,
        bodyweightText: String,
        heightText: String,
    ) {
        viewModelScope.launch {
            // Validate raw input instead of silently substituting defaults.
            val age = ageText.trim().toIntOrNull()?.takeIf { it in 1..120 }
            val bodyweight = bodyweightText.trim().toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0 && it < 500 }
            val height = heightText.trim().toDoubleOrNull()
                ?.takeIf { it.isFinite() && it in 30.0..300.0 }
            if (age == null || bodyweight == null || height == null) {
                _saveStatus.value =
                    "Check age (1-120), bodyweight (>0) and height (30-300 cm)"
                return@launch
            }
            val current = profileRepository.getProfile()
            profileRepository.saveProfile(
                current.copy(
                    name = name,
                    gender = gender,
                    age = age,
                    bodyweightKg = Units.displayToKg(bodyweight, current.weightUnit),
                    heightCm = height,
                )
            )
            _saveStatus.value = "Profile saved"
            refresh()
        }
    }

    fun consumeSaveStatus() { _saveStatus.value = null }

    fun setUnit(unit: String) {
        viewModelScope.launch {
            val current = profileRepository.getProfile()
            profileRepository.saveProfile(current.copy(weightUnit = unit))
            refresh()
        }
    }
}

data class PowerliftingUiState(
    val squatLabel: String = "—",
    val benchLabel: String = "—",
    val deadliftLabel: String = "—",
    val totalLabel: String = "—",
    val wilksLabel: String = "—",
    val levelDisplay: String = "Unknown",
    val percentileLabel: String = "",
    val unit: String = "lb",
)

class PowerliftingViewModel(
    @Suppress("unused") private val exerciseRepository: ExerciseRepository,
    private val statsRepository: StatsRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PowerliftingUiState())
    val uiState: StateFlow<PowerliftingUiState> = _state

    fun refresh() {
        viewModelScope.launch {
            val s = statsRepository.powerliftingSummary()
            val unit = profileRepository.getProfile().weightUnit
            _state.update {
                it.copy(
                    squatLabel = Units.kgToDisplay(s.bestSquatKg.takeIf { v -> v > 0 }, unit),
                    benchLabel = Units.kgToDisplay(s.bestBenchKg.takeIf { v -> v > 0 }, unit),
                    deadliftLabel = Units.kgToDisplay(s.bestDeadliftKg.takeIf { v -> v > 0 }, unit),
                    totalLabel = Units.kgToDisplay(s.totalKg.takeIf { v -> v > 0 }, unit),
                    wilksLabel = if (s.wilks > 0) String.format(java.util.Locale.US, "%.1f", s.wilks) else "—",
                    levelDisplay = s.level?.display ?: "Unknown",
                    percentileLabel = s.percentile?.let { p -> "Stronger than ${p.toInt()}%" } ?: "",
                    unit = unit,
                )
            }
        }
    }
}

class SettingsViewModel(
    private val profileRepository: ProfileRepository,
    private val csvExporter: CsvExporter,
    private val csvImporter: CsvImporter,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    val themeMode: StateFlow<String> = profileRepository.observeThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val exportCsvText = MutableStateFlow<String?>(null)
    val importStatus = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    fun setTheme(mode: String) {
        viewModelScope.launch { profileRepository.setThemeMode(mode) }
    }

    fun exportCsv() {
        viewModelScope.launch {
            busy.value = true
            try {
                exportCsvText.value = csvExporter.exportWorkoutsCsv()
            } catch (e: Exception) {
                importStatus.value = "Export failed: ${e.message}"
            } finally {
                busy.value = false
            }
        }
    }

    fun importCsvFromUri(uri: Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            busy.value = true
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalArgumentException("Cannot read file")
                }
                val result = csvImporter.importWorkoutsCsv(text, workoutRepository)
                val skipped = if (result.skippedDates > 0)
                    " (${result.skippedDates} dates already imported, skipped)" else ""
                val custom = if (result.unknownExercises.isNotEmpty())
                    ". New custom exercises: ${result.unknownExercises.joinToString(", ")}" else ""
                importStatus.value =
                    "Imported ${result.workoutsImported} workouts, ${result.setsImported} sets$skipped$custom"
            } catch (e: Exception) {
                importStatus.value = "Import failed: ${e.message}"
            } finally {
                busy.value = false
            }
        }
    }

    fun consumeExport() { exportCsvText.value = null }
    fun consumeImportStatus() { importStatus.value = null }
}
