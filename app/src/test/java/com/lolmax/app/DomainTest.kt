package com.lolmax.app

import com.lolmax.app.domain.LevelThresholds
import com.lolmax.app.domain.LiftingLevel
import com.lolmax.app.domain.OneRepMax
import com.lolmax.app.domain.StandardsEngine
import com.lolmax.app.domain.Wilks
import com.lolmax.app.domain.Gender
import com.lolmax.app.domain.SetEntry
import com.lolmax.app.data.repository.StatsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneRepMaxTest {
    @Test
    fun `single rep equals weight`() {
        assertEquals(100.0, OneRepMax.estimate(100.0, 1), 1e-9)
    }

    @Test
    fun `epley five reps`() {
        // 100 * (1 + 5/30) = 116.666...
        assertEquals(100.0 * (1 + 5.0 / 30), OneRepMax.estimate(100.0, 5), 1e-6)
    }

    @Test
    fun `percent of 1rm table matches strength level`() {
        assertEquals(89.0, OneRepMax.percentOf1rm(5), 1e-9)
        assertEquals(75.0, OneRepMax.percentOf1rm(10), 1e-9)
        assertEquals(100.0, OneRepMax.percentOf1rm(1), 1e-9)
    }
}

class StandardsEngineTest {
    private val thresholds = LevelThresholds(50.0, 75.0, 100.0, 125.0, 150.0)

    @Test
    fun `level boundaries`() {
        assertEquals(LiftingLevel.BEGINNER, StandardsEngine.levelFor(40.0, thresholds))
        assertEquals(LiftingLevel.NOVICE, StandardsEngine.levelFor(75.0, thresholds))
        assertEquals(LiftingLevel.INTERMEDIATE, StandardsEngine.levelFor(100.0, thresholds))
        assertEquals(LiftingLevel.ADVANCED, StandardsEngine.levelFor(125.0, thresholds))
        assertEquals(LiftingLevel.ELITE, StandardsEngine.levelFor(150.0, thresholds))
    }

    @Test
    fun `percentile anchors`() {
        assertEquals(5.0, StandardsEngine.percentileFor(50.0, thresholds), 0.01)
        assertEquals(20.0, StandardsEngine.percentileFor(75.0, thresholds), 0.01)
        assertEquals(50.0, StandardsEngine.percentileFor(100.0, thresholds), 0.01)
        assertEquals(80.0, StandardsEngine.percentileFor(125.0, thresholds), 0.01)
        assertEquals(95.0, StandardsEngine.percentileFor(150.0, thresholds), 0.01)
    }

    @Test
    fun `percentile interpolates between anchors`() {
        val p = StandardsEngine.percentileFor(87.5, thresholds) // halfway novice-intermediate
        assertEquals(35.0, p, 0.5)
    }
}

class WilksTest {
    @Test
    fun `wilks positive for realistic male`() {
        val score = Wilks.score(80.0, 200.0 + 150.0 + 250.0, Gender.MALE)
        assertTrue(score > 0)
    }
}

class AggregateTest {
    @Test
    fun `working sets only`() {
        val stats = StatsRepository.aggregate(
            listOf(
                SetEntry(100.0, 5, isWarmup = true),
                SetEntry(120.0, 5, isWarmup = false),
                SetEntry(120.0, 5, isWarmup = false),
            )
        )
        assertEquals(2, stats.workingSetCount)
        assertEquals(10, stats.totalReps)
        assertEquals(1200.0, stats.totalVolumeKg, 1e-9)
    }

    @Test
    fun `best one rm tracked`() {
        val stats = StatsRepository.aggregate(
            listOf(
                SetEntry(100.0, 10, false),
                SetEntry(80.0, 1, false),
            )
        )
        assertTrue(stats.bestOneRmKg >= OneRepMax.estimate(100.0, 10) - 1e-9)
    }
}

class CsvParseTest {
    @Test
    fun `parses quoted fields`() {
        val row = com.lolmax.app.data.csv.CsvImporter.parse(
            """2026-10-17,"Upper, Body",70,Bench Press,1,100,6,0,105"""
        )
        assertEquals(9, row.size)
        assertEquals("Upper, Body", row[1])
    }

    @Test
    fun `parses strength level native export row`() {
        val header = listOf(
            "date lifted", "exercise", "weight (kg)", "weight (lb)", "reps",
            "bodyweight (kg)", "bodyweight (lb)", "percentile (%)", "warmup",
            "time (seconds)", "distance (meters)",
        )
        val lines = listOf(
            "Date Lifted,Exercise,Weight (kg),Weight (lb),Reps,Bodyweight (kg),Bodyweight (lb),Percentile (%),Warmup,Time (seconds),Distance (meters)",
            "2026-10-17,Bench Press,75,165.4,6,70,154.3,52.5,0,,",
            "2026-10-17,Pull Ups,,,12,70,154.3,45,0,,",
        )
        val (rows, errors) = com.lolmax.app.data.csv.CsvImporter.parseNativeRows(lines, header)
        assertEquals(0, errors.size)
        assertEquals(2, rows.size)
        assertEquals(75.0, rows[0].weightKg!!, 1e-9)
        assertEquals(6, rows[0].reps)
        assertEquals(false, rows[0].isWarmup)
        assertEquals(null, rows[1].weightKg)
        assertEquals(12, rows[1].reps)
        assertEquals(70.0, rows[1].bodyweightKg!!, 1e-9)
    }

    @Test
    fun `falls back to lb when kg missing`() {
        val header = listOf(
            "date lifted", "exercise", "weight (kg)", "weight (lb)", "reps",
            "bodyweight (kg)", "bodyweight (lb)", "percentile (%)", "warmup",
        )
        val lines = listOf(
            "header",
            "2026-10-01,Squat,,220.5,5,,,,1",
        )
        val (rows, _) = com.lolmax.app.data.csv.CsvImporter.parseNativeRows(lines, header)
        assertEquals(1, rows.size)
        assertEquals(220.5 * 0.45359237, rows[0].weightKg!!, 1e-6)
        assertEquals(true, rows[0].isWarmup)
    }
}
