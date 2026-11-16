package com.personalai.assistant.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class WearCompanionBridgeTest {

    @Test
    fun messagePaths_areCorrectlyFormatted() {
        assertEquals("/jarvis/voice_command", WearCompanionBridge.PATH_VOICE_COMMAND)
        assertEquals("/jarvis/wake_word", WearCompanionBridge.PATH_WAKE_WORD)
        assertEquals("/jarvis/voice_response", WearCompanionBridge.PATH_VOICE_RESPONSE)
    }
}
