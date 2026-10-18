package com.lolmax.app.data.local

import android.content.Context
import com.lolmax.app.data.local.entities.Exercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray

object SeedingCoroutine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context, db: LolMaxDatabase) {
        scope.launch {
            // Sentinel check instead of count() == 0: a custom exercise or CSV import
            // completing first must not permanently block catalog seeding.
            if (db.exerciseDao().getById("squat") == null) {
                val json = context.assets.open("exercises.json")
                    .bufferedReader().use { it.readText() }
                val arr = JSONArray(json)
                val exercises = buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        add(
                            Exercise(
                                id = o.getString("id"),
                                name = o.getString("name"),
                                slug = o.optString("slug", o.getString("id")),
                                bodyPart = o.getString("bodyPart"),
                                category = o.getString("category"),
                                isBodyweight = o.optBoolean("isBodyweight", false),
                                isCustom = false,
                            )
                        )
                    }
                }
                db.exerciseDao().insertAll(exercises)
            }
        }
    }
}
