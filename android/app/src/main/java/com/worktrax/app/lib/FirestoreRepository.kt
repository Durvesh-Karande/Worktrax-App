package com.worktrax.app.lib

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.worktrax.app.data.BodyMeasurement
import com.worktrax.app.data.BodyweightEntry
import com.worktrax.app.data.Equipment
import com.worktrax.app.data.ExerciseDef
import com.worktrax.app.data.Routine
import com.worktrax.app.data.SettingsData
import com.worktrax.app.data.WeightUnit
import com.worktrax.app.data.Workout
import com.worktrax.app.data.WorkoutType
import kotlinx.coroutines.tasks.await

object FirestoreRepository {
    private val auth get() = FirebaseAuth.getInstance()
    private val rtdb get() = FirebaseDatabase.getInstance()
    private val uid get() = auth.currentUser?.uid ?: ""

    private fun userRef() = rtdb.getReference("users").child(uid)

    // ── Settings ──

    suspend fun saveSettings(s: SettingsData) {
        userRef().child("settings").setValue(mapOf(
            "name" to s.name,
            "unit" to s.unit,
            "theme" to s.theme,
        )).await()
    }

    suspend fun loadSettings(): SettingsData? {
        val snap = userRef().child("settings").get().await()
        val d = snap.value as? Map<*, *> ?: return null
        return SettingsData(
            name = d["name"] as? String ?: "",
            unit = d["unit"] as? String ?: "kg",
            theme = d["theme"] as? String ?: "system",
        )
    }

    // ── Workouts ──

    suspend fun addWorkout(w: Workout) {
        userRef().child("workouts").child(w.id)
            .setValue(mapOf("data" to w.toJson().toString())).await()
    }

    suspend fun saveWorkouts(workouts: List<Workout>) {
        val map = HashMap<String, Any>()
        workouts.forEach { w ->
            map["workouts/${w.id}"] = mapOf("data" to w.toJson().toString())
        }
        userRef().updateChildren(map).await()
    }

    suspend fun removeWorkout(id: String) {
        userRef().child("workouts").child(id).removeValue().await()
    }

    suspend fun loadWorkouts(): List<Workout> {
        val snap = userRef().child("workouts").get().await()
        val children = snap.children
        val result = mutableListOf<Workout>()
        for (child in children) {
            val raw = child.child("data").value as? String ?: continue
            try {
                result.add(workoutFromJson(org.json.JSONObject(raw)))
            } catch (_: Exception) {}
        }
        return result
    }

    // ── Bodyweight ──

    suspend fun saveBodyweight(entries: List<BodyweightEntry>) {
        val ref = userRef().child("bodyweight")
        ref.removeValue().await()
        val map = HashMap<String, Any>()
        entries.forEach { e ->
            val key = ref.push().key ?: uid()
            map[key] = mapOf(
                "date" to e.date,
                "weight" to e.weight,
                "unit" to e.unit.code,
            )
        }
        if (map.isNotEmpty()) ref.updateChildren(map).await()
    }

    suspend fun loadBodyweight(): List<BodyweightEntry> {
        val snap = userRef().child("bodyweight").get().await()
        val result = mutableListOf<BodyweightEntry>()
        for (child in snap.children) {
            val d = child.value as? Map<*, *> ?: continue
            val date = d["date"] as? String ?: continue
            val weight = (d["weight"] as? Number)?.toDouble() ?: continue
            result.add(BodyweightEntry(
                date = date,
                weight = weight,
                unit = WeightUnit.from(d["unit"] as? String ?: "kg"),
            ))
        }
        return result
    }

    // ── Measurements ──

    suspend fun saveMeasurements(entries: List<BodyMeasurement>) {
        val ref = userRef().child("measurements")
        ref.removeValue().await()
        val map = HashMap<String, Any>()
        entries.forEach { m ->
            val key = ref.push().key ?: uid()
            map[key] = mapOf(
                "date" to m.date,
                "chest" to m.chest,
                "waist" to m.waist,
                "hips" to m.hips,
                "arm" to m.arm,
                "thigh" to m.thigh,
                "calf" to m.calf,
                "unit" to m.unit,
            )
        }
        if (map.isNotEmpty()) ref.updateChildren(map).await()
    }

    suspend fun loadMeasurements(): List<BodyMeasurement> {
        val snap = userRef().child("measurements").get().await()
        val result = mutableListOf<BodyMeasurement>()
        for (child in snap.children) {
            val d = child.value as? Map<*, *> ?: continue
            val date = d["date"] as? String ?: continue
            result.add(BodyMeasurement(
                date = date,
                chest = (d["chest"] as? Number)?.toDouble() ?: 0.0,
                waist = (d["waist"] as? Number)?.toDouble() ?: 0.0,
                hips = (d["hips"] as? Number)?.toDouble() ?: 0.0,
                arm = (d["arm"] as? Number)?.toDouble() ?: 0.0,
                thigh = (d["thigh"] as? Number)?.toDouble() ?: 0.0,
                calf = (d["calf"] as? Number)?.toDouble() ?: 0.0,
                unit = d["unit"] as? String ?: "cm",
            ))
        }
        return result
    }

    // ── Routines ──

    suspend fun saveRoutines(routines: List<Routine>) {
        val ref = userRef().child("routines")
        ref.removeValue().await()
        val map = HashMap<String, Any>()
        routines.forEach { r ->
            val key = ref.push().key ?: uid()
            map[key] = mapOf(
                "id" to r.id,
                "name" to r.name,
                "type" to r.type.code,
                "exerciseIds" to r.exerciseIds,
                "createdAt" to r.createdAt,
            )
        }
        if (map.isNotEmpty()) ref.updateChildren(map).await()
    }

    suspend fun loadRoutines(): List<Routine> {
        val snap = userRef().child("routines").get().await()
        val result = mutableListOf<Routine>()
        for (child in snap.children) {
            val d = child.value as? Map<*, *> ?: continue
            val id = d["id"] as? String ?: continue
            result.add(Routine(
                id = id,
                name = d["name"] as? String ?: "",
                type = WorkoutType.from(d["type"] as? String ?: "strength"),
                exerciseIds = (d["exerciseIds"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                createdAt = d["createdAt"] as? String ?: "",
            ))
        }
        return result
    }

    // ── Custom Exercises ──

    suspend fun saveCustomExercises(exercises: List<ExerciseDef>) {
        val ref = userRef().child("custom_exercises")
        ref.removeValue().await()
        val map = HashMap<String, Any>()
        exercises.forEach { ex ->
            val key = ref.push().key ?: uid()
            map[key] = mapOf(
                "id" to ex.id,
                "name" to ex.name,
                "muscle" to ex.muscle,
                "equipment" to ex.equipment.label,
                "type" to ex.type.code,
            )
        }
        if (map.isNotEmpty()) ref.updateChildren(map).await()
    }

    suspend fun loadCustomExercises(): List<ExerciseDef> {
        val snap = userRef().child("custom_exercises").get().await()
        val result = mutableListOf<ExerciseDef>()
        for (child in snap.children) {
            val d = child.value as? Map<*, *> ?: continue
            val id = d["id"] as? String ?: continue
            result.add(ExerciseDef(
                id = id,
                name = d["name"] as? String ?: "",
                muscle = d["muscle"] as? String ?: "",
                equipment = Equipment.from(d["equipment"] as? String ?: "Barbell"),
                type = WorkoutType.from(d["type"] as? String ?: "strength"),
            ))
        }
        return result
    }

    // ── Migration ──

    suspend fun migrateLocalData(ctx: Context) {
        val rawSettings = Storage.getString(ctx, Storage.KEY_SETTINGS)
        if (rawSettings != null) {
            val s = SettingsData.fromJson(rawSettings)
            if (s != null) saveSettings(s)
        }

        val rawHistory = Storage.getString(ctx, Storage.KEY_HISTORY)
        if (!rawHistory.isNullOrBlank()) {
            val workouts = workoutsFromJsonString(rawHistory)
            if (workouts.isNotEmpty()) saveWorkouts(workouts)
        }

        val rawBw = Storage.getString(ctx, Storage.KEY_BODYWEIGHT)
        if (!rawBw.isNullOrBlank()) {
            saveBodyweight(bodyweightFromJsonString(rawBw))
        }

        val rawMeas = Storage.getString(ctx, Storage.KEY_BODY_MEASUREMENTS)
        if (!rawMeas.isNullOrBlank()) {
            saveMeasurements(measurementsFromJsonString(rawMeas))
        }

        val rawRt = Storage.getString(ctx, Storage.KEY_ROUTINES)
        if (!rawRt.isNullOrBlank()) {
            saveRoutines(routinesFromJsonString(rawRt))
        }

        val rawCust = Storage.getString(ctx, Storage.KEY_CUSTOM_EXERCISES)
        if (!rawCust.isNullOrBlank()) {
            saveCustomExercises(customExercisesFromJsonString(rawCust))
        }

        Storage.putString(ctx, Storage.KEY_MIGRATED, "1")
    }

    fun hasMigrated(ctx: Context): Boolean =
        Storage.getString(ctx, Storage.KEY_MIGRATED) == "1"

    // ── Account Deletion ──

    suspend fun deleteAllUserData() {
        userRef().removeValue().await()
    }
}
