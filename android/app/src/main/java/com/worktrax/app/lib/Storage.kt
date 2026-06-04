package com.worktrax.app.lib

import android.content.Context
import android.content.SharedPreferences
import com.worktrax.app.data.BodyMeasurement
import com.worktrax.app.data.BodyweightEntry
import com.worktrax.app.data.Equipment
import com.worktrax.app.data.ExerciseDef
import com.worktrax.app.data.ExerciseEntry
import com.worktrax.app.data.Routine
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
    const val KEY_ONBOARDING_DONE = "worktrax.onboarding.v1"
    const val KEY_ROUTINES = "worktrax.routines.v1"
    const val KEY_BODYWEIGHT = "worktrax.bodyweight.v1"
    const val KEY_LAST_ROUTINE = "worktrax.last_routine.v1"
    const val KEY_CUSTOM_EXERCISES = "worktrax.custom_exercises.v1"
    const val KEY_BODY_MEASUREMENTS = "worktrax.body_measurements.v1"
    const val KEY_MIGRATED = "worktrax.migrated.v1"

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
        put("metricType", metricType)
        put("sets", setsArr)
    }
}

fun SetEntry.toJson(): JSONObject = JSONObject().apply {
    put("metricType", metricType)
    put("reps", reps)
    put("weight", if (weight.isFinite()) weight else 0.0)
    put("unit", unit.code)
    put("durationSec", durationSec)
    put("distanceKm", if (distanceKm.isFinite()) distanceKm else 0.0)
    put("rounds", rounds)
    put("at", at)
    if (warmup) put("warmup", true)
    if (rpe != null) put("rpe", rpe)
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
        metricType = o.optString("metricType", "strength"),
        sets = sets,
    )
}

fun setEntryFromJson(o: JSONObject): SetEntry = SetEntry(
    metricType = o.optString("metricType", "strength"),
    reps = o.optInt("reps"),
    weight = o.optDouble("weight", 0.0),
    unit = WeightUnit.from(o.optString("unit")),
    durationSec = o.optInt("durationSec"),
    distanceKm = o.optDouble("distanceKm", 0.0),
    rounds = o.optInt("rounds"),
    at = o.optString("at"),
    warmup = o.optBoolean("warmup", false),
    rpe = if (o.has("rpe")) o.optInt("rpe", -1).let { if (it < 0) null else it } else null,
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

fun Routine.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("type", type.code)
    put("exerciseIds", JSONArray(exerciseIds))
    put("createdAt", createdAt)
}

fun routineFromJson(o: JSONObject): Routine = Routine(
    id = o.optString("id"),
    name = o.optString("name"),
    type = WorkoutType.from(o.optString("type")),
    exerciseIds = (0 until (o.optJSONArray("exerciseIds")?.length() ?: 0)).map {
        o.optJSONArray("exerciseIds")?.optString(it) ?: ""
    }.filter { it.isNotBlank() },
    createdAt = o.optString("createdAt"),
)

fun routinesToJsonString(list: List<Routine>): String {
    val arr = JSONArray()
    for (r in list) arr.put(r.toJson())
    return arr.toString()
}

fun routinesFromJsonString(s: String?): List<Routine> {
    if (s.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(s)
        val out = mutableListOf<Routine>()
        for (i in 0 until arr.length()) out.add(routineFromJson(arr.getJSONObject(i)))
        out
    } catch (_: Exception) { emptyList() }
}

fun BodyweightEntry.toJson(): JSONObject = JSONObject().apply {
    put("date", date)
    put("weight", if (weight.isFinite()) weight else 0.0)
    put("unit", unit.code)
}

fun bodyweightFromJson(o: JSONObject): BodyweightEntry = BodyweightEntry(
    date = o.optString("date"),
    weight = o.optDouble("weight", 0.0),
    unit = WeightUnit.from(o.optString("unit")),
)

fun bodyweightToJsonString(list: List<BodyweightEntry>): String {
    val arr = JSONArray()
    for (b in list) arr.put(b.toJson())
    return arr.toString()
}

fun bodyweightFromJsonString(s: String?): List<BodyweightEntry> {
    if (s.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(s)
        val out = mutableListOf<BodyweightEntry>()
        for (i in 0 until arr.length()) out.add(bodyweightFromJson(arr.getJSONObject(i)))
        out
    } catch (_: Exception) { emptyList() }
}

// ─── Custom exercises ───

fun customExercisesFromJsonString(s: String?): List<ExerciseDef> {
    if (s.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(s)
        val out = mutableListOf<ExerciseDef>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(ExerciseDef(
                id = o.optString("id"),
                name = o.optString("name"),
                muscle = o.optString("muscle"),
                equipment = Equipment.from(o.optString("equipment")),
                type = WorkoutType.from(o.optString("type")),
            ))
        }
        out
    } catch (_: Exception) { emptyList() }
}

fun customExercisesToJsonString(list: List<ExerciseDef>): String {
    val arr = JSONArray()
    for (ex in list) {
        arr.put(JSONObject().apply {
            put("id", ex.id)
            put("name", ex.name)
            put("muscle", ex.muscle)
            put("equipment", ex.equipment.label)
            put("type", ex.type.code)
        })
    }
    return arr.toString()
}

// ─── Body measurements ───

fun measurementsFromJsonString(s: String?): List<BodyMeasurement> {
    if (s.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(s)
        val out = mutableListOf<BodyMeasurement>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(BodyMeasurement(
                date = o.optString("date"),
                chest = o.optDouble("chest", 0.0),
                waist = o.optDouble("waist", 0.0),
                hips = o.optDouble("hips", 0.0),
                arm = o.optDouble("arm", 0.0),
                thigh = o.optDouble("thigh", 0.0),
                calf = o.optDouble("calf", 0.0),
                unit = o.optString("unit", "cm"),
            ))
        }
        out
    } catch (_: Exception) { emptyList() }
}

fun measurementsToJsonString(list: List<BodyMeasurement>): String {
    val arr = JSONArray()
    for (m in list) {
        arr.put(JSONObject().apply {
            put("date", m.date)
            if (m.chest > 0 && m.chest.isFinite()) put("chest", m.chest)
            if (m.waist > 0 && m.waist.isFinite()) put("waist", m.waist)
            if (m.hips > 0 && m.hips.isFinite()) put("hips", m.hips)
            if (m.arm > 0 && m.arm.isFinite()) put("arm", m.arm)
            if (m.thigh > 0 && m.thigh.isFinite()) put("thigh", m.thigh)
            if (m.calf > 0 && m.calf.isFinite()) put("calf", m.calf)
            put("unit", m.unit)
        })
    }
    return arr.toString()
}
