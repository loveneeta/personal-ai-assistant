package com.personalai.assistant.translation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranslationEngineTest {

    private lateinit var context: Context
    private lateinit var translationEngine: TranslationEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        translationEngine = TranslationEngine.getInstance(context)
    }

    @Test
    fun detectLanguage_identifiesEnglishCorrectly() {
        val lang = translationEngine.detectLanguage("What is the weather today?")
        assertEquals("en", lang)
    }

    @Test
    fun detectLanguage_identifiesDevanagariHindi() {
        val lang = translationEngine.detectLanguage("नमस्ते जार्विस")
        assertEquals("hi", lang)
    }

    @Test
    fun detectLanguage_identifiesRomanizedHinglish() {
        val lang1 = translationEngine.detectLanguage("kal mujhe office jana hai")
        assertEquals("hi-Latn", lang1)

        val lang2 = translationEngine.detectLanguage("flashlight chalu kar do")
        assertEquals("hi-Latn", lang2)

        val lang3 = translationEngine.detectLanguage("Sharma ji ko phone lagao")
        assertEquals("hi-Latn", lang3)
    }

    @Test
    fun translateToEnglish_bypassesEnglishDirectly() = runTest {
        val result = translationEngine.translateToEnglish("Turn on the flashlight")
        assertEquals("Turn on the flashlight", result.originalText)
        assertEquals("Turn on the flashlight", result.translatedText)
        assertEquals("en", result.detectedLanguage)
        assertFalse(result.isTranslated)
    }

    @Test
    fun translateToEnglish_translatesHinglishPhrases() = runTest {
        val result = translationEngine.translateToEnglish("kal subah 10 baje meeting schedule karni hai")
        assertEquals("kal subah 10 baje meeting schedule karni hai", result.originalText)
        assertEquals("hi-Latn", result.detectedLanguage)
        assertTrue(result.isTranslated)
        assertTrue(result.translatedText.contains("tomorrow morning"))
        assertTrue(result.translatedText.contains("schedule a meeting"))
    }

    @Test
    fun translateToEnglish_translatesHinglishActionCommands() = runTest {
        val resultTorch = translationEngine.translateToEnglish("flashlight chalu kar do")
        assertEquals("hi-Latn", resultTorch.detectedLanguage)
        assertTrue(resultTorch.translatedText.contains("turn on flashlight"))

        val resultVolume = translationEngine.translateToEnglish("volume mute kar do")
        assertEquals("hi-Latn", resultVolume.detectedLanguage)
        assertTrue(resultVolume.translatedText.contains("mute volume"))
    }

    @Test
    fun translateToEnglish_preservesNamedEntitiesInHinglish() = runTest {
        val result = translationEngine.translateToEnglish("Sharma ji ko call karo")
        assertEquals("hi-Latn", result.detectedLanguage)
        assertTrue(result.translatedText.contains("Sharma ji"))
        assertTrue(result.translatedText.contains("call"))
    }

    @Test
    fun translateToEnglish_devanagariFallbackTranslatesOffline() = runTest {
        val fallback = translationEngine.translateDevanagariFallback("फ्लैशलाइट चालू कर दो")
        assertEquals("turn on flashlight", fallback)

        val fallbackVolume = translationEngine.translateDevanagariFallback("वॉल्यूम म्यूट कर दो")
        assertEquals("mute volume", fallbackVolume)
    }

    @Test
    fun translateToEnglish_handlesEmptyOrBlank() = runTest {
        val emptyResult = translationEngine.translateToEnglish("   ")
        assertEquals("", emptyResult.originalText)
        assertEquals("", emptyResult.translatedText)
        assertFalse(emptyResult.isTranslated)
    }
}
