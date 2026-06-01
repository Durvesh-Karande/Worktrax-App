package com.worktrax.app.store

import androidx.lifecycle.ViewModel
import com.worktrax.app.data.ExerciseEntry
import com.worktrax.app.data.SetEntry
import com.worktrax.app.data.WeightUnit
import com.worktrax.app.data.Workout
import com.worktrax.app.data.WorkoutType
import com.worktrax.app.lib.isoEpochMs
import com.worktrax.app.lib.nowEpochMs
import com.worktrax.app.lib.nowIso
import com.worktrax.app.lib.uid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

data class SessionState(
    val active: Boolean = false,
    val type: WorkoutType? = null,
    val muscle: String? = null,
    val startedAt: String? = null,
    val currentExerciseId: String? = null,
    val exercises: List<ExerciseEntry> = emptyList(),
)

class SessionViewModel : ViewModel() {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun start(type: WorkoutType) {
        _state.value = SessionState(
            active = true,
            type = type,
            startedAt = nowIso(),
        )
    }

    fun setMuscle(muscle: String) {
        _state.value = _state.value.copy(muscle = muscle)
    }

    fun pickExercise(id: String, name: String, muscle: String) {
        val s = _state.value
        val existing = s.exercises.find { it.id == id }
        if (existing != null) {
            _state.value = s.copy(currentExerciseId = id)
            return
        }
        val entry = ExerciseEntry(id = id, name = name, muscle = muscle, sets = emptyList())
        _state.value = s.copy(
            currentExerciseId = id,
            exercises = s.exercises + entry,
        )
    }

    fun addSet(reps: Int, weight: Double, unit: WeightUnit, warmup: Boolean = false, rpe: Int? = null) {
        val s = _state.value
        val cur = s.currentExerciseId ?: return
        val full = SetEntry(reps = reps, weight = weight, unit = unit, at = nowIso(), warmup = warmup, rpe = rpe)
        val next = s.exercises.map { e ->
            if (e.id == cur) e.copy(sets = e.sets + full) else e
        }
        _state.value = s.copy(exercises = next)
    }

    fun removeLastSet() {
        val s = _state.value
        val cur = s.currentExerciseId ?: return
        val next = s.exercises.map { e ->
            if (e.id == cur) e.copy(sets = e.sets.dropLast(1)) else e
        }
        _state.value = s.copy(exercises = next)
    }

    fun finish(): Workout? {
        val s = _state.value
        if (!s.active || s.type == null || s.startedAt == null) return null
        val durationSec = max(1, ((nowEpochMs() - isoEpochMs(s.startedAt)) / 1000).toInt())
        val w = Workout(
            id = uid("w"),
            date = s.startedAt,
            type = s.type,
            durationSec = durationSec,
            exercises = s.exercises.filter { it.sets.isNotEmpty() },
        )
        _state.value = SessionState()
        return w
    }

    fun discard() { _state.value = SessionState() }

    fun seedFromWorkout(w: Workout) {
        _state.value = SessionState(
            active = true,
            type = w.type,
            muscle = w.exercises.firstOrNull()?.muscle,
            startedAt = nowIso(),
            currentExerciseId = null,
            exercises = w.exercises.map {
                ExerciseEntry(id = it.id, name = it.name, muscle = it.muscle, sets = emptyList())
            },
        )
    }
}

fun lastSetForExercise(
    workouts: List<Workout>,
    exerciseId: String,
): SetEntry? {
    // Return the last non-warmup set from the most recent workout containing this exercise
    val sorted = workouts.sortedByDescending { it.date }
    for (w in sorted) {
        val ex = w.exercises.find { it.id == exerciseId } ?: continue
        val last = ex.sets.lastOrNull() ?: continue
        return last
    }
    return null
}

fun bestSetForExercise(
    workouts: List<Workout>,
    exerciseId: String,
): SetEntry? {
    // Return the best set (highest estimated 1RM) for this exercise
    var best: SetEntry? = null
    var bestScore = 0.0
    for (w in workouts) {
        val ex = w.exercises.find { it.id == exerciseId } ?: continue
        for (s in ex.sets) {
            val score = s.weight * (1.0 + s.reps / 30.0) // Epley formula
            if (score > bestScore) {
                bestScore = score
                best = s
            }
        }
    }
    return best
}

fun estimated1rm(weight: Double, reps: Int): Double {
    if (reps == 1) return weight
    return weight * (1.0 + reps / 30.0)
}

fun bestSetAcrossExercises(
    workouts: List<Workout>,
    exerciseName: String,
): SetEntry? {
    var best: SetEntry? = null
    var bestScore = 0.0
    for (w in workouts) {
        for (ex in w.exercises) {
            if (!ex.name.equals(exerciseName, ignoreCase = true)) continue
            for (s in ex.sets) {
                val score = s.weight * (1.0 + s.reps / 30.0)
                if (score > bestScore) {
                    bestScore = score
                    best = s
                }
            }
        }
    }
    return best
}
