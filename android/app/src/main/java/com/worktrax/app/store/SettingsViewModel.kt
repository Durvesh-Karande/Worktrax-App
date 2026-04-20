package com.worktrax.app.store

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.worktrax.app.data.ThemeMode
import com.worktrax.app.data.WeightUnit
import com.worktrax.app.lib.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class SettingsState(
    val name: String = "Amal Okafor",
    val unit: WeightUnit = WeightUnit.KG,
    val theme: ThemeMode = ThemeMode.SYSTEM,
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
                name = o.optString("name", "Amal Okafor"),
                unit = WeightUnit.from(o.optString("unit", "kg")),
                theme = ThemeMode.from(o.optString("theme", "system")),
            )
        } catch (_: Exception) {
            SettingsState()
        }
    }

    private fun persist(s: SettingsState) {
        val o = JSONObject().apply {
            put("name", s.name)
            put("unit", s.unit.code)
            put("theme", s.theme.code)
        }
        Storage.putString(getApplication(), Storage.KEY_SETTINGS, o.toString())
    }

    fun setName(name: String) { _state.value = _state.value.copy(name = name).also(::persist) }
    fun setUnit(unit: WeightUnit) { _state.value = _state.value.copy(unit = unit).also(::persist) }
    fun setTheme(theme: ThemeMode) { _state.value = _state.value.copy(theme = theme).also(::persist) }
}
