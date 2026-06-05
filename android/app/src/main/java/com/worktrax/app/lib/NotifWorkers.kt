package com.worktrax.app.lib

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.worktrax.app.data.Workout
import java.util.concurrent.TimeUnit
import kotlin.math.max

class WorkoutReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!Storage.prefs(ctx).getBoolean(Storage.KEY_REMINDER_ENABLED, false)) return Result.success()

        // Check if any workout was logged today
        val today = todayIsoDate()
        val raw = Storage.getString(ctx, Storage.KEY_HISTORY)
        val workouts = workoutsFromJsonString(raw)
        val workedOutToday = workouts.any { it.date.startsWith(today) }

        if (!workedOutToday) {
            NotifHelper.showWorkoutReminder(ctx)
        }
        return Result.success()
    }
}

class StreakCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val raw = Storage.getString(ctx, Storage.KEY_HISTORY)
        val workouts = workoutsFromJsonString(raw)
        if (workouts.isEmpty()) return Result.success()

        val sorted = workouts.sortedByDescending { it.date }
        val lastDate = sorted.first().date.take(10)
        val today = todayIsoDate()
        val daysSince = daysBetween(lastDate, today)

        // Calculate user's typical rest gap (median days between consecutive workouts)
        val gaps = mutableListOf<Int>()
        for (i in 0 until sorted.size - 1) {
            val d1 = sorted[i].date.take(10)
            val d2 = sorted[i + 1].date.take(10)
            if (d1 != d2) {
                val gap = daysBetween(d2, d1)
                if (gap > 0) gaps.add(gap)
            }
        }
        val typicalGap = if (gaps.size >= 2) {
            gaps.sorted().let { it[it.size / 2] }
        } else {
            3 // default for new users
        }

        val threshold = max(typicalGap + 1, (typicalGap * 1.5).toInt())
        if (daysSince >= threshold) {
            val lastNotified = Storage.prefs(ctx).getString(Storage.KEY_STREAK_NOTIFIED, null)
            if (lastNotified != lastDate) {
                NotifHelper.showStreakAtRisk(ctx)
                val editor = Storage.prefs(ctx).edit()
                editor.putString(Storage.KEY_STREAK_NOTIFIED, lastDate)
                editor.apply()
            }
        }
        return Result.success()
    }
}

private fun daysBetween(from: String, to: String): Int {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val fromDate = sdf.parse(from) ?: return 0
    val toDate = sdf.parse(to) ?: return 0
    val diff = toDate.time - fromDate.time
    return (diff / (1000 * 60 * 60 * 24)).toInt()
}

object WorkScheduler {
    private const val REMINDER_TAG = "workout_reminder_daily"
    private const val STREAK_TAG = "streak_check_daily"

    fun scheduleReminder(ctx: Context, hour: Int, minute: Int) {
        val prefs = Storage.prefs(ctx)
        val enabled = prefs.getBoolean(Storage.KEY_REMINDER_ENABLED, false)
        val mgr = WorkManager.getInstance(ctx)

        if (!enabled) {
            mgr.cancelUniqueWork(REMINDER_TAG)
            return
        }

        // Calculate delay until next occurrence of the specified time
        val cal = java.util.Calendar.getInstance()
        val nowMillis = cal.timeInMillis
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, minute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        var targetMillis = cal.timeInMillis
        if (targetMillis <= nowMillis) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            targetMillis = cal.timeInMillis
        }
        val initialDelay = targetMillis - nowMillis

        val request = PeriodicWorkRequestBuilder<WorkoutReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(REMINDER_TAG)
            .build()

        mgr.enqueueUniquePeriodicWork(
            REMINDER_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleStreakCheck(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<StreakCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(6, TimeUnit.HOURS) // first check 6h after scheduling
            .addTag(STREAK_TAG)
            .build()

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            STREAK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
