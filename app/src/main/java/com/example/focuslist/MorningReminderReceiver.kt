package com.example.focuslist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Background receiver triggered by the OS AlarmManager.
 * Queries the Room database to dynamically check the user's canvas state
 * and issues localized notifications to enforce the Daily Highlight habit.
 */
class MorningReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val activeTasks = db.taskDao().getActiveTasksSnapshot()

                val hasHighlight = activeTasks.any { it.category == TaskCategory.HIGHLIGHT }

                if (!hasHighlight) {
                    val message = if (activeTasks.isEmpty()) {
                        "Your canvas is empty. What is your Daily Highlight today?"
                    } else {
                        "You have tasks queued up, but no Daily Highlight. Set your priority!"
                    }

                    showNotification(context, message)
                }

                scheduleMorningReminder(context)

            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, message: String) {
        val channelId = "morning_reminder_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Morning Reminder", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val pendingOpenIntent = PendingIntent.getActivity(
            context, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("focusly.")
            .setContentText(message)
            .setContentIntent(pendingOpenIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2001, notification)
    }
}