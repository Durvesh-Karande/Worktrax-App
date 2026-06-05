package com.worktrax.app.store

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.worktrax.app.data.Workout
import com.worktrax.app.lib.FirestoreRepository
import com.worktrax.app.lib.NotifHelper
import com.worktrax.app.lib.Storage
import com.worktrax.app.lib.convertWeight
import com.worktrax.app.lib.workoutsFromJsonString
import com.worktrax.app.lib.workoutsToJsonString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val VOLUME_MULTIPLIERS = listOf(10, 25, 50, 100, 250, 500, 1000, 2000)
private const val MIN_MILESTONE = 100

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val _workouts = MutableStateFlow(load())
    val workouts: StateFlow<List<Workout>> = _workouts.asStateFlow()

    private fun load(): List<Workout> {
        val raw = Storage.getString(getApplication(), Storage.KEY_HISTORY)
        return workoutsFromJsonString(raw)
    }

    private fun persist(list: List<Workout>) {
        Storage.putString(getApplication(), Storage.KEY_HISTORY, workoutsToJsonString(list))
    }

    fun add(w: Workout) {
        val next = listOf(w) + _workouts.value
        _workouts.value = next
        persist(next)
        CoroutineScope(Dispatchers.IO).launch {
            try { FirestoreRepository.addWorkout(w) } catch (_: Exception) {}
        }
        checkVolumeMilestone()
    }

    private fun checkVolumeMilestone() {
        val ctx: android.content.Context = getApplication()
        val prefs = Storage.prefs(ctx)
        val lastNotified = prefs.getInt(Storage.KEY_LAST_VOLUME_MILESTONE, 0)
        val unit = com.worktrax.app.data.WeightUnit.KG

        var total = 0.0
        var strengthWorkoutCount = 0
        for (workout in _workouts.value) {
            var workoutVol = 0.0
            var hasStrength = false
            for (ex in workout.exercises) {
                for (s in ex.sets) {
                    if (s.metricType != "strength") continue
                    hasStrength = true
                    val weight = if (s.unit == unit) s.weight else convertWeight(s.weight, s.unit, unit)
                    workoutVol += weight * s.reps
                }
            }
            if (hasStrength && workoutVol > 0) {
                total += workoutVol
                strengthWorkoutCount++
            }
        }

        if (strengthWorkoutCount == 0) return
        val avgPerWorkout = total / strengthWorkoutCount
        if (avgPerWorkout <= 0) return

        val totalInt = total.toInt()
        for (multiplier in VOLUME_MULTIPLIERS) {
            val milestone = maxOf((avgPerWorkout * multiplier).toInt(), MIN_MILESTONE)
            if (totalInt >= milestone && lastNotified < milestone) {
                NotifHelper.showVolumeMilestone(ctx, milestone, multiplier)
                val editor = prefs.edit()
                editor.putInt(Storage.KEY_LAST_VOLUME_MILESTONE, milestone)
                editor.apply()
                break
            }
        }
    }

    fun remove(id: String) {
        val next = _workouts.value.filterNot { it.id == id }
        _workouts.value = next
        persist(next)
        CoroutineScope(Dispatchers.IO).launch {
            try { FirestoreRepository.removeWorkout(id) } catch (_: Exception) {}
        }
    }

    fun clear() {
        _workouts.value = emptyList()
        persist(emptyList())
    }

    fun refresh() {
        _workouts.value = load()
    }
}
