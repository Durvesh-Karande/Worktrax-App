package com.worktrax.app.lib

import com.worktrax.app.data.WeightUnit
import com.worktrax.app.data.Workout
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

private fun parseIso(iso: String): Date {
    return try {
        // ISO8601 with Z — strip milliseconds if present
        val cleaned = if (iso.endsWith("Z")) iso.replace("Z", "+0000") else iso
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).parse(cleaned)
            ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(cleaned)
            ?: Date()
    } catch (_: Exception) {
        try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(iso) ?: Date()
        } catch (_: Exception) {
            Date()
        }
    }
}

fun nowIso(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}

fun isoEpochMs(iso: String): Long = parseIso(iso).time

fun formatDate(iso: String): String {
    val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    return sdf.format(parseIso(iso))
}

fun formatShortDate(iso: String): String {
    val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
    return sdf.format(parseIso(iso))
}

fun formatMonthKey(iso: String): String {
    val d = parseIso(iso)
    val cal = Calendar.getInstance().apply { time = d }
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    return "%d-%02d".format(y, m)
}

fun formatMonthLabel(key: String): String {
    val parts = key.split("-")
    val y = parts[0].toInt()
    val m = parts[1].toInt()
    val cal = Calendar.getInstance().apply { set(y, m - 1, 1) }
    val sdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    return sdf.format(cal.time)
}

fun formatDuration(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%02d:%02d".format(m, s)
}

fun formatMinutes(sec: Int): String {
    val m = (sec.toDouble() / 60.0).roundToInt()
    return "$m min"
}

fun convertWeight(value: Double, from: WeightUnit, to: WeightUnit): Double {
    if (from == to) return value
    return if (from == WeightUnit.KG && to == WeightUnit.LB) value * 2.20462 else value / 2.20462
}

fun volumeOf(w: Workout, unit: WeightUnit): Int {
    var total = 0.0
    for (ex in w.exercises) {
        for (s in ex.sets) {
            val weight = if (s.unit == unit) s.weight else convertWeight(s.weight, s.unit, unit)
            total += weight * s.reps
        }
    }
    return total.roundToInt()
}

fun greeting(cal: Calendar = Calendar.getInstance()): String {
    val h = cal.get(Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> "Morning"
        h < 17 -> "Afternoon"
        else -> "Evening"
    }
}

fun weightStep(unit: WeightUnit): Double = if (unit == WeightUnit.KG) 2.5 else 5.0

data class HighlightPart(val text: String, val match: Boolean)

fun highlight(text: String, query: String): List<HighlightPart> {
    if (query.isEmpty()) return listOf(HighlightPart(text, false))
    val idx = text.lowercase().indexOf(query.lowercase())
    if (idx == -1) return listOf(HighlightPart(text, false))
    return listOf(
        HighlightPart(text.substring(0, idx), false),
        HighlightPart(text.substring(idx, idx + query.length), true),
        HighlightPart(text.substring(idx + query.length), false),
    )
}

fun uid(prefix: String = ""): String {
    val rand = ((Math.random() * Long.MAX_VALUE).toLong())
        .toString(36).takeLast(8)
    val ts = System.currentTimeMillis().toString(36)
    val sep = if (prefix.isNotEmpty()) "_" else ""
    return "$prefix$sep$ts$rand"
}

fun formatTopDate(): String {
    val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    return sdf.format(Date()).uppercase()
}

fun nowEpochMs(): Long = System.currentTimeMillis()

fun daysAgoIsoDate(days: Int): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -days)
    return sdf.format(cal.time)
}

fun todayIsoDate(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(Date())
}

fun parseIsoDateStart(date: String): Long {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return sdf.parse("$date 00:00:00")?.time ?: 0L
}

fun parseIsoDateEnd(date: String): Long {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return sdf.parse("$date 23:59:59")?.time ?: 0L
}

fun isoFromEpochMs(ms: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(Date(ms))
}

fun numberWithCommas(n: Int): String = "%,d".format(Locale.US, n)
