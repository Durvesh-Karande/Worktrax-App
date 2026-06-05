package com.worktrax.app.store

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.worktrax.app.data.SettingsData
import com.worktrax.app.data.ThemeMode
import com.worktrax.app.data.WeightUnit
import com.worktrax.app.lib.AnalyticsHelper
import com.worktrax.app.lib.FirestoreRepository
import com.worktrax.app.lib.Storage
import com.worktrax.app.lib.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class SettingsState(
    val name: String = "",
    val unit: WeightUnit = WeightUnit.KG,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private fun load(): SettingsState {
        val raw = Storage.getString(getApplication(), Storage.KEY_SETTINGS)
        if (raw.isNullOrBlank()) return SettingsState()
        return try {
            val o = JSONObject(raw)
            SettingsState(
                name = o.optString("name", ""),
                unit = WeightUnit.from(o.optString("unit", "kg")),
                theme = ThemeMode.from(o.optString("theme", "system")),
            )
        } catch (_: Exception) {
            SettingsState()
        }
    }

    private fun loadReminderPrefs(): SettingsState {
        val ctx: android.content.Context = getApplication()
        val prefs = Storage.prefs(ctx)
        val enabled = prefs.getBoolean(Storage.KEY_REMINDER_ENABLED, false)
        val hour = prefs.getInt(Storage.KEY_REMINDER_HOUR, 18)
        val minute = prefs.getInt(Storage.KEY_REMINDER_MINUTE, 0)
        return _state.value.copy(
            reminderEnabled = enabled,
            reminderHour = hour,
            reminderMinute = minute,
        )
    }

    fun refreshNotificationState() {
        _state.value = loadReminderPrefs()
    }

    private fun persist(s: SettingsState) {
        persistLocal(s)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FirestoreRepository.saveSettings(
                    SettingsData(s.name, s.unit.code, s.theme.code)
                )
            } catch (_: Exception) {}
        }
    }

    private fun persistLocal(s: SettingsState) {
        val o = JSONObject().apply {
            put("name", s.name)
            put("unit", s.unit.code)
            put("theme", s.theme.code)
        }
        Storage.putString(getApplication(), Storage.KEY_SETTINGS, o.toString())
    }

    fun sync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val remote = FirestoreRepository.loadSettings()
                if (remote != null) {
                    val newState = SettingsState(
                        name = remote.name,
                        unit = WeightUnit.from(remote.unit),
                        theme = ThemeMode.from(remote.theme)
                    )
                    _state.value = newState
                    persistLocal(newState)
                }
            } catch (_: Exception) {}
        }
    }

    fun setName(name: String) { _state.value = _state.value.copy(name = name).also(::persist) }
    fun setUnit(unit: WeightUnit) { _state.value = _state.value.copy(unit = unit).also(::persist) }
    fun setTheme(theme: ThemeMode) { _state.value = _state.value.copy(theme = theme).also(::persist) }
    fun reset() { _state.value = SettingsState() }

    fun setReminderEnabled(enabled: Boolean) {
        val ctx: android.content.Context = getApplication()
        val s = _state.value.copy(reminderEnabled = enabled)
        _state.value = s
        val editor = Storage.prefs(ctx).edit()
        editor.putBoolean(Storage.KEY_REMINDER_ENABLED, enabled)
        editor.apply()
        WorkScheduler.scheduleReminder(ctx, s.reminderHour, s.reminderMinute)
    }

    fun setReminderTime(hour: Int, minute: Int) {
        val ctx: android.content.Context = getApplication()
        val s = _state.value.copy(reminderHour = hour, reminderMinute = minute)
        _state.value = s
        val editor = Storage.prefs(ctx).edit()
        editor.putInt(Storage.KEY_REMINDER_HOUR, hour)
        editor.putInt(Storage.KEY_REMINDER_MINUTE, minute)
        editor.apply()
        if (s.reminderEnabled) {
            WorkScheduler.scheduleReminder(ctx, hour, minute)
        }
    }
}
