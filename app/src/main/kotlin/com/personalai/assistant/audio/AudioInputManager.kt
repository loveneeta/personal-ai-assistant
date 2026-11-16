package com.personalai.assistant.audio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioInputDeviceType {
    BLUETOOTH_SCO,
    USB_HEADSET,
    WIRED_HEADSET,
    BUILTIN_MIC
}

data class AudioInputDevice(
    val id: Int,
    val name: String,
    val type: AudioInputDeviceType,
    val deviceInfo: AudioDeviceInfo? = null
)

/**
 * Enterprise Audio Input Manager for JARVIS.
 * Handles automatic priority-based routing across:
 * 1. Bluetooth Headsets (SCO / BLE Audio)
 * 2. USB / Wired Headsets
 * 3. Built-in Multi-Microphone Array
 *
 * Provides instant fallback and recovery upon headset disconnection.
 */
class AudioInputManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioInputManager"

        @Volatile
        private var INSTANCE: AudioInputManager? = null

        fun getInstance(context: Context): AudioInputManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioInputManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _currentDevice = MutableStateFlow(
        AudioInputDevice(0, "Built-in Microphone", AudioInputDeviceType.BUILTIN_MIC)
    )
    val currentDevice: StateFlow<AudioInputDevice> = _currentDevice.asStateFlow()

    private var deviceChangeListener: ((AudioInputDevice) -> Unit)? = null
    private var isMonitoring = false
    private var isScoStarted = false

    private val audioDeviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                Log.d(TAG, "Audio devices added: ${addedDevices?.size ?: 0}")
                reevaluateInputDevice()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                Log.d(TAG, "Audio devices removed: ${removedDevices?.size ?: 0}")
                reevaluateInputDevice()
            }
        }
    } else null

    private val audioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(TAG, "Received audio event: $action")
            when (action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    Log.w(TAG, "Audio becoming noisy - peripheral disconnected. Recovering to built-in mic.")
                    recoverToBuiltInMic()
                }
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
                    Log.d(TAG, "SCO audio state updated: $state")
                    if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                        reevaluateInputDevice()
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    reevaluateInputDevice()
                }
            }
        }
    }

    @Synchronized
    fun startMonitoring(listener: (AudioInputDevice) -> Unit) {
        deviceChangeListener = listener
        if (isMonitoring) return
        isMonitoring = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            try {
                audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register AudioDeviceCallback: ${e.message}")
            }
        }

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        try {
            context.registerReceiver(audioReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register audio broadcast receiver: ${e.message}")
        }

        routeToOptimalDevice()
    }

    @Synchronized
    fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            try {
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister AudioDeviceCallback: ${e.message}")
            }
        }

        try {
            context.unregisterReceiver(audioReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }

        resetRouting()
        deviceChangeListener = null
    }

    /**
     * Identifies and connects to the optimal audio input device.
     * Priority:
     * 1. Bluetooth SCO / BLE Headset
     * 2. USB Headset
     * 3. Wired Headset
     * 4. Built-in Microphone
     */
    @Synchronized
    fun routeToOptimalDevice(): AudioInputDevice {
        // Android 12+ (API 31+): setCommunicationDevice
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val availableDevices = audioManager.availableCommunicationDevices
            val btDevice = availableDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            if (btDevice != null) {
                val success = audioManager.setCommunicationDevice(btDevice)
                Log.i(TAG, "Routed communication to Bluetooth device: ${btDevice.productName} (success: $success)")
                val dev = AudioInputDevice(btDevice.id, btDevice.productName.toString(), AudioInputDeviceType.BLUETOOTH_SCO, btDevice)
                updateActiveDevice(dev)
                return dev
            }

            val usbOrWired = availableDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            }
            if (usbOrWired != null) {
                val success = audioManager.setCommunicationDevice(usbOrWired)
                Log.i(TAG, "Routed communication to wired/USB device: ${usbOrWired.productName} (success: $success)")
                val type = if (usbOrWired.type == AudioDeviceInfo.TYPE_USB_HEADSET) AudioInputDeviceType.USB_HEADSET else AudioInputDeviceType.WIRED_HEADSET
                val dev = AudioInputDevice(usbOrWired.id, usbOrWired.productName.toString(), type, usbOrWired)
                updateActiveDevice(dev)
                return dev
            }

            audioManager.clearCommunicationDevice()
        } else {
            // Android 11 and lower: Bluetooth SCO legacy routing
            if (isBluetoothHeadsetConnected()) {
                try {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    isScoStarted = true
                    Log.i(TAG, "Legacy Bluetooth SCO started successfully")
                    val dev = AudioInputDevice(1, "Bluetooth Headset", AudioInputDeviceType.BLUETOOTH_SCO)
                    updateActiveDevice(dev)
                    return dev
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start Bluetooth SCO: ${e.message}")
                }
            }
        }

        // Default: Built-in omnidirectional microphone
        val defaultDevice = AudioInputDevice(0, "Built-in Microphone", AudioInputDeviceType.BUILTIN_MIC)
        updateActiveDevice(defaultDevice)
        return defaultDevice
    }

    /**
     * Gracefully falls back to the built-in microphone upon peripheral disconnection.
     */
    @Synchronized
    fun recoverToBuiltInMic(): AudioInputDevice {
        Log.i(TAG, "Recovering audio input to built-in microphone")
        resetRouting()
        val defaultDevice = AudioInputDevice(0, "Built-in Microphone", AudioInputDeviceType.BUILTIN_MIC)
        updateActiveDevice(defaultDevice)
        return defaultDevice
    }

    private fun reevaluateInputDevice() {
        val newDevice = routeToOptimalDevice()
        Log.d(TAG, "Re-evaluated active input device: ${newDevice.name} (${newDevice.type})")
    }

    private fun updateActiveDevice(device: AudioInputDevice) {
        val changed = _currentDevice.value != device
        _currentDevice.value = device
        if (changed) {
            deviceChangeListener?.invoke(device)
        }
    }

    private fun resetRouting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice()
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing communication device: ${e.message}")
            }
        }
        if (isScoStarted) {
            try {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
                audioManager.mode = AudioManager.MODE_NORMAL
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping Bluetooth SCO: ${e.message}")
            }
            isScoStarted = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun isBluetoothHeadsetConnected(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            return devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        }
        return false
    }
}
