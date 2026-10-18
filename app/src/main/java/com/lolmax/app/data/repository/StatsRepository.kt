package com.lolmax.app.data.repository

import com.lolmax.app.data.local.dao.SetDao
import com.lolmax.app.data.local.dao.WorkoutDao
import com.lolmax.app.data.local.dao.WorkoutExerciseDao
import com.lolmax.app.data.local.entities.Workout
import com.lolmax.app.domain.ExerciseSetStats
import com.lolmax.app.domain.Gender
import com.lolmax.app.domain.LevelThresholds
import com.lolmax.app.domain.LiftingLevel
import com.lolmax.app.domain.OneRepMax
import com.lolmax.app.domain.SetEntry
import com.lolmax.app.domain.StandardsEngine
import com.lolmax.app.domain.Wilks
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first

enum class TimeWindow(val months: Int, val display: String) {
    THREE_MONTHS(3, "3m"),
    SIX_MONTHS(6, "6m"),
    TWELVE_MONTHS(12, "12m"),
    ALL_TIME(0, "All time"),
}

data class ExercisePoint(
    val dateEpochDay: Long,
    val oneRmKg: Double,
    val maxWeightKg: Double,
    val maxReps: Int,
    val totalReps: Int,
    val volumeKg: Double,
    val percentile: Double?,
    val level: LiftingLevel?,
)

data class ExerciseReport(
    val bestOneRmKg: Double,
    val bestOneRmPoint: ExercisePoint?,
    val currentLevel: LiftingLevel?,
    val currentPercentile: Double?,
    val points: List<ExercisePoint>,
    val workoutCount: Int,
)

data class PowerliftingSummary(
    val bestSquatKg: Double,
    val bestBenchKg: Double,
    val bestDeadliftKg: Double,
    val totalKg: Double,
    val wilks: Double,
    val level: LiftingLevel?,
    val percentile: Double?,
)

class StatsRepository(
    private val workoutDao: WorkoutDao,
    private val workoutExerciseDao: WorkoutExerciseDao,
    private val setDao: SetDao,
    private val exerciseRepository: ExerciseRepository,
    private val standardsRepository: StandardsRepository,
    private val profileRepository: ProfileRepository,
) {
    suspend fun exerciseReport(
        exerciseId: String,
        window: TimeWindow,
    ): ExerciseReport {
        val profile = profileRepository.getProfile()
        val isMale = profile.gender == "MALE"
        val bodyweightKg = profile.bodyweightKg
        val thresholds = standardsRepository.getThresholds(exerciseId, isMale, bodyweightKg)

        val since = windowSince(window)
        val workoutExercises = workoutExerciseDao.getByExercise(exerciseId)
        if (workoutExercises.isEmpty()) {
            return ExerciseReport(0.0, null, null, null, emptyList(), 0)
        }
        val workoutsById = workoutExercises
            .mapNotNull { we -> workoutDao.getById(we.workoutId) }
            .filter { w -> since == null || w.dateEpochDay >= since }
            .associateBy { it.id }
        val relevantWe = workoutExercises.filter { it.workoutId in workoutsById }
        if (relevantWe.isEmpty()) {
            return ExerciseReport(0.0, null, null, null, emptyList(), 0)
        }
        val allSets = setDao.getForExercises(relevantWe.map { it.id })
            .filter { !it.isWarmup }

        val byWorkout = relevantWe.groupBy { it.workoutId }
        val points = byWorkout.mapNotNull { (workoutId, wes) ->
            val workout = workoutsById[workoutId] ?: return@mapNotNull null
            val sets = allSets.filter { s -> wes.any { it.id == s.workoutExerciseId } }
            if (sets.isEmpty()) return@mapNotNull null
            val stats = aggregate(sets.map { SetEntry(it.weightKg, it.reps, false) })
            if (stats.workingSetCount == 0) return@mapNotNull null
            val level = thresholds?.let { StandardsEngine.levelFor(stats.bestOneRmKg, it) }
            val percentile = thresholds?.let { StandardsEngine.percentileFor(stats.bestOneRmKg, it) }
            ExercisePoint(
                dateEpochDay = workout.dateEpochDay,
                oneRmKg = stats.bestOneRmKg,
                maxWeightKg = stats.bestWeightKg ?: 0.0,
                maxReps = stats.bestReps,
                totalReps = stats.totalReps,
                volumeKg = stats.totalVolumeKg,
                percentile = percentile,
                level = level,
            )
        }.sortedBy { it.dateEpochDay }

        val bestPoint = points.maxByOrNull { it.oneRmKg }
        return ExerciseReport(
            bestOneRmKg = bestPoint?.oneRmKg ?: 0.0,
            bestOneRmPoint = bestPoint,
            currentLevel = bestPoint?.level,
            currentPercentile = bestPoint?.percentile,
            points = points,
            workoutCount = points.size,
        )
    }

    suspend fun lastPerformance(exerciseId: String): Pair<Double?, Int>? {
        val wes = workoutExerciseDao.getByExerciseRecentFirst(exerciseId)
        for (we in wes) {
            val sets = setDao.observeForExercise(we.id).first().filter { !it.isWarmup }
            if (sets.isNotEmpty()) {
                val last = sets.last()
                return last.weightKg to last.reps
            }
        }
        return null
    }

    suspend fun bestSet(exerciseId: String): Pair<Double?, Int>? {
        val sets = setDao.getByExerciseId(exerciseId).filter { !it.isWarmup }
        if (sets.isEmpty()) return null
        val best = sets.maxByOrNull { s ->
            OneRepMax.estimate(s.weightKg ?: 0.0, s.reps)
        } ?: return null
        return best.weightKg to best.reps
    }

    suspend fun allTimeStats(exerciseId: String): ExerciseSetStats {
        val wes = workoutExerciseDao.getByExercise(exerciseId)
        if (wes.isEmpty()) return ExerciseSetStats(null, 0, 0.0, 0, 0.0, 0)
        val sets = setDao.getForExercises(wes.map { it.id }).filter { !it.isWarmup }
        return aggregate(sets.map { SetEntry(it.weightKg, it.reps, false) })
    }

    /** Best squat/bench/deadlift 1RM in the last 12 weeks (84 days). */
    suspend fun powerliftingSummary(): PowerliftingSummary {
        val profile = profileRepository.getProfile()
        val isMale = profile.gender == "MALE"
        val gender = if (isMale) Gender.MALE else Gender.FEMALE
        val bodyweightKg = profile.bodyweightKg

        val bestByExercise = mutableMapOf<String, Double>()
        for (slug in listOf("squat", "bench-press", "deadlift")) {
            val exercise = exerciseRepository.findBySlug(slug) ?: continue
            val wes = workoutExerciseDao.getByExercise(exercise.id)
            val workouts = wes.mapNotNull { workoutDao.getById(it.workoutId) }
                .associateBy { it.id }
            val cutoff = LocalDate.now().minusWeeks(12).toEpochDay()
            val recentWes = wes.filter { (workouts[it.workoutId]?.dateEpochDay ?: 0) >= cutoff }
            if (recentWes.isEmpty()) continue
            val sets = setDao.getForExercises(recentWes.map { it.id }).filter { !it.isWarmup }
            val best = sets.maxOfOrNull { OneRepMax.estimate(it.weightKg ?: 0.0, it.reps) } ?: 0.0
            bestByExercise[slug] = best
        }
        val squat = bestByExercise["squat"] ?: 0.0
        val bench = bestByExercise["bench-press"] ?: 0.0
        val deadlift = bestByExercise["deadlift"] ?: 0.0
        val total = squat + bench + deadlift
        val wilks = if (total > 0) Wilks.score(bodyweightKg, total, gender) else 0.0

        // Powerlifting level from total vs typical standards (rough, bodyweight-relative).
        val totalRatio = if (bodyweightKg > 0) total / bodyweightKg else 0.0
        val level = when {
            total <= 0 -> null
            !isMale && totalRatio >= 5.5 -> LiftingLevel.ELITE
            !isMale && totalRatio >= 4.3 -> LiftingLevel.ADVANCED
            !isMale && totalRatio >= 3.2 -> LiftingLevel.INTERMEDIATE
            !isMale && totalRatio >= 2.0 -> LiftingLevel.NOVICE
            !isMale -> LiftingLevel.BEGINNER
            isMale && totalRatio >= 7.5 -> LiftingLevel.ELITE
            isMale && totalRatio >= 6.0 -> LiftingLevel.ADVANCED
            isMale && totalRatio >= 4.5 -> LiftingLevel.INTERMEDIATE
            isMale && totalRatio >= 3.0 -> LiftingLevel.NOVICE
            else -> LiftingLevel.BEGINNER
        }
        val percentile = level?.let { StandardsEngine.percentileFor(totalRatio.toDouble(), ratioThresholds(it, isMale)) }
        return PowerliftingSummary(squat, bench, deadlift, total, wilks, level, percentile)
    }

    private fun ratioThresholds(level: LiftingLevel, isMale: Boolean): LevelThresholds {
        val male = LevelThresholds(3.0, 4.5, 6.0, 7.5, 9.5)
        val female = LevelThresholds(2.0, 3.2, 4.3, 5.5, 7.0)
        return if (isMale) male else female
    }

    private fun windowSince(window: TimeWindow): Long? {
        if (window == TimeWindow.ALL_TIME) return null
        return LocalDate.now().minusMonths(window.months.toLong()).toEpochDay()
    }

    companion object {
        fun aggregate(entries: List<SetEntry>): ExerciseSetStats {
            val working = entries.filter { !it.isWarmup }
            if (working.isEmpty()) {
                return ExerciseSetStats(null, 0, 0.0, 0, 0.0, 0)
            }
            var bestWeight = 0.0
            var bestReps = 0
            var bestOneRm = 0.0
            var totalReps = 0
            var volume = 0.0
            for (s in working) {
                val w = s.weightKg ?: 0.0
                if (w > bestWeight) bestWeight = w
                if (s.reps > bestReps) bestReps = s.reps
                val orm = OneRepMax.estimate(w, s.reps)
                if (orm > bestOneRm) bestOneRm = orm
                totalReps += s.reps
                volume += w * s.reps
            }
            return ExerciseSetStats(
                bestWeightKg = bestWeight.takeIf { it > 0 },
                bestReps = bestReps,
                bestOneRmKg = bestOneRm,
                totalReps = totalReps,
                totalVolumeKg = volume,
                workingSetCount = working.size,
            )
        }

        fun durationMinutes(workout: Workout): Int? {
            val s = workout.startMinuteOfDay ?: return null
            val e = workout.endMinuteOfDay ?: return null
            return if (e >= s) e - s else (24 * 60 - s) + e
        }

        fun daysAgo(dateEpochDay: Long): Long =
            ChronoUnit.DAYS.between(LocalDate.ofEpochDay(dateEpochDay), LocalDate.now())
    }
}
