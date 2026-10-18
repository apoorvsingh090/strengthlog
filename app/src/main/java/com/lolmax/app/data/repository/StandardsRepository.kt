package com.lolmax.app.data.repository

import android.content.Context
import com.lolmax.app.domain.LevelThresholds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/**
 * Loads published Strength Level bodyweight-ratio (or rep) standards from assets.
 * Thresholds are [beginner, novice, intermediate, advanced, elite].
 * Type "ratio": values are 1RM (or per-dumbbell weight) multiples of bodyweight.
 * Type "reps": values are max-rep counts for bodyweight exercises.
 */
class StandardsRepository(private val context: Context) {

    data class StandardsEntry(
        val type: String,
        val male: List<Double>,
        val female: List<Double>,
        val perDumbbell: Boolean = false,
    )

    private val cache = AtomicReference<Map<String, StandardsEntry>?>(null)

    private suspend fun load(): Map<String, StandardsEntry> {
        cache.get()?.let { return it }
        return withContext(Dispatchers.IO) {
            cache.get() ?: run {
                val text = context.assets.open("standards.json")
                    .bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val map = buildMap {
                    for (key in root.keys()) {
                        val o = root.getJSONObject(key)
                        put(
                            key,
                            StandardsEntry(
                                type = o.getString("type"),
                                male = jsonArrayToList(o.getJSONArray("male")),
                                female = jsonArrayToList(o.getJSONArray("female")),
                                perDumbbell = o.optBoolean("perDumbbell", false),
                            )
                        )
                    }
                }
                cache.set(map)
                map
            }
        }
    }

    private fun jsonArrayToList(arr: org.json.JSONArray): List<Double> =
        (0 until arr.length()).map { arr.getDouble(it) }

    suspend fun hasStandards(exerciseId: String): Boolean = load().containsKey(exerciseId)

    suspend fun getThresholds(
        exerciseId: String,
        isMale: Boolean,
        bodyweightKg: Double,
    ): LevelThresholds? {
        val entry = load()[exerciseId] ?: return null
        val raw = if (isMale) entry.male else entry.female
        if (raw.size != 5) return null
        val values = if (entry.type == "ratio") {
            raw.map { it * bodyweightKg }
        } else {
            raw
        }
        return LevelThresholds(values[0], values[1], values[2], values[3], values[4])
    }
}
