package com.worktrax.app.lib

import android.content.Context
import android.content.SharedPreferences
import com.worktrax.app.data.ExerciseEntry
import com.worktrax.app.data.SetEntry
import com.worktrax.app.data.WeightUnit
import com.worktrax.app.data.Workout
import com.worktrax.app.data.WorkoutType
import org.json.JSONArray
import org.json.JSONObject

object Storage {
    const val PREFS_NAME = "worktrax_prefs"

    const val KEY_HISTORY = "worktrax.history.v1"
    const val KEY_SETTINGS = "worktrax.settings.v1"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun putString(ctx: Context, key: String, value: String) {
        prefs(ctx).edit().putString(key, value).apply()
    }

    fun getString(ctx: Context, key: String, fallback: String? = null): String? =
        prefs(ctx).getString(key, fallback)
}

// ---------- JSON (de)serialization helpers ----------

fun Workout.toJson(): JSONObject {
    val exArr = JSONArray()
    for (ex in exercises) exArr.put(ex.toJson())
    return JSONObject().apply {
        put("id", id)
        put("date", date)
        put("type", type.code)
        put("durationSec", durationSec)
        put("exercises", exArr)
    }
}

fun ExerciseEntry.toJson(): JSONObject {
    val setsArr = JSONArray()
    for (s in sets) setsArr.put(s.toJson())
    return JSONObject().apply {
        put("id", id)
        put("name", name)
        put("muscle", muscle)
        put("sets", setsArr)
    }
}

fun SetEntry.toJson(): JSONObject = JSONObject().apply {
    put("reps", reps)
    put("weight", weight)
    put("unit", unit.code)
    put("at", at)
}

fun workoutFromJson(o: JSONObject): Workout {
    val exercises = mutableListOf<ExerciseEntry>()
    val arr = o.optJSONArray("exercises") ?: JSONArray()
    for (i in 0 until arr.length()) exercises.add(exerciseEntryFromJson(arr.getJSONObject(i)))
    return Workout(
        id = o.optString("id"),
        date = o.optString("date"),
        type = WorkoutType.from(o.optString("type")),
        durationSec = o.optInt("durationSec"),
        exercises = exercises,
    )
}

fun exerciseEntryFromJson(o: JSONObject): ExerciseEntry {
    val sets = mutableListOf<SetEntry>()
    val arr = o.optJSONArray("sets") ?: JSONArray()
    for (i in 0 until arr.length()) sets.add(setEntryFromJson(arr.getJSONObject(i)))
    return ExerciseEntry(
        id = o.optString("id"),
        name = o.optString("name"),
        muscle = o.optString("muscle"),
        sets = sets,
    )
}

fun setEntryFromJson(o: JSONObject): SetEntry = SetEntry(
    reps = o.optInt("reps"),
    weight = o.optDouble("weight"),
    unit = WeightUnit.from(o.optString("unit")),
    at = o.optString("at"),
)

fun workoutsToJsonString(list: List<Workout>): String {
    val arr = JSONArray()
    for (w in list) arr.put(w.toJson())
    return arr.toString()
}

fun workoutsFromJsonString(s: String?): List<Workout> {
    if (s.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(s)
        val out = mutableListOf<Workout>()
        for (i in 0 until arr.length()) out.add(workoutFromJson(arr.getJSONObject(i)))
        out
    } catch (_: Exception) {
        emptyList()
    }
}
