package com.personalai.assistant.integrations.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.provider.Settings
import android.util.Log

data class DeviceDiagnostics(
    val batteryPct: Int,
    val isCharging: Boolean,
    val isConnectedToWifi: Boolean,
    val isConnectedToCellular: Boolean,
    val mediaVolumePercent: Int
)

/**
 * Executive Android Device Control Agent.
 *
 * Inspects device health and executes supported hardware controls
 * without root or privileged security bypasses.
 */
class DeviceAgent(private val context: Context) {

    companion object {
        private const val TAG = "DeviceAgent"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * Inspects battery, network, and audio volume status.
     */
    fun getDeviceStatus(): DeviceDiagnostics {
        var batteryPct = -1
        var isCharging = false

        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter)
            if (batteryStatus != null) {
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPct = (level * 100) / scale
                }
                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (_: Exception) {}

        var isWifi = false
        var isCellular = false

        try {
            val activeNetwork = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            }
        } catch (_: Exception) {}

        var volPct = 50
        try {
            val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
            volPct = if (maxVol > 0) (currentVol * 100) / maxVol else 50
        } catch (_: Exception) {}

        return DeviceDiagnostics(
            batteryPct = batteryPct,
            isCharging = isCharging,
            isConnectedToWifi = isWifi,
            isConnectedToCellular = isCellular,
            mediaVolumePercent = volPct
        )
    }

    /**
     * Adjusts media volume level (0 to 100).
     */
    fun setMediaVolume(percent: Int): Boolean {
        return try {
            val am = audioManager ?: return false
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = ((percent.coerceIn(0, 100) * max) / 100)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            Log.i(TAG, "Adjusted media volume to $target / $max ($percent%)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
            false
        }
    }

    /**
     * Toggles flashlight on devices with camera flash.
     */
    fun toggleFlashlight(enable: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull() ?: return false
            cameraManager.setTorchMode(cameraId, enable)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight", e)
            false
        }
    }
}
