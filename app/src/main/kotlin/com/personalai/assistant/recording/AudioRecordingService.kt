package com.personalai.assistant.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.personalai.assistant.MainActivity
import com.personalai.assistant.R
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecordingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var recordJob: Job? = null
    private var timerJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var currentRecordingId: Long = 0
    private var currentTitle: String = ""
    private var currentFile: File? = null
    private var fileOutputStream: FileOutputStream? = null

    private var totalBytesRecorded: Long = 0
    private var recordingStartTimeMs: Long = 0
    private var elapsedDurationMs: Long = 0
    private var pauseStartTimeMs: Long = 0

    private val isRecordingActive = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private lateinit var repository: AppRepository
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository.getInstance(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: generateDefaultTitle()
                startSession(title)
            }
            ACTION_PAUSE -> pauseSession()
            ACTION_RESUME -> resumeSession()
            ACTION_STOP -> stopSession()
        }
        return START_NOT_STICKY
    }

    private fun startSession(title: String) {
        if (isRecordingActive.get()) return // Prevent multiple concurrent recording sessions

        currentTitle = title
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val recordingsDir = File(filesDir, "recordings").apply { if (!exists()) mkdirs() }
        val audioFile = File(recordingsDir, "REC_${timestamp}.wav")
        currentFile = audioFile

        recordingStartTimeMs = System.currentTimeMillis()
        elapsedDurationMs = 0
        totalBytesRecorded = 0

        val minBufferSize = AudioRecord.getMinBufferSize(
            WavWriter.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            _recordingState.value = RecordingState.Error("Unsupported audio hardware settings")
            stopSelf()
            return
        }

        val bufferSize = minBufferSize * 2

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                WavWriter.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _recordingState.value = RecordingState.Error("Microphone initialization failed. Check permissions.")
                stopSelf()
                return
            }

            fileOutputStream = FileOutputStream(audioFile)
            WavWriter.writeWavHeader(fileOutputStream!!, 0)

            isRecordingActive.set(true)
            isPaused.set(false)

            // Start foreground immediately
            val notification = buildNotification(currentTitle, 0, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Persist session metadata immediately into Room
            serviceScope.launch {
                val entity = RecordingEntity(
                    title = currentTitle,
                    filePath = audioFile.absolutePath,
                    startTimestamp = recordingStartTimeMs,
                    durationMs = 0,
                    status = "RECORDING",
                    fileSizeBytes = audioFile.length(),
                    mimeType = "audio/wav",
                    processingStatus = "PENDING"
                )
                currentRecordingId = repository.insertRecording(entity)
                _recordingState.value = RecordingState.Recording(0, currentRecordingId, currentTitle)
            }

            audioRecord?.startRecording()

            // Coroutine to read buffer from AudioRecord and write incrementally
            recordJob = serviceScope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)
                while (isActive && isRecordingActive.get()) {
                    if (isPaused.get()) {
                        delay(100)
                        continue
                    }

                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        try {
                            fileOutputStream?.write(buffer, 0, read)
                            totalBytesRecorded += read
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                _recordingState.value = RecordingState.Error("Storage write failure: ${e.message}")
                                stopSession()
                            }
                            break
                        }
                    }
                }
            }

            // Timer coroutine to update duration state & notification
            timerJob = serviceScope.launch {
                while (isActive && isRecordingActive.get()) {
                    delay(1000)
                    if (!isPaused.get()) {
                        elapsedDurationMs = System.currentTimeMillis() - recordingStartTimeMs
                        _recordingState.value = RecordingState.Recording(
                            durationMs = elapsedDurationMs,
                            recordingId = currentRecordingId,
                            title = currentTitle
                        )
                        notificationManager.notify(
                            NOTIFICATION_ID,
                            buildNotification(currentTitle, elapsedDurationMs, false)
                        )
                    }
                }
            }

        } catch (e: SecurityException) {
            _recordingState.value = RecordingState.Error("Microphone permission denied: ${e.message}")
            cleanupAudioResources()
            stopSelf()
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error("Audio error: ${e.message}")
            cleanupAudioResources()
            stopSelf()
        }
    }

    private fun pauseSession() {
        if (!isRecordingActive.get() || isPaused.get()) return
        isPaused.set(true)
        pauseStartTimeMs = System.currentTimeMillis()
        _recordingState.value = RecordingState.Paused(
            durationMs = elapsedDurationMs,
            recordingId = currentRecordingId,
            title = currentTitle
        )
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(currentTitle, elapsedDurationMs, true)
        )
        serviceScope.launch {
            repository.updateRecording(
                repository.getRecording(currentRecordingId)?.copy(
                    status = "PAUSED",
                    durationMs = elapsedDurationMs,
                    updatedAt = System.currentTimeMillis()
                ) ?: return@launch
            )
        }
    }

    private fun resumeSession() {
        if (!isRecordingActive.get() || !isPaused.get()) return
        // Adjust start time to account for pause duration
        val pausedDuration = System.currentTimeMillis() - pauseStartTimeMs
        recordingStartTimeMs += pausedDuration
        isPaused.set(false)
        _recordingState.value = RecordingState.Recording(
            durationMs = elapsedDurationMs,
            recordingId = currentRecordingId,
            title = currentTitle
        )
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(currentTitle, elapsedDurationMs, false)
        )
        serviceScope.launch {
            repository.updateRecording(
                repository.getRecording(currentRecordingId)?.copy(
                    status = "RECORDING",
                    updatedAt = System.currentTimeMillis()
                ) ?: return@launch
            )
        }
    }

    private fun stopSession() {
        if (!isRecordingActive.get()) {
            stopSelf()
            return
        }

        _recordingState.value = RecordingState.Finalizing
        isRecordingActive.set(false)
        isPaused.set(false)

        recordJob?.cancel()
        timerJob?.cancel()

        serviceScope.launch(Dispatchers.IO) {
            cleanupAudioResources()

            val file = currentFile
            val fileLength = file?.length() ?: 0

            if (file != null && file.exists() && fileLength > 44) {
                // Finalize WAV header sizes
                WavWriter.finalizeWavHeader(file, totalBytesRecorded)
                val finalLength = file.length()
                val finalDuration = (totalBytesRecorded * 1000L) / WavWriter.BYTES_PER_SECOND

                // Update Room metadata
                val existing = repository.getRecording(currentRecordingId)
                if (existing != null) {
                    val updated = existing.copy(
                        endTimestamp = System.currentTimeMillis(),
                        durationMs = finalDuration,
                        fileSizeBytes = finalLength,
                        status = "COMPLETED",
                        processingStatus = "PENDING",
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateRecording(updated)
                }

                withContext(Dispatchers.Main) {
                    _recordingState.value = RecordingState.Completed(
                        recordingId = currentRecordingId,
                        durationMs = finalDuration,
                        filePath = file.absolutePath
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            } else {
                // File missing or empty
                val existing = repository.getRecording(currentRecordingId)
                if (existing != null) {
                    repository.updateRecording(
                        existing.copy(
                            status = "ERROR",
                            errorDetails = "Recorded audio file was empty or missing",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                withContext(Dispatchers.Main) {
                    _recordingState.value = RecordingState.Error("Recording produced an invalid or empty file")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun cleanupAudioResources() {
        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord = null
        }

        try {
            fileOutputStream?.flush()
            fileOutputStream?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            fileOutputStream = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecordingActive.get()) {
            stopSession()
        }
        cleanupAudioResources()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Persistent Notification ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live status and controls for ongoing voice recording"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, durationMs: Long, isPaused: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AudioRecordingService::class.java).apply {
                action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, AudioRecordingService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedDuration = formatDuration(durationMs)
        val stateText = if (isPaused) "Paused ($formattedDuration)" else "Recording ($formattedDuration)"
        val pauseActionText = if (isPaused) "Resume" else "Pause"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(stateText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, pauseActionText, pauseResumeIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", min, sec)
    }

    private fun generateDefaultTitle(): String {
        val dateStr = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())
        return "Voice Note $dateStr"
    }

    companion object {
        const val CHANNEL_ID = "personal_ai_recording_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.personalai.assistant.action.START_RECORDING"
        const val ACTION_PAUSE = "com.personalai.assistant.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.personalai.assistant.action.RESUME_RECORDING"
        const val ACTION_STOP = "com.personalai.assistant.action.STOP_RECORDING"

        const val EXTRA_TITLE = "extra_recording_title"

        private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
        val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

        fun start(context: Context, title: String? = null) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_START
                if (title != null) putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
