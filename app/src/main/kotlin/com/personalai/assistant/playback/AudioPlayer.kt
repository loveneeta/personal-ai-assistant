package com.personalai.assistant.playback

import android.media.MediaPlayer
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
import java.io.File

class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPlayingPath = MutableStateFlow<String?>(null)
    val currentPlayingPath: StateFlow<String?> = _currentPlayingPath.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    fun play(filePath: String) {
        if (filePath.startsWith("[")) return
        val file = File(filePath)
        if (!file.exists() || file.length() < 44) return

        if (_currentPlayingPath.value == filePath && mediaPlayer != null) {
            resume()
            return
        }

        stop()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                _durationMs.value = duration
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = 0
                    _currentPlayingPath.value = null
                    stopProgressTracking()
                }
                start()
            }
            _currentPlayingPath.value = filePath
            _isPlaying.value = true
            startProgressTracking()
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                stopProgressTracking()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                _isPlaying.value = true
                startProgressTracking()
            }
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun stop() {
        stopProgressTracking()
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            _currentPlayingPath.value = null
            _isPlaying.value = false
            _currentPositionMs.value = 0
            _durationMs.value = 0
        }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        _currentPositionMs.value = it.currentPosition
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
        stop()
        scope.cancel()
    }
}
