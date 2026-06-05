package com.worktrax.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.worktrax.app.data.ThemeMode
import com.worktrax.app.lib.AnalyticsHelper
import com.worktrax.app.lib.NotifHelper
import com.worktrax.app.lib.Storage
import com.worktrax.app.lib.WorkScheduler
import com.worktrax.app.store.HistoryViewModel
import com.worktrax.app.store.SettingsViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val settingsVM: SettingsViewModel by viewModels()
    private val historyVM: HistoryViewModel by viewModels()
    private var lastTheme: ThemeMode? = null

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — we handle missing permission gracefully in NotifHelper */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        AnalyticsHelper.init(this)
        lastTheme = settingsVM.state.value.theme
        applyTheme(settingsVM.state.value.theme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity_design)

        NotifHelper.createChannels(this)
        WorkScheduler.scheduleStreakCheck(this)
        scheduleReminderIfEnabled()

        requestNotifPermission()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, dest: NavDestination, _ ->
            bottomNav.visibility = if (dest.id == R.id.authFragment) View.GONE else View.VISIBLE
            if (dest.id == R.id.homeFragment) {
                settingsVM.sync()
            }
        }

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                historyVM.startSync()
            } else {
                historyVM.stopSync()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_host_fragment)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsVM.state.collectLatest { state ->
                    AnalyticsHelper.setUserProperty("theme_preference", state.theme.code)
                    AnalyticsHelper.setUserProperty("preferred_unit", state.unit.code)
                    if (state.name.isNotBlank()) {
                        AnalyticsHelper.setUserProperty("has_name", "true")
                    }
                    if (lastTheme != state.theme) {
                        lastTheme = state.theme
                        applyTheme(state.theme)
                        recreate()
                    }
                }
            }
        }
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun scheduleReminderIfEnabled() {
        val prefs = Storage.prefs(this)
        if (prefs.getBoolean(Storage.KEY_REMINDER_ENABLED, false)) {
            val h = prefs.getInt(Storage.KEY_REMINDER_HOUR, 18)
            val m = prefs.getInt(Storage.KEY_REMINDER_MINUTE, 0)
            WorkScheduler.scheduleReminder(this, h, m)
        }
    }

    private fun applyTheme(mode: ThemeMode) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
