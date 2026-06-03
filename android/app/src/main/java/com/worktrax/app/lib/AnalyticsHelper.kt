package com.worktrax.app.lib

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsHelper {
    private var analytics: FirebaseAnalytics? = null

    fun init(ctx: Context) {
        analytics = FirebaseAnalytics.getInstance(ctx)
    }

    fun screenView(screenName: String) {
        val b = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        }
        analytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, b)
    }

    fun workoutStarted(type: String, exerciseCount: Int = 0) {
        analytics?.logEvent("workout_started", Bundle().apply {
            putString("workout_type", type)
            putInt("exercise_count", exerciseCount)
        })
    }

    fun workoutCompleted(type: String, totalExercises: Int, totalVolume: Double, durationSec: Int) {
        analytics?.logEvent("workout_completed", Bundle().apply {
            putString("workout_type", type)
            putInt("total_exercises", totalExercises)
            putDouble("total_volume", totalVolume)
            putInt("duration_sec", durationSec)
        })
    }

    fun workoutCancelled(type: String) {
        analytics?.logEvent("workout_cancelled", Bundle().apply {
            putString("workout_type", type)
        })
    }

    fun exercisePicked(exerciseName: String, muscle: String) {
        analytics?.logEvent("exercise_picked", Bundle().apply {
            putString("exercise_name", exerciseName)
            putString("muscle", muscle)
        })
    }

    fun setLogged(exerciseName: String, reps: Int, weight: Double, warmup: Boolean, rpe: Int?) {
        analytics?.logEvent("set_logged", Bundle().apply {
            putString("exercise_name", exerciseName)
            putInt("reps", reps)
            putDouble("weight", weight)
            putInt("is_warmup", if (warmup) 1 else 0)
            if (rpe != null) putInt("rpe", rpe)
        })
    }

    fun historyItemViewed(workoutType: String) {
        analytics?.logEvent("history_item_viewed", Bundle().apply {
            putString("workout_type", workoutType)
        })
    }

    fun workoutDeleted(workoutType: String) {
        analytics?.logEvent("workout_deleted", Bundle().apply {
            putString("workout_type", workoutType)
        })
    }

    fun themeChanged(theme: String) {
        analytics?.logEvent("theme_changed", Bundle().apply {
            putString("theme", theme)
        })
    }

    fun unitChanged(unit: String) {
        analytics?.logEvent("unit_changed", Bundle().apply {
            putString("unit", unit)
        })
    }

    fun nameSet() {
        analytics?.logEvent("name_set", null)
    }

    fun bodyweightLogged(unit: String) {
        analytics?.logEvent("bodyweight_logged", Bundle().apply {
            putString("unit", unit)
        })
    }

    fun measurementLogged() {
        analytics?.logEvent("measurement_logged", null)
    }

    fun reportDownloaded(format: String) {
        analytics?.logEvent("report_downloaded", Bundle().apply {
            putString("format", format)
        })
    }

    fun routineSaved() {
        analytics?.logEvent("routine_saved", null)
    }

    fun workoutShared() {
        analytics?.logEvent("workout_shared", null)
    }

    fun customExerciseCreated() {
        analytics?.logEvent("custom_exercise_created", null)
    }

    fun signedOut() {
        analytics?.logEvent("signed_out", null)
    }

    fun routineStarted(type: String, routineName: String) {
        analytics?.logEvent("routine_started", Bundle().apply {
            putString("workout_type", type)
            putString("routine_name", routineName)
        })
    }

    fun setUserProperty(name: String, value: String) {
        analytics?.setUserProperty(name, value)
    }
}
