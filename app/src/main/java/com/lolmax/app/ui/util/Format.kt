package com.lolmax.app.ui.util

import java.util.Locale
import kotlin.math.roundToInt

object Units {
    const val KG_PER_LB = 0.45359237

    fun kgToDisplay(kg: Double?, unit: String): String {
        if (kg == null) return "—"
        return if (unit == "lb") {
            String.format(Locale.US, "%.1f lb", kg / KG_PER_LB)
        } else {
            String.format(Locale.US, "%.1f kg", kg)
        }
    }

    fun kgToNumber(kg: Double?, unit: String): String {
        if (kg == null) return ""
        return if (unit == "lb") {
            String.format(Locale.US, "%.1f", kg / KG_PER_LB)
        } else {
            String.format(Locale.US, "%.1f", kg)
        }
    }

    fun displayToKg(value: Double, unit: String): Double =
        if (unit == "lb") value * KG_PER_LB else value

    fun formatVolume(kg: Double, unit: String): String {
        val value = if (unit == "lb") kg / KG_PER_LB else kg
        return when {
            value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000)
            value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000)
            else -> value.roundToInt().toString()
        } + if (unit == "lb") " lb" else " kg"
    }
}

fun starString(count: Int): String = "★".repeat(count.coerceIn(0, 5))

fun formatDuration(minutes: Int?): String {
    if (minutes == null) return ""
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
