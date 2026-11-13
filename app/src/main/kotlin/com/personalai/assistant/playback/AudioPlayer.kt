package com.personalai.assistant.playback

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Robust audio playback engine backed by the type-safe [PlaybackState] state machine.
 *
 * Guarantees:
 * 1. Synchronized state transitions preventing Android MediaPlayer race conditions and IllegalStateExceptions.
 * 2. Automatic cleanup on completion and error states.
 * 3. Both full [playbackState] observation and legacy backward-compatible flows.
 */
class AudioPlayer {

    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val playerMutex = Mutex()

    // Authoritative Type-Safe State Machine
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    // Backward-compatible mirrored flows for existing UI
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPlayingPath = MutableStateFlow<String?>(null)
    val currentPlayingPath: StateFlow<String?> = _currentPlayingPath.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private fun updateState(newState: PlaybackState) {
        _playbackState.value = newState
        _isPlaying.value = newState.isPlaying
        _currentPlayingPath.value = newState.currentPath
        _currentPositionMs.value = newState.currentPositionMs
        _durationMs.value = newState.durationMs
    }

    fun play(filePath: String) {
        if (filePath.isBlank() || filePath.startsWith("[")) {
            Log.w(TAG, "Invalid audio path passed to play: $filePath")
            return
        }
        val file = File(filePath)
        if (!file.exists() || file.length() < 44) {
            Log.w(TAG, "Audio file missing or empty: $filePath")
            updateState(PlaybackState.Error(filePath, "Audio file is missing or contains no data"))
            return
        }

        scope.launch {
            playerMutex.withLock {
                val current = _playbackState.value

                // If already paused on the same file, resume cleanly
                if (current is PlaybackState.Paused && current.filePath == filePath && mediaPlayer != null) {
                    internalResume()
                    return@withLock
                }

                // If already playing this exact file, do nothing
                if (current is PlaybackState.Playing && current.filePath == filePath) {
                    return@withLock
                }

                // Cleanly release existing player before preparing a new one
                internalStop()

                updateState(PlaybackState.Loading(filePath))

                try {
                    val player = MediaPlayer().apply {
                        setDataSource(filePath)
                        setOnErrorListener { _, what, extra ->
                            Log.e(TAG, "MediaPlayer native error: what=$what, extra=$extra")
                            scope.launch {
                                playerMutex.withLock {
                                    internalStop()
                                    updateState(PlaybackState.Error(filePath, "Playback error ($what, $extra)"))
                                }
                            }
                            true
                        }
                        setOnCompletionListener {
                            scope.launch {
                                playerMutex.withLock {
                                    val dur = duration
                                    stopProgressTracking()
                                    updateState(PlaybackState.Completed(filePath, dur))
                                }
                            }
                        }
                        prepare()
                    }

                    val totalDuration = player.duration
                    player.start()
                    mediaPlayer = player

                    updateState(PlaybackState.Playing(filePath, 0, totalDuration))
                    startProgressTracking(filePath, totalDuration)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start playback for $filePath: ${e.message}", e)
                    internalStop()
                    updateState(PlaybackState.Error(filePath, e.message ?: "Failed to start playback"))
                }
            }
        }
    }

    fun pause() {
        scope.launch {
            playerMutex.withLock {
                val current = _playbackState.value
                if (current !is PlaybackState.Playing) return@withLock

                try {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            player.pause()
                            val pos = player.currentPosition
                            stopProgressTracking()
                            updateState(PlaybackState.Paused(current.filePath, pos, current.durationMs))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error pausing MediaPlayer: ${e.message}", e)
                }
            }
        }
    }

    fun resume() {
        scope.launch {
            playerMutex.withLock {
                internalResume()
            }
        }
    }

    private fun internalResume() {
        val current = _playbackState.value
        if (current !is PlaybackState.Paused) return

        try {
            mediaPlayer?.let { player ->
                player.start()
                updateState(PlaybackState.Playing(current.filePath, current.positionMs, current.durationMs))
                startProgressTracking(current.filePath, current.durationMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming MediaPlayer: ${e.message}", e)
            internalStop()
            updateState(PlaybackState.Error(current.filePath, e.message ?: "Error resuming playback"))
        }
    }

    fun seekTo(positionMs: Int) {
        scope.launch {
            playerMutex.withLock {
                try {
                    mediaPlayer?.seekTo(positionMs)
                    val current = _playbackState.value
                    when (current) {
                        is PlaybackState.Playing -> updateState(current.copy(positionMs = positionMs))
                        is PlaybackState.Paused -> updateState(current.copy(positionMs = positionMs))
                        else -> _currentPositionMs.value = positionMs
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error seeking MediaPlayer: ${e.message}", e)
                }
            }
        }
    }

    fun stop() {
        scope.launch {
            playerMutex.withLock {
                internalStop()
                updateState(PlaybackState.Stopped)
            }
        }
    }

    private fun internalStop() {
        stopProgressTracking()
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }

    private fun startProgressTracking(filePath: String, totalDuration: Int) {
        stopProgressTracking()
        progressJob = scope.launch {
            while (isActive) {
                val current = _playbackState.value
                if (current !is PlaybackState.Playing) break

                val player = mediaPlayer
                if (player != null) {
                    try {
                        if (player.isPlaying) {
                            val pos = player.currentPosition
                            _currentPositionMs.value = pos
                            _playbackState.value = PlaybackState.Playing(filePath, pos, totalDuration)
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        scope.launch {
            playerMutex.withLock {
                internalStop()
                updateState(PlaybackState.Idle)
            }
            scope.cancel()
        }
    }
}
