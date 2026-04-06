package com.example.focuslist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.abs

/**
 * Foreground service responsible for managing the deep-work timer.
 * * Features include:
 * - Drift-free countdowns via Coroutines independent of the UI lifecycle.
 * - Custom MediaPlayer audio routing (bypassing media volume via USAGE_ALARM).
 * - Full-Screen Intents to break the device lock screen upon task completion.
 */
class TimerService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var timerJob: Job? = null
    private lateinit var taskDao: TaskDao
    private var currentTaskId: String? = null
    private var ringtone: Ringtone? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isSilenced = false
    private var wakeLock: PowerManager.WakeLock? = null

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
                    if (currentTaskId != taskId) {
                        isSilenced = false
                    }
                    startTimer(taskId)
                }
            }
            ACTION_STOP -> {
                stopAlarm()
                stopTimer()
            }
            ACTION_SILENCE -> {
                isSilenced = true
                stopAlarm()
                currentTaskId?.let { taskId ->
                    serviceScope.launch {
                        taskDao.getTaskById(taskId)?.let { updateNotification(it) }
                    }
                }
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

        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Focusly::DeepWorkWakeLock")
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(12 * 60 * 60 * 1000L)
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RUNNING_TASK_ID, taskId).apply()

        startForeground(NOTIFICATION_ID, createNotification("focusly.", "Loading task...", false, false, taskId))

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

                val forceFullScreen = (task.remainingSeconds <= 0L && !isSilenced)
                if (forceFullScreen) {
                    startAlarm()
                }

                updateNotification(task, forceFullScreen)

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
        if (task != null && task.isRunning != isRunning) {
            taskDao.update(task.copy(isRunning = isRunning))
        }
    }

    private fun startAlarm() {
        if (ringtone?.isPlaying == true || mediaPlayer?.isPlaying == true) return

        val prefs = getSharedPreferences("FocuslyPrefs", Context.MODE_PRIVATE)
        val soundEnabled = prefs.getBoolean("sound_enabled", true)
        val vibrateEnabled = prefs.getBoolean("vibrate_enabled", true)
        val customUriStr = prefs.getString("custom_alarm_uri", null)

        try {
            if (soundEnabled) {
                if (customUriStr != null) {
                    try {
                        mediaPlayer = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .build()
                            )
                            setDataSource(applicationContext, Uri.parse(customUriStr))
                            isLooping = true
                            prepare()
                            start()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        playSystemDefaultRingtone()
                    }
                } else {
                    playSystemDefaultRingtone()
                }
            }

            if (vibrateEnabled) {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

                val pattern = longArrayOf(0, 500, 500)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playSystemDefaultRingtone() {
        val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(applicationContext, fallbackUri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone?.isLooping = true
        }
        ringtone?.play()
    }

    private fun stopAlarm() {
        ringtone?.takeIf { it.isPlaying }?.stop()
        mediaPlayer?.takeIf { it.isPlaying }?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(2)
    }

    private fun createNotification(title: String, content: String, isOvertime: Boolean, forceFullScreen: Boolean, taskId: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, TimerService::class.java).apply { action = ACTION_STOP }
        val pendingStopIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingOpenIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", pendingStopIntent)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (forceFullScreen) {
            val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
                putExtra("SHOW_FULLSCREEN_TASK_ID", taskId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(this, 3, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        if (isOvertime && !isSilenced) {
            val silenceIntent = Intent(this, TimerService::class.java).apply { action = ACTION_SILENCE }
            val silencePendingIntent = PendingIntent.getService(this, 2, silenceIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(android.R.drawable.ic_lock_silent_mode_off, "Silence", silencePendingIntent)
        }

        return builder.build()
    }

    private fun updateNotification(task: Task, forceFullScreen: Boolean = false) {
        val isOvertime = task.remainingSeconds < 0
        val titleText = if (isOvertime) "${task.name} (Overtime!)" else task.name
        val timeText = formatSeconds(task.remainingSeconds)
        val notificationManager = getSystemService(NotificationManager::class.java)

        notificationManager.notify(NOTIFICATION_ID, createNotification(titleText, "Time: $timeText", isOvertime, false, task.id))

        if (forceFullScreen) {
            notificationManager.notify(2, createNotification(titleText, "Time: $timeText", isOvertime, true, task.id))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                val directLaunchIntent = Intent(this, MainActivity::class.java).apply {
                    putExtra("SHOW_FULLSCREEN_TASK_ID", task.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(directLaunchIntent)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Timer Service Channel", NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
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
        const val ACTION_SILENCE = "ACTION_SILENCE"
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val CHANNEL_ID = "TimerServiceChannel_V2"
        const val NOTIFICATION_ID = 1
        private const val PREFS_NAME = "TimerServicePrefs"
        private const val KEY_RUNNING_TASK_ID = "running_task_id"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}