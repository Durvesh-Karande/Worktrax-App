package com.worktrax.app.lib

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.worktrax.app.R

object NotifHelper {
    const val CHANNEL_REMINDERS = "workout_reminders"
    const val CHANNEL_MILESTONES = "workout_milestones"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Workout Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders to work out and streak alerts"
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_MILESTONES, "Milestones", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Volume milestones and achievements"
            }
        )
    }

    private fun launchIntent(ctx: Context): PendingIntent {
        val pkg = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, 0, pkg,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun show(ctx: Context, channelId: String, title: String, body: String) {
        val id = title.hashCode()
        val n = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(launchIntent(ctx))
            .setPriority(if (channelId == CHANNEL_MILESTONES) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {}
    }

    fun showWorkoutReminder(ctx: Context) {
        show(ctx, CHANNEL_REMINDERS,
            "Time for a workout?",
            "You haven't logged a workout today. Let's get after it!")
    }

    fun showStreakAtRisk(ctx: Context) {
        show(ctx, CHANNEL_REMINDERS,
            "Don't lose your streak!",
            "It's been a few days since your last workout. Time to get back at it!")
    }

    fun showVolumeMilestone(ctx: Context, milestone: Int, multiplier: Int) {
        val label = numberWithCommas(milestone)
        show(ctx, CHANNEL_MILESTONES,
            "Volume milestone: $label kg!",
            "That's ${multiplier}x your average workout. Keep it up!")
    }
}
