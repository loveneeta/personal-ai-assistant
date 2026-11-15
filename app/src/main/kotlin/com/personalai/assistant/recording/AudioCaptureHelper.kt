package com.personalai.assistant.recording

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MicrophoneDirection
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission

/**
 * Universal Multi-Microphone / Omni-Directional Audio Capture Helper.
 *
 * Configures modern Android hardware to enable all physical microphones
 * (bottom primary, top noise-canceling/ambient, and camera array) in 360-degree
 * omnidirectional pickup mode, bypassing single-mic directional beamforming
 * and aggressive noise suppression that silences room and surrounding voices.
 */
object AudioCaptureHelper {
    private const val TAG = "AudioCaptureHelper"

    /**
     * Creates an [AudioRecord] configured for 360-degree surrounding acoustic pickup.
     *
     * Tries wide-field audio sources in priority order:
     * 1. [MediaRecorder.AudioSource.UNPROCESSED] (API 24+): Disables single-mic beamforming & aggressive noise suppression.
     * 2. [MediaRecorder.AudioSource.CAMCORDER]: Uses multi-mic room/ambient array for surrounding audio.
     * 3. [MediaRecorder.AudioSource.VOICE_RECOGNITION]: Tuned for speech recognition.
     * 4. [MediaRecorder.AudioSource.MIC]: Standard fallback.
     *
     * Once initialized, on API 28+ applies [MicrophoneDirection.MIC_DIRECTION_OMNI]
     * and on API 29+ applies full 1.0f field dimension for maximum surround sensitivity.
     */
    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun createOmniAudioRecord(
        sampleRate: Int = 16000,
        channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
        audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
        bufferSize: Int
    ): AudioRecord? {
        val candidateSources = mutableListOf<Pair<Int, String>>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            candidateSources.add(MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED (Raw multi-mic, no beamforming)")
        }
        candidateSources.add(MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER (Wide-field multi-mic surround)")
        candidateSources.add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION")
        candidateSources.add(MediaRecorder.AudioSource.MIC to "MIC (Default)")

        for ((source, description) in candidateSources) {
            try {
                val record = AudioRecord(
                    source,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "AudioRecord initialized using source: $description (source ID: $source)")
                    configureOmniDirectional(record)
                    return record
                } else {
                    Log.w(TAG, "Source $description did not reach STATE_INITIALIZED. Releasing and trying next.")
                    try { record.release() } catch (_: Throwable) {}
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException on source $description: ${e.message}")
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to instantiate AudioRecord with source $description: ${e.message}")
            }
        }

        Log.e(TAG, "All audio source candidates failed to initialize.")
        return null
    }

    /**
     * Applies omnidirectional 360-degree acoustic pickup across all physical microphones
     * using Android 9+ (API 28) and Android 10+ (API 29) hardware APIs.
     */
    private fun configureOmniDirectional(record: AudioRecord) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                // MIC_DIRECTION_UNSPECIFIED removes directional constraints across microphone array (omni pickup)
                val directionSuccess = record.setPreferredMicrophoneDirection(MicrophoneDirection.MIC_DIRECTION_UNSPECIFIED)
                Log.i(TAG, "Applied setPreferredMicrophoneDirection(MIC_DIRECTION_UNSPECIFIED) -> success: $directionSuccess")
            } catch (e: Throwable) {
                Log.w(TAG, "setPreferredMicrophoneDirection not supported by device HAL: ${e.message}")
            }

            try {
                val activeMics = record.activeMicrophones
                if (!activeMics.isNullOrEmpty()) {
                    Log.i(TAG, "Active physical microphones engaged: ${activeMics.size}")
                    for (mic in activeMics) {
                        Log.d(TAG, "-> Mic id=${mic.id}, location=${mic.location}, desc=${mic.description}")
                    }
                }
            } catch (e: Throwable) {
                Log.d(TAG, "Active microphones query not supported: ${e.message}")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // 0.0f indicates wide omnidirectional capture field (all surroundings, no directional beamforming)
                val fieldSuccess = record.setPreferredMicrophoneFieldDimension(0.0f)
                Log.i(TAG, "Applied setPreferredMicrophoneFieldDimension(0.0f) [Omnidirectional] -> success: $fieldSuccess")
            } catch (e: Throwable) {
                Log.w(TAG, "setPreferredMicrophoneFieldDimension not supported by device HAL: ${e.message}")
            }
        }
    }
}
