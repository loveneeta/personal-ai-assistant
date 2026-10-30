package com.personalai.assistant.transcription

import com.personalai.assistant.recording.WavWriter
import com.personalai.assistant.transcription.models.AvailableModels
import com.personalai.assistant.transcription.models.ModelManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TranscriptionPipelineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun availableModels_containConfiguredOfflineEngines() {
        val models = AvailableModels.ALL_MODELS
        assertEquals(3, models.size)

        val inEnglish = AvailableModels.INDIAN_ENGLISH
        assertEquals("vosk-model-small-en-in-0.4", inEnglish.id)
        assertEquals("English (India)", inEnglish.language)
        assertTrue(inEnglish.downloadUrl.endsWith(".zip"))

        val hindi = AvailableModels.HINDI
        assertEquals("vosk-model-small-hi-0.22", hindi.id)
        assertEquals("Hindi", hindi.language)
        assertTrue(hindi.downloadUrl.endsWith(".zip"))
    }

    @Test
    fun transcriptSegment_preservesTimeOffsetsAndConfidence() {
        val segment = TranscriptSegment(
            startOffsetMs = 1500L,
            endOffsetMs = 4500L,
            text = "Namaste team, let us review the action items.",
            confidence = 0.98f,
            language = "en"
        )

        assertEquals(1500L, segment.startOffsetMs)
        assertEquals(4500L, segment.endOffsetMs)
        assertEquals(3000L, segment.endOffsetMs - segment.startOffsetMs)
        assertEquals("Namaste team, let us review the action items.", segment.text)
        assertTrue(segment.confidence > 0.9f)
    }

    @Test
    fun sentenceReconstructor_mergesAcousticFragmentsIntoWholeSentence() {
        val tokens = listOf(
            WordToken("we", 1000L, 1200L, 0.95f),
            WordToken("need", 1250L, 1500L, 0.98f),
            WordToken("to", 1550L, 1700L, 0.99f),
            WordToken("finalize", 2000L, 2500L, 0.96f),
            WordToken("the", 2550L, 2700L, 0.98f),
            WordToken("presentation", 2750L, 3400L, 0.97f),
            WordToken("by", 3500L, 3700L, 0.99f),
            WordToken("tomorrow", 3750L, 4200L, 0.95f)
        )

        val segments = SentenceReconstructor.reconstruct(tokens, languageCode = "en", pauseThresholdMs = 950L)
        assertEquals(1, segments.size)
        val fullSentence = segments[0]
        assertEquals("We need to finalize the presentation by tomorrow.", fullSentence.text)
        assertEquals(1000L, fullSentence.startOffsetMs)
        assertEquals(4200L, fullSentence.endOffsetMs)
    }

    @Test
    fun sentenceReconstructor_doesNotBreakOnDanglingConjunction() {
        val tokens = listOf(
            WordToken("i", 1000L, 1200L, 0.99f),
            WordToken("will", 1250L, 1450L, 0.98f),
            WordToken("call", 1500L, 1750L, 0.95f),
            WordToken("rahul", 1800L, 2100L, 0.92f),
            WordToken("and", 2150L, 2350L, 0.99f),
            WordToken("we", 3450L, 3650L, 0.97f),
            WordToken("will", 3700L, 3900L, 0.96f),
            WordToken("discuss", 3950L, 4300L, 0.95f),
            WordToken("the", 4350L, 4500L, 0.98f),
            WordToken("budget", 4550L, 4900L, 0.97f)
        )

        val segments = SentenceReconstructor.reconstruct(tokens, languageCode = "en", pauseThresholdMs = 950L)
        assertEquals(1, segments.size)
        assertEquals("I will call rahul and we will discuss the budget.", segments[0].text)
    }

    @Test
    fun sentenceReconstructor_detectsNaturalSentenceBoundaryOnLongSilence() {
        val tokens = listOf(
            WordToken("welcome", 1000L, 1400L, 0.95f),
            WordToken("everyone", 1450L, 1900L, 0.96f),
            WordToken("to", 1950L, 2100L, 0.99f),
            WordToken("the", 2150L, 2300L, 0.98f),
            WordToken("meeting", 2350L, 2800L, 0.97f),
            // Silence > 950ms
            WordToken("let", 4300L, 4500L, 0.95f),
            WordToken("us", 4550L, 4700L, 0.98f),
            WordToken("review", 4750L, 5100L, 0.96f),
            WordToken("the", 5150L, 5300L, 0.99f),
            WordToken("agenda", 5350L, 5800L, 0.95f)
        )

        val segments = SentenceReconstructor.reconstruct(tokens, languageCode = "en", pauseThresholdMs = 950L)
        assertEquals(2, segments.size)
        assertEquals("Welcome everyone to the meeting.", segments[0].text)
        assertEquals("Let us review the agenda.", segments[1].text)
    }

    @Test
    fun sentenceReconstructor_formatsQuestionsAndHindiPoornViraam() {
        val questionTokens = listOf(
            WordToken("what", 1000L, 1300L, 0.99f),
            WordToken("is", 1350L, 1500L, 0.98f),
            WordToken("the", 1550L, 1700L, 0.99f),
            WordToken("deadline", 1750L, 2200L, 0.95f)
        )
        val qSegments = SentenceReconstructor.reconstruct(questionTokens, languageCode = "en")
        assertEquals("What is the deadline?", qSegments[0].text)

        val hindiTokens = listOf(
            WordToken("aaj", 1000L, 1300L, 0.95f),
            WordToken("hum", 1350L, 1600L, 0.96f),
            WordToken("kaam", 1650L, 1950L, 0.97f),
            WordToken("karenge", 2000L, 2500L, 0.98f)
        )
        val hSegments = SentenceReconstructor.reconstruct(hindiTokens, languageCode = "hi")
        assertEquals("aaj hum kaam karenge।", hSegments[0].text)
    }

    @Test
    fun transcriptionProgress_stateTransitions() {
        val idle: TranscriptionProgress = TranscriptionProgress.Idle
        assertTrue(idle is TranscriptionProgress.Idle)

        val processing: TranscriptionProgress = TranscriptionProgress.Processing(1L, 0.45f, "Transcribing 45%...")
        assertEquals(1L, (processing as TranscriptionProgress.Processing).recordingId)
        assertEquals(0.45f, processing.progress, 0.001f)

        val completed: TranscriptionProgress = TranscriptionProgress.Completed(1L, 5)
        assertEquals(5, (completed as TranscriptionProgress.Completed).segmentCount)

        val failed: TranscriptionProgress = TranscriptionProgress.Failed(1L, "Model missing")
        assertEquals("Model missing", (failed as TranscriptionProgress.Failed).reason)
    }

    // --- Regression Tests for Batch 3 Requirements ---

    @Test(expected = IllegalArgumentException::class)
    fun transcriptionEngine_throwsOnMissingAudioFile() {
        val engine = VoskTranscriptionEngine()
        val nonExistentFile = File(tempFolder.root, "does_not_exist.wav")
        val modelDir = tempFolder.newFolder("model")
        engine.transcribe(nonExistentFile, modelDir)
    }

    @Test
    fun transcriptionEngine_throwsOnCorruptWavFile() {
        val engine = VoskTranscriptionEngine()
        val corruptFile = tempFolder.newFile("corrupt.wav")
        corruptFile.writeText("Not a valid RIFF audio file")
        val modelDir = tempFolder.newFolder("model_test")

        try {
            engine.transcribe(corruptFile, modelDir)
            fail("Expected IllegalArgumentException on corrupt WAV file")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid WAV") == true || e.message?.contains("missing or contains no data") == true)
        }
    }

    @Test
    fun transcriptionEngine_throwsOnMissingModelDirectory() {
        val engine = VoskTranscriptionEngine()
        val validWav = tempFolder.newFile("valid.wav")
        FileOutputStream(validWav).use { fos ->
            WavWriter.writeWavHeader(fos, 16000L)
            fos.write(ByteArray(16000))
        }
        val missingModelDir = File(tempFolder.root, "missing_model_dir")

        try {
            engine.transcribe(validWav, missingModelDir)
            fail("Expected IllegalArgumentException on missing model directory")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("model directory does not exist") == true)
        }
    }

    @Test
    fun modelManager_rejectsZipSlipPathTraversal() {
        // Construct a malicious ZIP file containing an entry attempting path traversal outside target
        val maliciousZip = tempFolder.newFile("malicious_model.zip")
        val extractionTargetDir = tempFolder.newFolder("safe_models")

        ZipOutputStream(FileOutputStream(maliciousZip)).use { zos ->
            zos.putNextEntry(ZipEntry("../escaped_payload.txt"))
            zos.write("malicious payload bytes".toByteArray())
            zos.closeEntry()
        }

        // Test unzip logic directly
        try {
            ModelManager.unzip(maliciousZip, extractionTargetDir)
            fail("Expected SecurityException on Zip Slip entry path traversal")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Zip Slip") == true)
        }
    }
}
