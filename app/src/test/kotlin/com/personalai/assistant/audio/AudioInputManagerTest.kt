package com.personalai.assistant.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioInputManagerTest {

    private lateinit var context: Context
    private lateinit var audioInputManager: AudioInputManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        audioInputManager = AudioInputManager.getInstance(context)
    }

    @Test
    fun defaultDevice_routesToBuiltInMic() {
        val device = audioInputManager.routeToOptimalDevice()
        assertNotNull(device)
        assertEquals(AudioInputDeviceType.BUILTIN_MIC, device.type)
        assertEquals("Built-in Microphone", device.name)
    }

    @Test
    fun recoverToBuiltInMic_resetsRoutingCleanly() {
        val recovered = audioInputManager.recoverToBuiltInMic()
        assertNotNull(recovered)
        assertEquals(AudioInputDeviceType.BUILTIN_MIC, recovered.type)
        assertEquals("Built-in Microphone", recovered.name)
    }

    @Test
    fun monitoringLifecycle_startsAndStopsWithoutCrashing() {
        var observedDevice: AudioInputDevice? = null
        audioInputManager.startMonitoring { dev ->
            observedDevice = dev
        }
        audioInputManager.stopMonitoring()
    }
}
