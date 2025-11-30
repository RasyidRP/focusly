package com.example.focuslist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.abs

class TimerService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var timerJob: Job? = null
    private lateinit var taskDao: TaskDao
    private var currentTaskId: String? = null

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(applicationContext)
        taskDao = database.taskDao()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                if (taskId != null) {
                    startTimer(taskId)
                }
            }
            ACTION_STOP -> {
                stopTimer()
            }
            null -> {
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedTaskId = prefs.getString(KEY_RUNNING_TASK_ID, null)
                if (savedTaskId != null) {
                    startTimer(savedTaskId)
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startTimer(taskId: String) {
        timerJob?.cancel()
        currentTaskId = taskId

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RUNNING_TASK_ID, taskId).apply()

        startForeground(NOTIFICATION_ID, createNotification("Focusly", "Loading task..."))

        timerJob = serviceScope.launch {
            var task = taskDao.getTaskById(taskId)
            if (task == null || task.isCompleted) {
                stopTimer()
                return@launch
            }

            setTaskRunningState(taskId, true)

            val sessionStartTimeMillis = System.currentTimeMillis()
            val secondsRemainingAtSessionStart = task.remainingSeconds

            while (isActive) {
                task = taskDao.getTaskById(taskId)

                if (task == null || task.isCompleted) {
                    stopTimer()
                    break
                }

                updateNotification(task)

                delay(1000)

                val currentTimeMillis = System.currentTimeMillis()

                val millisPassed = currentTimeMillis - sessionStartTimeMillis
                val secondsPassed = millisPassed / 1000

                val newRemaining = secondsRemainingAtSessionStart - secondsPassed

                if (newRemaining != task.remainingSeconds) {
                    taskDao.update(task.copy(remainingSeconds = newRemaining))
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        val taskId = currentTaskId

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_RUNNING_TASK_ID).apply()

        if (taskId != null) {
            serviceScope.launch {
                setTaskRunningState(taskId, false)
            }.invokeOnCompletion {
                finishStoppingService()
            }
        } else {
            finishStoppingService()
        }
    }

    private fun finishStoppingService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        currentTaskId = null
    }

    private suspend fun setTaskRunningState(taskId: String, isRunning: Boolean) {
        val task = taskDao.getTaskById(taskId)
        if (task != null) {
            if (task.isRunning != isRunning) {
                taskDao.update(task.copy(isRunning = isRunning))
            }
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingOpenIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", pendingStopIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(task: Task) {
        val titleText = if (task.remainingSeconds < 0) "${task.name} (Overtime!)" else task.name
        val timeText = formatSeconds(task.remainingSeconds)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(titleText, "Time: $timeText"))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Timer Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private fun formatSeconds(seconds: Long): String {
        val isNegative = seconds < 0
        val absSeconds = abs(seconds)

        val h = absSeconds / 3600
        val m = (absSeconds % 3600) / 60
        val s = absSeconds % 60
        val formatted = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)

        return if (isNegative) "-$formatted" else formatted
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val CHANNEL_ID = "TimerServiceChannel"
        const val NOTIFICATION_ID = 1
        private const val PREFS_NAME = "TimerServicePrefs"
        private const val KEY_RUNNING_TASK_ID = "running_task_id"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}