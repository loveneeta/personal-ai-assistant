package com.personalai.assistant.wakeword

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.personalai.assistant.MainActivity
import com.personalai.assistant.R
import com.personalai.assistant.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android Foreground Service operating the continuous J.A.R.V.I.S. hands-free
 * wake-word listener with persistent status notification and media controls.
 */
class JarvisAlwaysOnService : Service() {

    companion object {
        const val CHANNEL_ID = "jarvis_always_on_channel"
        const val NOTIFICATION_ID = 4001

        const val PREFS_NAME = "jarvis_voice_prefs"
        const val KEY_ALWAYS_READY = "always_ready_enabled"

        const val ACTION_START = "com.personalai.assistant.action.START_ALWAYS_ON"
        const val ACTION_PAUSE = "com.personalai.assistant.action.PAUSE_ALWAYS_ON"
        const val ACTION_RESUME = "com.personalai.assistant.action.RESUME_ALWAYS_ON"
        const val ACTION_STOP = "com.personalai.assistant.action.STOP_ALWAYS_ON"

        private val _serviceState = MutableStateFlow(JarvisAudioState.IDLE_DISABLED)
        val serviceState: StateFlow<JarvisAudioState> = _serviceState.asStateFlow()

        private val _soundLevel = MutableStateFlow(0f)
        val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

        fun isAlwaysReadyPersisted(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ALWAYS_READY, false)
        }

        fun start(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ALWAYS_READY, true).apply()
            val intent = Intent(context, JarvisAlwaysOnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            val intent = Intent(context, JarvisAlwaysOnService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, JarvisAlwaysOnService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ALWAYS_READY, false).apply()
            val intent = Intent(context, JarvisAlwaysOnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var stateMachine: JarvisVoiceStateMachine
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jarvis::AlwaysOnWakeLock")
        } catch (e: Exception) {
            android.util.Log.e("JarvisAlwaysOnService", "Failed to acquire WakeLock", e)
        }

        val repository = AppRepository.getInstance(this)
        val speakerManager = SpeakerProfileManager.getInstance(this)
        val wakeWordEngine = VoskKeywordSpotter(this)

        stateMachine = JarvisVoiceStateMachine(
            context = this,
            repository = repository,
            speakerManager = speakerManager,
            wakeWordEngine = wakeWordEngine
        )

        // Observe and bridge state machine to static StateFlows and notifications
        serviceScope.launch {
            stateMachine.currentState.collect { state ->
                _serviceState.value = state
                updateNotification(state)
            }
        }

        serviceScope.launch {
            stateMachine.soundLevel.collect { level ->
                _soundLevel.value = level
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> {
                if (wakeLock?.isHeld == false) {
                    try { wakeLock?.acquire(24 * 60 * 60 * 1000L) } catch (e: Exception) {}
                }
                startForegroundWithNotification()
                stateMachine.startHandsFree()
            }
            ACTION_PAUSE -> {
                if (wakeLock?.isHeld == true) {
                    try { wakeLock?.release() } catch (e: Exception) {}
                }
                stateMachine.pauseHandsFree()
            }
            ACTION_RESUME -> {
                if (wakeLock?.isHeld == false) {
                    try { wakeLock?.acquire(24 * 60 * 60 * 1000L) } catch (e: Exception) {}
                }
                stateMachine.resumeHandsFree()
            }
            ACTION_STOP -> {
                if (wakeLock?.isHeld == true) {
                    try { wakeLock?.release() } catch (e: Exception) {}
                }
                stateMachine.stopHandsFree()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        val notification = buildNotification(JarvisAudioState.STARTING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: JarvisAudioState) {
        val notification = buildNotification(state)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(state: JarvisAudioState): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (state == JarvisAudioState.PAUSED) {
            val resumeIntent = PendingIntent.getService(
                this, 1,
                Intent(this, JarvisAlwaysOnService::class.java).apply { action = ACTION_RESUME },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play, "Resume", resumeIntent
            ).build()
        } else {
            val pauseIntent = PendingIntent.getService(
                this, 2,
                Intent(this, JarvisAlwaysOnService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause, "Pause", pauseIntent
            ).build()
        }

        val stopIntent = PendingIntent.getService(
            this, 3,
            Intent(this, JarvisAlwaysOnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent
        ).build()

        val statusText = when (state) {
            JarvisAudioState.WAKE_LISTENING -> "Waiting for 'Jarvis' (Low-Power Mic Active)"
            JarvisAudioState.WAKE_DETECTED -> "Wake Word Detected!"
            JarvisAudioState.VERIFYING_SPEAKER -> "Verifying Primary User Voice..."
            JarvisAudioState.COMMAND_LISTENING -> "Listening to Command (Hinglish/English)..."
            JarvisAudioState.PROCESSING -> "Processing Neural Query..."
            JarvisAudioState.SPEAKING -> "J.A.R.V.I.S. is Speaking..."
            JarvisAudioState.PAUSED -> "Hands-Free Listening Paused"
            JarvisAudioState.PERMISSION_DENIED -> "Microphone Permission Required"
            JarvisAudioState.ERROR -> "Acoustic Engine Error"
            else -> "Initializing Hands-Free Core..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("J.A.R.V.I.S. Always-Ready")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.jarvis_icon_master)
            .setContentIntent(openAppIntent)
            .setOngoing(state != JarvisAudioState.PAUSED)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "J.A.R.V.I.S. Always-Ready Hands-Free",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows hands-free wake word listening status and controls"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

        override fun onDestroy() {
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (e: Exception) {}
        }
        stateMachine.release()
        _serviceState.value = JarvisAudioState.IDLE_DISABLED
        super.onDestroy()
    }
}
