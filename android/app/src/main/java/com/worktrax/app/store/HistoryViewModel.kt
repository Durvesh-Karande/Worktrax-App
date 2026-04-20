package com.worktrax.app.store

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.worktrax.app.data.Workout
import com.worktrax.app.lib.Storage
import com.worktrax.app.lib.workoutsFromJsonString
import com.worktrax.app.lib.workoutsToJsonString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    }

    fun remove(id: String) {
        val next = _workouts.value.filterNot { it.id == id }
        _workouts.value = next
        persist(next)
    }

    fun clear() {
        _workouts.value = emptyList()
        persist(emptyList())
    }
}
