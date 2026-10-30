package com.personalai.assistant.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.personalai.assistant.MainActivity
import com.personalai.assistant.data.local.entity.RecordingEntity
import com.personalai.assistant.data.repository.AppRepository
import com.personalai.assistant.wakeword.AudioOwner
import com.personalai.assistant.wakeword.JarvisVoiceCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecordingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val sessionMutex = Mutex()

    private var recordJob: Job? = null
    private var writerJob: Job? = null
    private var timerJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentRecordingId: Long = 0
    private var currentTitle: String = ""
    private var currentFile: File? = null
    private var fileOutputStream: FileOutputStream? = null

    // Bounded channel decoupling real-time audio capture from disk writes
    private var audioChannel: Channel<ByteArray>? = null

    @Volatile
    private var totalBytesRecorded: Long = 0

    // Strict time accounting excluding paused periods
    private var recordingStartTimeMs: Long = 0
    private var lastResumeTimestampMs: Long = 0
    private var cumulativeActiveDurationMs: Long = 0
    private var currentElapsedDurationMs: Long = 0

    private val isRecordingActive = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val isCleaningUp = AtomicBoolean(false)

    private lateinit var repository: AppRepository
    private lateinit var notificationManager: NotificationManager
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isReceiverRegistered = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Audio focus permanently lost")
                RecordingHealthMonitor.onInterruption("AUDIOFOCUS_LOSS", "Permanent audio focus loss to another application")
                serviceScope.launch {
                    sessionMutex.withLock { handlePauseSession() }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.w(TAG, "Audio focus transiently lost (call or navigation)")
                RecordingHealthMonitor.onInterruption("AUDIOFOCUS_LOSS_TRANSIENT", "Transient audio focus loss to call/navigation")
                serviceScope.launch {
                    sessionMutex.withLock { handlePauseSession() }
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Audio focus restored")
                RecordingHealthMonitor.recordEvent("AUDIOFOCUS_GAIN", "Audio focus restored")
                serviceScope.launch {
                    sessionMutex.withLock { handleResumeSession() }
                }
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.w(TAG, "Audio becoming noisy: headphones/Bluetooth disconnected")
                RecordingHealthMonitor.recordEvent("BECOMING_NOISY", "Headphones or audio accessory disconnected")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository.getInstance(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        createNotificationChannel()

        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(noisyReceiver, filter)
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register noisy receiver: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val title = intent.getStringExtra(EXTRA_TITLE)

        serviceScope.launch {
            sessionMutex.withLock {
                when (action) {
                    ACTION_START -> handleStartSession(title ?: generateDefaultTitle())
                    ACTION_PAUSE -> handlePauseSession()
                    ACTION_RESUME -> handleResumeSession()
                    ACTION_STOP -> handleStopSession()
                }
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Safely initiates an isolated audio recording session with verified microphone acquisition,
     * synchronous Room database entity persistence, and continuous background WakeLock.
     */
    private suspend fun handleStartSession(title: String) {
        if (isRecordingActive.get()) {
            Log.w(TAG, "Recording session already in progress, ignoring duplicate start request.")
            return
        }

        // 1. Request microphone arbitration via centralized coordinator
        if (!JarvisVoiceCoordinator.requestMicrophone(AudioOwner.WAV_RECORDER)) {
            Log.e(TAG, "Microphone arbitration failed: hardware is occupied by another session.")
            _recordingState.value = RecordingState.Error("Microphone is currently in use by another session.")
            RecordingHealthMonitor.setMicState("ERROR", "Microphone hardware occupied")
            stopSelf()
            return
        }

        // 2. Request audio focus
        requestAudioFocus()

        currentTitle = title
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val recordingsDir = File(filesDir, "recordings").apply { if (!exists()) mkdirs() }
        val audioFile = File(recordingsDir, "REC_${timestamp}.wav")
        currentFile = audioFile

        recordingStartTimeMs = System.currentTimeMillis()
        lastResumeTimestampMs = recordingStartTimeMs
        cumulativeActiveDurationMs = 0L
        currentElapsedDurationMs = 0L
        totalBytesRecorded = 0L
        isCleaningUp.set(false)

        val minBufferSize = AudioRecord.getMinBufferSize(
            WavWriter.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize <= 0 || minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Unsupported audio hardware settings: minBufferSize=$minBufferSize")
            _recordingState.value = RecordingState.Error("Unsupported audio hardware settings")
            RecordingHealthMonitor.setMicState("ERROR", "Unsupported buffer size")
            cleanupAudioResources()
            stopSelf()
            return
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                WavWriter.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed (state != STATE_INITIALIZED).")
                _recordingState.value = RecordingState.Error("Microphone initialization failed. Check permissions.")
                RecordingHealthMonitor.setMicState("ERROR", "AudioRecord initialization failed")
                cleanupAudioResources()
                stopSelf()
                return
            }

            // Acquire Partial WakeLock to guarantee uninterrupted background/screen-locked recording
            acquireWakeLock()

            // Initialize WAV file container with initial 44-byte RIFF header
            val fos = FileOutputStream(audioFile)
            fileOutputStream = fos
            WavWriter.writeWavHeader(fos, 0)

            // Persist valid recording entity into Room BEFORE dependent operations or UI use it
            val entity = RecordingEntity(
                title = currentTitle,
                filePath = audioFile.absolutePath,
                startTimestamp = recordingStartTimeMs,
                durationMs = 0,
                status = "RECORDING",
                fileSizeBytes = 44L,
                mimeType = "audio/wav",
                processingStatus = "PENDING"
            )
            currentRecordingId = withContext(Dispatchers.IO) { repository.insertRecording(entity) }
            Log.i(TAG, "Created persistent recording session #$currentRecordingId at ${audioFile.absolutePath}")

            // Initialize bounded channel between real-time capture and disk writing
            audioChannel = Channel(capacity = 64)

            // Reset and start health monitor
            RecordingHealthMonitor.reset()
            RecordingHealthMonitor.setMicState("CAPTURING")
            RecordingHealthMonitor.recordEvent("SESSION_START", "Recording #$currentRecordingId started: $currentTitle")

            isRecordingActive.set(true)
            isPaused.set(false)

            // Post verified active state to observers
            _recordingState.value = RecordingState.Recording(0, currentRecordingId, currentTitle)

            // Post persistent foreground notification with controls
            val notification = buildNotification(currentTitle, 0, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Start hardware audio capture
            audioRecord?.startRecording()

            // Writer Coroutine: Consumes PCM chunks from bounded channel and writes to disk
            writerJob = serviceScope.launch(Dispatchers.IO) {
                val channel = audioChannel ?: return@launch
                for (chunk in channel) {
                    try {
                        fileOutputStream?.write(chunk)
                        totalBytesRecorded += chunk.size
                    } catch (e: Exception) {
                        Log.e(TAG, "Disk write failure during recording: ${e.message}", e)
                        RecordingHealthMonitor.onInterruption("DISK_WRITE_ERROR", e.message ?: "Storage write failed")
                        withContext(Dispatchers.Main) {
                            _recordingState.value = RecordingState.Error("Storage write failure: ${e.message}")
                            sessionMutex.withLock { handleStopSession() }
                        }
                        break
                    }
                }
            }

            // Capture Coroutine: Reads PCM frames from hardware AudioRecord and dispatches to channel & monitor
            recordJob = serviceScope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)
                var consecutiveErrors = 0

                while (isActive && isRecordingActive.get()) {
                    if (isPaused.get()) {
                        delay(50)
                        continue
                    }

                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        consecutiveErrors = 0
                        val pcmChunk = buffer.copyOf(read)

                        // Update real-time health telemetry
                        RecordingHealthMonitor.processCapturedPcm(
                            pcmData = pcmChunk,
                            bytesRead = read,
                            totalBytesWritten = totalBytesRecorded,
                            fileSizeBytes = (currentFile?.length() ?: 0L),
                            activeDurationMs = currentElapsedDurationMs
                        )

                        // Send to disk writer without blocking real-time capture
                        audioChannel?.send(pcmChunk)

                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord read returned negative code: $read")
                        consecutiveErrors++

                        val errorCategory = when (read) {
                            AudioRecord.ERROR_DEAD_OBJECT -> "HARDWARE_DEAD_OBJECT"
                            AudioRecord.ERROR_INVALID_OPERATION -> "INVALID_OPERATION"
                            AudioRecord.ERROR_BAD_VALUE -> "BAD_VALUE"
                            else -> "HARDWARE_ERROR_$read"
                        }
                        RecordingHealthMonitor.onInterruption("READ_ERROR", "AudioRecord read returned $errorCategory ($read)")

                        // Attempt automatic hardware recovery on DEAD_OBJECT
                        if (read == AudioRecord.ERROR_DEAD_OBJECT) {
                            Log.e(TAG, "AudioRecord DEAD_OBJECT encountered. Attempting hardware recovery...")
                            val recovered = attemptAudioRecordRecovery(bufferSize)
                            RecordingHealthMonitor.onRecoveryAttempt(recovered, if (recovered) "AudioRecord restarted" else "Recovery failed")
                            if (!recovered) {
                                withContext(Dispatchers.Main) {
                                    _recordingState.value = RecordingState.Error("Microphone hardware disconnected (DEAD_OBJECT)")
                                    sessionMutex.withLock { handleStopSession() }
                                }
                                break
                            }
                        } else if (consecutiveErrors >= 5) {
                            Log.e(TAG, "AudioRecord encountered consecutive hardware read failures.")
                            withContext(Dispatchers.Main) {
                                _recordingState.value = RecordingState.Error("Hardware audio read failed (code $read)")
                                sessionMutex.withLock { handleStopSession() }
                            }
                            break
                        }
                        delay(40)
                    } else {
                        delay(10)
                    }
                }
            }

            // Duration timer ticker coroutine (updates notification and UI state every second)
            timerJob = serviceScope.launch {
                while (isActive && isRecordingActive.get()) {
                    delay(1000)
                    if (!isPaused.get()) {
                        val activeDelta = System.currentTimeMillis() - lastResumeTimestampMs
                        currentElapsedDurationMs = cumulativeActiveDurationMs + activeDelta
                        _recordingState.value = RecordingState.Recording(
                            durationMs = currentElapsedDurationMs,
                            recordingId = currentRecordingId,
                            title = currentTitle
                        )
                        notificationManager.notify(
                            NOTIFICATION_ID,
                            buildNotification(currentTitle, currentElapsedDurationMs, false)
                        )
                    }
                }
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission denied: ${e.message}", e)
            _recordingState.value = RecordingState.Error("Microphone permission denied: ${e.message}")
            RecordingHealthMonitor.setMicState("ERROR", "Permission denied: ${e.message}")
            cleanupAudioResources()
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting audio session: ${e.message}", e)
            _recordingState.value = RecordingState.Error("Audio error: ${e.message}")
            RecordingHealthMonitor.setMicState("ERROR", e.message ?: "Audio startup error")
            cleanupAudioResources()
            stopSelf()
        }
    }

    private fun attemptAudioRecordRecovery(bufferSize: Int): Boolean {
        try {
            audioRecord?.apply {
                try { stop() } catch (e: Exception) {}
                try { release() } catch (e: Exception) {}
            }
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                WavWriter.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord recovery attempt failed: ${e.message}")
        }
        return false
    }

    private suspend fun handlePauseSession() {
        if (!isRecordingActive.get() || isPaused.get()) return
        isPaused.set(true)
        RecordingHealthMonitor.setMicState("PAUSED")

        // Lock in elapsed active duration
        val activeDelta = System.currentTimeMillis() - lastResumeTimestampMs
        cumulativeActiveDurationMs += activeDelta
        currentElapsedDurationMs = cumulativeActiveDurationMs

        _recordingState.value = RecordingState.Paused(
            durationMs = currentElapsedDurationMs,
            recordingId = currentRecordingId,
            title = currentTitle
        )
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(currentTitle, currentElapsedDurationMs, true)
        )

        withContext(Dispatchers.IO) {
            val existing = repository.getRecording(currentRecordingId)
            if (existing != null) {
                repository.updateRecording(
                    existing.copy(
                        status = "PAUSED",
                        durationMs = currentElapsedDurationMs,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private suspend fun handleResumeSession() {
        if (!isRecordingActive.get() || !isPaused.get()) return
        requestAudioFocus()
        lastResumeTimestampMs = System.currentTimeMillis()
        isPaused.set(false)
        RecordingHealthMonitor.setMicState("CAPTURING")

        _recordingState.value = RecordingState.Recording(
            durationMs = currentElapsedDurationMs,
            recordingId = currentRecordingId,
            title = currentTitle
        )
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(currentTitle, currentElapsedDurationMs, false)
        )

        withContext(Dispatchers.IO) {
            val existing = repository.getRecording(currentRecordingId)
            if (existing != null) {
                repository.updateRecording(
                    existing.copy(
                        status = "RECORDING",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /**
     * Stops recording capture cleanly:
     * 1. Stops AudioRecord hardware capture first.
     * 2. Closes audio channel and waits for writer job to flush to disk.
     * 3. Flushes and closes fileOutputStream.
     * 4. Finalizes the 44-byte WAV header on disk with exact byte count.
     * 5. Updates Room database record.
     * 6. Emits Completed state and stops foreground.
     */
    private suspend fun handleStopSession() {
        if (!isRecordingActive.get()) {
            stopSelf()
            return
        }

        _recordingState.value = RecordingState.Finalizing
        isRecordingActive.set(false)
        isPaused.set(false)

        // 1. Stop AudioRecord hardware
        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED && recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }

        timerJob?.cancel()

        // 2. Wait for writer coroutine to write out any remaining audio frames from channel
        try {
            audioChannel?.close()
            writerJob?.join()
        } catch (e: Exception) {
            Log.w(TAG, "Error joining writerJob: ${e.message}")
        } finally {
            audioChannel = null
        }

        // 3. Flush & close FileOutputStream
        try {
            fileOutputStream?.flush()
            fileOutputStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing FileOutputStream: ${e.message}")
        } finally {
            fileOutputStream = null
        }

        // 4. Finalize WAV header and calculate exact audio duration
        val file = currentFile
        val finalAudioBytes = totalBytesRecorded
        val fileExistsWithData = file != null && file.exists() && file.length() > 44L && finalAudioBytes > 0L

        if (fileExistsWithData) {
            try {
                WavWriter.finalizeWavHeader(file, finalAudioBytes)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to finalize WAV header on disk: ${e.message}", e)
            }

            val finalDuration = WavWriter.calculateDurationMs(finalAudioBytes)
            val finalLength = file.length()

            withContext(Dispatchers.IO) {
                val existing = repository.getRecording(currentRecordingId)
                if (existing != null) {
                    repository.updateRecording(
                        existing.copy(
                            endTimestamp = System.currentTimeMillis(),
                            durationMs = finalDuration,
                            fileSizeBytes = finalLength,
                            status = "COMPLETED",
                            processingStatus = "PENDING",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            RecordingHealthMonitor.setMicState("IDLE")
            RecordingHealthMonitor.recordEvent("SESSION_FINALIZED", "Session completed: $finalAudioBytes audio bytes.")

            _recordingState.value = RecordingState.Completed(
                recordingId = currentRecordingId,
                durationMs = finalDuration,
                filePath = file.absolutePath
            )
        } else {
            // Recording produced 0 audio bytes
            withContext(Dispatchers.IO) {
                val existing = repository.getRecording(currentRecordingId)
                if (existing != null) {
                    repository.updateRecording(
                        existing.copy(
                            status = "ERROR",
                            errorDetails = "Recording produced an invalid or empty audio file",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            RecordingHealthMonitor.setMicState("ERROR", "Empty audio recording produced")
            _recordingState.value = RecordingState.Error("Recording produced an invalid or empty file")
        }

        abandonAudioFocus()
        cleanupAudioResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request failed: ${e.message}")
            true
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusListener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus abandon failed: ${e.message}")
        } finally {
            audioFocusRequest = null
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "PersonalAI:AudioRecordingServiceWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire(12 * 60 * 60 * 1000L) // 12 hours max timeout
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire partial WakeLock: ${e.message}")
        }
    }

    /**
     * Idempotent cleanup of native audio hardware, wake locks, streams, and mutexes.
     */
    private fun cleanupAudioResources() {
        if (isCleaningUp.getAndSet(true)) return

        JarvisVoiceCoordinator.releaseMicrophone(AudioOwner.WAV_RECORDER)

        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during AudioRecord release: ${e.message}")
        } finally {
            audioRecord = null
        }

        try {
            fileOutputStream?.flush()
            fileOutputStream?.close()
        } catch (e: Exception) {
            // ignore
        } finally {
            fileOutputStream = null
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            wakeLock = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecordingActive.get()) {
            val file = currentFile
            if (file != null && file.exists() && totalBytesRecorded > 0) {
                try {
                    fileOutputStream?.flush()
                    fileOutputStream?.close()
                    WavWriter.finalizeWavHeader(file, totalBytesRecorded)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onDestroy header finalization: ${e.message}")
                }
            }
        }
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(noisyReceiver)
            } catch (e: Exception) {}
            isReceiverRegistered = false
        }
        abandonAudioFocus()
        cleanupAudioResources()
        serviceScope.cancel()
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
        private const val TAG = "AudioRecordingService"
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
