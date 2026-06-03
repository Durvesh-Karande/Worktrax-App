package com.worktrax.app.lib

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
    private val db get() = FirebaseFirestore.getInstance()
    private val uid get() = auth.currentUser?.uid ?: ""

    // ── Settings ──

    suspend fun saveSettings(s: SettingsData) {
        val data = hashMapOf(
            "name" to s.name,
            "unit" to s.unit,
            "theme" to s.theme,
        )
        db.collection("users").document(uid)
            .collection("profile").document("settings")
            .set(data).await()
    }

    suspend fun loadSettings(): SettingsData? {
        val snap = db.collection("users").document(uid)
            .collection("profile").document("settings").get().await()
        if (!snap.exists()) return null
        val d = snap.data ?: return null
        return SettingsData(
            name = d["name"] as? String ?: "",
            unit = d["unit"] as? String ?: "kg",
            theme = d["theme"] as? String ?: "system",
        )
    }

    // ── Workouts ──

    suspend fun addWorkout(w: Workout) {
        db.collection("users").document(uid)
            .collection("workouts").document(w.id)
            .set(mapOf("data" to w.toJson().toString())).await()
    }

    suspend fun saveWorkouts(workouts: List<Workout>) {
        val batch = db.batch()
        workouts.forEach { w ->
            val ref = db.collection("users").document(uid)
                .collection("workouts").document(w.id)
            batch.set(ref, mapOf("data" to w.toJson().toString()))
        }
        batch.commit().await()
    }

    suspend fun removeWorkout(id: String) {
        db.collection("users").document(uid)
            .collection("workouts").document(id).delete().await()
    }

    suspend fun loadWorkouts(): List<Workout> {
        val snap = db.collection("users").document(uid)
            .collection("workouts").get().await()
        return snap.documents.mapNotNull { doc ->
            val raw = doc.getString("data") ?: return@mapNotNull null
            org.json.JSONObject(raw).let { workoutFromJson(it) }
        }
    }

    // ── Bodyweight ──

    suspend fun saveBodyweight(entries: List<BodyweightEntry>) {
        val batch = db.batch()
        val col = db.collection("users").document(uid).collection("bodyweight")
        val existing = col.get().await()
        existing.documents.forEach { batch.delete(it.reference) }
        entries.forEach { e ->
            batch.set(col.document(), hashMapOf(
                "date" to e.date,
                "weight" to e.weight,
                "unit" to e.unit.code,
            ))
        }
        batch.commit().await()
    }

    suspend fun loadBodyweight(): List<BodyweightEntry> {
        val snap = db.collection("users").document(uid)
            .collection("bodyweight").get().await()
        return snap.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            BodyweightEntry(
                date = d["date"] as? String ?: return@mapNotNull null,
                weight = (d["weight"] as? Number)?.toDouble() ?: return@mapNotNull null,
                unit = WeightUnit.from(d["unit"] as? String ?: "kg"),
            )
        }
    }

    // ── Measurements ──

    suspend fun saveMeasurements(entries: List<BodyMeasurement>) {
        val batch = db.batch()
        val col = db.collection("users").document(uid).collection("measurements")
        val existing = col.get().await()
        existing.documents.forEach { batch.delete(it.reference) }
        entries.forEach { m ->
            batch.set(col.document(), hashMapOf(
                "date" to m.date,
                "chest" to m.chest,
                "waist" to m.waist,
                "hips" to m.hips,
                "arm" to m.arm,
                "thigh" to m.thigh,
                "calf" to m.calf,
                "unit" to m.unit,
            ))
        }
        batch.commit().await()
    }

    suspend fun loadMeasurements(): List<BodyMeasurement> {
        val snap = db.collection("users").document(uid)
            .collection("measurements").get().await()
        return snap.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            BodyMeasurement(
                date = d["date"] as? String ?: return@mapNotNull null,
                chest = (d["chest"] as? Number)?.toDouble() ?: 0.0,
                waist = (d["waist"] as? Number)?.toDouble() ?: 0.0,
                hips = (d["hips"] as? Number)?.toDouble() ?: 0.0,
                arm = (d["arm"] as? Number)?.toDouble() ?: 0.0,
                thigh = (d["thigh"] as? Number)?.toDouble() ?: 0.0,
                calf = (d["calf"] as? Number)?.toDouble() ?: 0.0,
                unit = d["unit"] as? String ?: "cm",
            )
        }
    }

    // ── Routines ──

    suspend fun saveRoutines(routines: List<Routine>) {
        val batch = db.batch()
        val col = db.collection("users").document(uid).collection("routines")
        val existing = col.get().await()
        existing.documents.forEach { batch.delete(it.reference) }
        routines.forEach { r ->
            batch.set(col.document(), hashMapOf(
                "id" to r.id,
                "name" to r.name,
                "type" to r.type.code,
                "exerciseIds" to r.exerciseIds,
                "createdAt" to r.createdAt,
            ))
        }
        batch.commit().await()
    }

    suspend fun loadRoutines(): List<Routine> {
        val snap = db.collection("users").document(uid)
            .collection("routines").get().await()
        return snap.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            Routine(
                id = d["id"] as? String ?: return@mapNotNull null,
                name = d["name"] as? String ?: "",
                type = WorkoutType.from(d["type"] as? String ?: "strength"),
                exerciseIds = (d["exerciseIds"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                createdAt = d["createdAt"] as? String ?: "",
            )
        }
    }

    // ── Custom Exercises ──

    suspend fun saveCustomExercises(exercises: List<ExerciseDef>) {
        val batch = db.batch()
        val col = db.collection("users").document(uid).collection("custom_exercises")
        val existing = col.get().await()
        existing.documents.forEach { batch.delete(it.reference) }
        exercises.forEach { ex ->
            batch.set(col.document(), hashMapOf(
                "id" to ex.id,
                "name" to ex.name,
                "muscle" to ex.muscle,
                "equipment" to ex.equipment.label,
                "type" to ex.type.code,
            ))
        }
        batch.commit().await()
    }

    suspend fun loadCustomExercises(): List<ExerciseDef> {
        val snap = db.collection("users").document(uid)
            .collection("custom_exercises").get().await()
        return snap.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            ExerciseDef(
                id = d["id"] as? String ?: return@mapNotNull null,
                name = d["name"] as? String ?: "",
                muscle = d["muscle"] as? String ?: "",
                equipment = Equipment.from(d["equipment"] as? String ?: "Barbell"),
                type = WorkoutType.from(d["type"] as? String ?: "strength"),
            )
        }
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
}
