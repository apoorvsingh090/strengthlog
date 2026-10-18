package com.lolmax.app.domain

import kotlin.math.pow
import kotlin.math.roundToInt

enum class Gender { MALE, FEMALE }

enum class BodyPart(val display: String) {
    WHOLE_BODY("Whole Body"), LEGS("Legs"), BACK("Back"), CHEST("Chest"),
    SHOULDERS("Shoulders"), BICEPS("Biceps"), TRICEPS("Triceps"),
    CORE("Core"), FOREARMS("Forearms")
}

enum class EquipmentCategory(val display: String) {
    BARBELL("Barbell"), BODYWEIGHT("Bodyweight"), DUMBBELL("Dumbbell"),
    MACHINE("Machine"), CABLE("Cable"), KETTLEBELL("Kettlebell")
}

enum class LiftingLevel(val display: String, val minPercentile: Int) {
    BEGINNER("Beginner", 5),
    NOVICE("Novice", 20),
    INTERMEDIATE("Intermediate", 50),
    ADVANCED("Advanced", 80),
    ELITE("Elite", 95);

    val stars: Int
        get() = ordinal + 1
}

object OneRepMax {
    /** Epley formula — matches Strength Level's rep-to-%1RM table closely. */
    fun estimate(weightKg: Double, reps: Int): Double {
        if (reps <= 1) return weightKg
        return weightKg * (1.0 + reps / 30.0)
    }

    /** Percentage of 1RM table used by Strength Level. */
    fun percentOf1rm(reps: Int): Double = when (reps) {
        1 -> 100.0; 2 -> 97.0; 3 -> 94.0; 4 -> 92.0; 5 -> 89.0; 6 -> 86.0
        7 -> 83.0; 8 -> 81.0; 9 -> 78.0; 10 -> 75.0; 11 -> 73.0; 12 -> 71.0
        13 -> 70.0; 14 -> 68.0; 15 -> 67.0; 16 -> 65.0; 17 -> 64.0; 18 -> 63.0
        19 -> 61.0; 20 -> 60.0; in 21..30 -> (60 - (reps - 20) * (10.0 / 29)).roundToInt().toDouble()
        else -> 50.0
    }

    /** Weight to lift for a given % of 1RM. */
    fun weightForPercent(oneRepMaxKg: Double, percent: Double): Double =
        oneRepMaxKg * percent / 100.0
}

object Wilks {
    /** Classic Wilks score. Bodyweight in kg, total in kg. */
    fun score(bodyweightKg: Double, totalKg: Double, gender: Gender): Double {
        val bw = bodyweightKg.coerceIn(35.0, 170.0)
        val denom = if (gender == Gender.MALE) {
            -216.0475144 + 1626.114352 * bw - 0.002388645 * bw.pow(2) -
                0.00000898 * bw.pow(3) + 0.00000000737 * bw.pow(4)
        } else {
            -945.0417 + 272.3843 * bw - 0.8211 * bw.pow(2) +
                0.00931 * bw.pow(3) - 0.0000473 * bw.pow(4)
        }
        return if (denom == 0.0) 0.0 else 500.0 / denom * totalKg
    }
}

data class LevelThresholds(
    val beginner: Double,
    val novice: Double,
    val intermediate: Double,
    val advanced: Double,
    val elite: Double,
) {
    fun ordered(): List<Double> = listOf(beginner, novice, intermediate, advanced, elite)
}

object StandardsEngine {
    /**
     * Star level from a 1RM vs bodyweight-ratio thresholds.
     * Returns the level whose threshold the lifter meets or exceeds.
     */
    fun levelFor(oneRmKg: Double, thresholds: LevelThresholds): LiftingLevel = when {
        oneRmKg >= thresholds.elite -> LiftingLevel.ELITE
        oneRmKg >= thresholds.advanced -> LiftingLevel.ADVANCED
        oneRmKg >= thresholds.intermediate -> LiftingLevel.INTERMEDIATE
        oneRmKg >= thresholds.novice -> LiftingLevel.NOVICE
        else -> LiftingLevel.BEGINNER
    }

    /**
     * Estimated percentile (0-100) via piecewise-linear interpolation between
     * the published band anchors: Beg=5%, Nov=20%, Int=50%, Adv=80%, Elite=95%.
     * Below beginner anchor extrapolates toward 0; above elite toward 100.
     */
    fun percentileFor(oneRmKg: Double, thresholds: LevelThresholds): Double {
        // Guard NaN/Infinity/<=0 from garbage set data; never surface NaN to the UI.
        if (!oneRmKg.isFinite() || oneRmKg <= 0.0) return 0.0
        val anchors = listOf(5.0, 20.0, 50.0, 80.0, 95.0)
        val levels = thresholds.ordered()
        if (oneRmKg <= levels.first()) {
            val span = levels[1] - levels[0]
            val frac = if (span <= 0 || levels[0] <= 0) 0.0
            else (oneRmKg / levels[0]).coerceIn(0.0, 1.0)
            return 5.0 * frac
        }
        for (i in 0 until levels.size - 1) {
            if (oneRmKg < levels[i + 1]) {
                val gap = levels[i + 1] - levels[i]
                val frac = if (gap <= 0) 0.0 else (oneRmKg - levels[i]) / gap
                return (anchors[i] + frac * (anchors[i + 1] - anchors[i])).coerceIn(0.0, 100.0)
            }
        }
        val span = oneRmKg - levels.last()
        val above = if (levels.last() > 0) span / levels.last() * 5.0 else 5.0
        return (95.0 + above).coerceIn(0.0, 100.0)
    }

    fun starsFor(level: LiftingLevel): Int = level.stars
}

data class SetEntry(
    val weightKg: Double?,
    val reps: Int,
    val isWarmup: Boolean,
)

data class ExerciseSetStats(
    val bestWeightKg: Double?,
    val bestReps: Int,
    val bestOneRmKg: Double,
    val totalReps: Int,
    val totalVolumeKg: Double,
    val workingSetCount: Int,
)
