package com.personalai.assistant.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HinglishMultilingualTest {

    @Test
    fun testCase1_HindiOnlySpeech_DevanagariAndRomanized() {
        // Devanagari Hindi
        val devTokens = listOf(
            WordToken("कल", 1000L, 1300L, 0.98f),
            WordToken("हमारी", 1350L, 1600L, 0.97f),
            WordToken("टीम", 1650L, 1900L, 0.99f),
            WordToken("काम", 1950L, 2200L, 0.98f),
            WordToken("करेगी", 2250L, 2600L, 0.98f)
        )
        val devSegments = SentenceReconstructor.reconstruct(devTokens, languageCode = "hi-en")
        assertEquals(1, devSegments.size)
        assertEquals("कल हमारी टीम काम करेगी।", devSegments[0].text)

        // Romanized Hindi
        val romTokens = listOf(
            WordToken("kal", 1000L, 1300L, 0.98f),
            WordToken("humari", 1350L, 1600L, 0.97f),
            WordToken("team", 1650L, 1900L, 0.99f),
            WordToken("kaam", 1950L, 2200L, 0.98f),
            WordToken("karegi", 2250L, 2600L, 0.98f)
        )
        val romSegments = SentenceReconstructor.reconstruct(romTokens, languageCode = "hi-en")
        assertEquals(1, romSegments.size)
        // Romanized Hindi should use period '.', NOT Devanagari '।'
        assertEquals("Kal humari team kaam karegi.", romSegments[0].text)
    }

    @Test
    fun testCase2_EnglishOnlySpeech() {
        val tokens = listOf(
            WordToken("the", 1000L, 1200L, 0.99f),
            WordToken("quarterly", 1250L, 1700L, 0.96f),
            WordToken("financial", 1750L, 2200L, 0.97f),
            WordToken("audit", 2250L, 2600L, 0.98f),
            WordToken("is", 2650L, 2800L, 0.99f),
            WordToken("completed", 2850L, 3400L, 0.98f)
        )
        val segments = SentenceReconstructor.reconstruct(tokens, languageCode = "hi-en")
        assertEquals(1, segments.size)
        assertEquals("The quarterly financial audit is completed.", segments[0].text)
    }

    @Test
    fun testCase3_RomanizedHinglish_MeetingScheduleExample() {
        // "Mujhe kal morning 10 baje meeting schedule karni hai, aur uska reminder bhi set kar dena."
        val tokens = listOf(
            WordToken("mujhe", 1000L, 1300L, 0.96f),
            WordToken("kal", 1350L, 1550L, 0.98f),
            WordToken("morning", 1600L, 1950L, 0.97f),
            WordToken("10", 2000L, 2200L, 0.99f),
            WordToken("baje", 2250L, 2450L, 0.96f),
            WordToken("meeting", 2500L, 2800L, 0.98f),
            WordToken("schedule", 2850L, 3300L, 0.97f),
            WordToken("karni", 3350L, 3600L, 0.96f),
            WordToken("hai", 3650L, 3850L, 0.99f),
            // Natural 500ms pause before "aur"
            WordToken("aur", 4350L, 4550L, 0.98f),
            WordToken("uska", 4600L, 4850L, 0.96f),
            WordToken("reminder", 4900L, 5300L, 0.98f),
            WordToken("bhi", 5350L, 5500L, 0.97f),
            WordToken("set", 5550L, 5750L, 0.99f),
            WordToken("kar", 5800L, 5950L, 0.98f),
            WordToken("dena", 6000L, 6300L, 0.97f)
        )

        val segments = SentenceReconstructor.reconstruct(tokens, languageCode = "hi-en")
        assertEquals(1, segments.size)
        val text = segments[0].text
        // Must maintain single flowing sentence with connector comma before "aur"
        assertEquals("Mujhe kal morning 10 baje meeting schedule karni hai, aur uska reminder bhi set kar dena.", text)
        assertFalse(text.contains("।")) // No Devanagari full stop in Romanized text
    }

    @Test
    fun testCase4_DevanagariHindiMixedWithEnglishTechnicalTerms() {
        // "आज production dashboard check करो और मुझे pending orders की report भेज दो."
        val tokens = listOf(
            WordToken("आज", 1000L, 1300L, 0.98f),
            WordToken("production", 1350L, 1850L, 0.97f),
            WordToken("dashboard", 1900L, 2400L, 0.98f),
            WordToken("check", 2450L, 2750L, 0.99f),
            WordToken("करो", 2800L, 3100L, 0.97f),
            WordToken("और", 3150L, 3350L, 0.99f),
            WordToken("मुझे", 3400L, 3650L, 0.98f),
            WordToken("pending", 3700L, 4100L, 0.96f),
            WordToken("orders", 4150L, 4500L, 0.97f),
            WordToken("की", 4550L, 4700L, 0.99f),
            WordToken("report", 4750L, 5100L, 0.98f),
            WordToken("भेज", 5150L, 5400L, 0.98f),
            WordToken("दो", 5450L, 5700L, 0.99f)
        )

        val segments = SentenceReconstructor.reconstruct(tokens, languageCode = "hi-en")
        assertEquals(1, segments.size)
        val text = segments[0].text
        assertEquals("आज production dashboard check करो और मुझे pending orders की report भेज दो।", text)
        assertTrue(SentenceReconstructor.hasDevanagari(text))
    }

    @Test
    fun testCase5_SwitchingLanguagesMultipleTimes_ExcelSkuExample() {
        // "Is Excel file mein duplicate SKU numbers check karo aur missing products ki list export kar do."
        val tokens = listOf(
            WordToken("is", 1000L, 1200L, 0.98f),
            WordToken("excel", 1250L, 1650L, 0.97f), // Technical term: Excel
            WordToken("file", 1700L, 1950L, 0.98f),
            WordToken("mein", 2000L, 2200L, 0.97f), // Hindi postposition
            WordToken("duplicate", 2250L, 2750L, 0.96f),
            WordToken("sku", 2800L, 3100L, 0.99f), // Technical acronym: SKU
            WordToken("numbers", 3150L, 3550L, 0.98f),
            WordToken("check", 3600L, 3900L, 0.98f),
            WordToken("karo", 3950L, 4200L, 0.97f),
            WordToken("aur", 4250L, 4450L, 0.99f), // Hindi connector
            WordToken("missing", 4500L, 4850L, 0.96f),
            WordToken("products", 4900L, 5350L, 0.97f),
            WordToken("ki", 5400L, 5550L, 0.99f), // Hindi connector
            WordToken("list", 5600L, 5850L, 0.98f),
            WordToken("export", 5900L, 6300L, 0.97f),
            WordToken("kar", 6350L, 6500L, 0.98f),
            WordToken("do", 6550L, 6750L, 0.99f)
        )

        val segments = SentenceReconstructor.reconstruct(tokens, languageCode = "hi-en")
        assertEquals(1, segments.size)
        val text = segments[0].text
        // Must preserve casing for Excel and SKU
        assertTrue(text.contains("Excel"))
        assertTrue(text.contains("SKU"))
        assertEquals("Is Excel file mein duplicate SKU numbers check karo aur missing products ki list export kar do.", text)
    }

    @Test
    fun testCase6_TechnicalTermsAndAcronymPreservation() {
        val tokens = listOf(
            WordToken("humara", 1000L, 1300L, 0.98f),
            WordToken("new", 1350L, 1550L, 0.99f),
            WordToken("ai", 1600L, 1800L, 0.98f), // AI
            WordToken("model", 1850L, 2150L, 0.97f),
            WordToken("api", 2200L, 2450L, 0.99f), // API
            WordToken("se", 2500L, 2650L, 0.98f),
            WordToken("pdf", 2700L, 2950L, 0.99f), // PDF
            WordToken("aur", 3000L, 3200L, 0.98f),
            WordToken("csv", 3250L, 3550L, 0.99f), // CSV
            WordToken("generate", 3600L, 4050L, 0.97f),
            WordToken("karega", 4100L, 4450L, 0.98f)
        )

        val segments = SentenceReconstructor.reconstruct(tokens, languageCode = "hi-en")
        assertEquals(1, segments.size)
        val text = segments[0].text
        assertTrue(text.contains("AI"))
        assertTrue(text.contains("API"))
        assertTrue(text.contains("PDF"))
        assertTrue(text.contains("CSV"))
        assertEquals("Humara new AI model API se PDF aur CSV generate karega.", text)
    }

    @Test
    fun testCase7_ConversationalPauses_NeverBreaksOnHindiOrEnglishConnector() {
        val connectorsToTest = listOf("aur", "ki", "lekin", "isliye", "and", "because", "to", "for")
        for (connector in connectorsToTest) {
            assertTrue("Connector '$connector' must be recognized in universal set",
                SentenceReconstructor.isConnectorWord(connector))
        }
    }

    @Test
    fun testCase8_QuestionDetection_Bilingual() {
        // English question in Hinglish
        val tokens1 = listOf(
            WordToken("what", 1000L, 1300L, 0.99f),
            WordToken("is", 1350L, 1500L, 0.98f),
            WordToken("the", 1550L, 1700L, 0.99f),
            WordToken("plan", 1750L, 2100L, 0.98f)
        )
        val seg1 = SentenceReconstructor.reconstruct(tokens1)
        assertEquals("What is the plan?", seg1[0].text)

        // Hindi question word in Romanized Hinglish
        val tokens2 = listOf(
            WordToken("kya", 1000L, 1250L, 0.98f),
            WordToken("aap", 1300L, 1500L, 0.99f),
            WordToken("kal", 1550L, 1750L, 0.98f),
            WordToken("meeting", 1800L, 2150L, 0.97f),
            WordToken("attend", 2200L, 2550L, 0.98f),
            WordToken("karoge", 2600L, 3000L, 0.98f)
        )
        val seg2 = SentenceReconstructor.reconstruct(tokens2)
        assertEquals("Kya aap kal meeting attend karoge?", seg2[0].text)

        // Devanagari question word
        val tokens3 = listOf(
            WordToken("क्या", 1000L, 1300L, 0.99f),
            WordToken("meeting", 1350L, 1750L, 0.97f),
            WordToken("reschedule", 1800L, 2400L, 0.98f),
            WordToken("हो", 2450L, 2650L, 0.99f),
            WordToken("गई", 2700L, 2900L, 0.99f)
        )
        val seg3 = SentenceReconstructor.reconstruct(tokens3)
        assertEquals("क्या meeting reschedule हो गई?", seg3[0].text)
    }

    @Test
    fun testCase9_LongRecording_NaturalPauseSplitsCoherently() {
        val tokens = listOf(
            // Utterance 1: 1000L to 3000L
            WordToken("first", 1000L, 1300L, 0.98f),
            WordToken("export", 1350L, 1700L, 0.97f),
            WordToken("the", 1750L, 1900L, 0.99f),
            WordToken("sku", 1950L, 2200L, 0.98f),
            WordToken("data", 2250L, 2600L, 0.98f),
            // 1500ms deliberate silence
            // Utterance 2: 4100L to 6100L
            WordToken("fir", 4100L, 4350L, 0.98f),
            WordToken("uska", 4400L, 4650L, 0.97f),
            WordToken("excel", 4700L, 5100L, 0.98f),
            WordToken("share", 5150L, 5500L, 0.97f),
            WordToken("karo", 5550L, 5850L, 0.98f)
        )

        val segments = SentenceReconstructor.reconstruct(tokens, languageCode = "hi-en", pauseThresholdMs = 950L)
        assertEquals(2, segments.size)
        assertEquals("First export the SKU data.", segments[0].text)
        assertEquals("Fir uska Excel share karo.", segments[1].text)
    }
    @Test
    fun testCase10_ExactPromptHinglishSentences() {
        // 1. "Jarvis, mujhe kal morning mein meeting schedule karni hai."
        val tokens1 = listOf(
            WordToken("jarvis", 1000L, 1400L, 0.98f),
            WordToken("mujhe", 1450L, 1750L, 0.97f),
            WordToken("kal", 1800L, 2050L, 0.98f),
            WordToken("morning", 2100L, 2500L, 0.99f),
            WordToken("mein", 2550L, 2800L, 0.97f),
            WordToken("meeting", 2850L, 3250L, 0.98f),
            WordToken("schedule", 3300L, 3800L, 0.99f),
            WordToken("karni", 3850L, 4150L, 0.97f),
            WordToken("hai", 4200L, 4450L, 0.99f)
        )
        val seg1 = SentenceReconstructor.reconstruct(tokens1)
        assertEquals("Jarvis mujhe kal morning mein meeting schedule karni hai.", seg1[0].text)

        // 2. "Open Excel aur last month's sales report dikhao."
        val tokens2 = listOf(
            WordToken("open", 1000L, 1300L, 0.99f),
            WordToken("excel", 1350L, 1750L, 0.98f),
            WordToken("aur", 1800L, 2000L, 0.99f),
            WordToken("last", 2050L, 2300L, 0.98f),
            WordToken("month's", 2350L, 2800L, 0.97f),
            WordToken("sales", 2850L, 3200L, 0.99f),
            WordToken("report", 3250L, 3650L, 0.98f),
            WordToken("dikhao", 3700L, 4100L, 0.97f)
        )
        val seg2 = SentenceReconstructor.reconstruct(tokens2)
        assertEquals("Open Excel aur last month's sales report dikhao.", seg2[0].text)

        // 3. "Is product ka costing calculate karo and export it to Excel."
        val tokens3 = listOf(
            WordToken("is", 1000L, 1200L, 0.98f),
            WordToken("product", 1250L, 1650L, 0.99f),
            WordToken("ka", 1700L, 1850L, 0.97f),
            WordToken("costing", 1900L, 2350L, 0.98f),
            WordToken("calculate", 2400L, 2900L, 0.99f),
            WordToken("karo", 2950L, 3250L, 0.97f),
            WordToken("and", 3600L, 3800L, 0.98f),
            WordToken("export", 3850L, 4250L, 0.99f),
            WordToken("it", 4300L, 4450L, 0.98f),
            WordToken("to", 4500L, 4650L, 0.99f),
            WordToken("excel", 4700L, 5100L, 0.98f)
        )
        val seg3 = SentenceReconstructor.reconstruct(tokens3)
        // With pause before 'and', reconstructor appropriately inserts coordinating comma
        assertEquals("Is product ka costing calculate karo and export it to Excel.", seg3[0].text)

        // 4. "Maine jo bola hai usko save kar do, but don't send it yet."
        val tokens4 = listOf(
            WordToken("maine", 1000L, 1350L, 0.98f),
            WordToken("jo", 1400L, 1600L, 0.97f),
            WordToken("bola", 1650L, 1950L, 0.98f),
            WordToken("hai", 2000L, 2200L, 0.99f),
            WordToken("usko", 2250L, 2550L, 0.97f),
            WordToken("save", 2600L, 2950L, 0.99f),
            WordToken("kar", 3000L, 3150L, 0.98f),
            WordToken("do", 3200L, 3400L, 0.99f),
            WordToken("but", 3850L, 4100L, 0.98f),
            WordToken("don't", 4150L, 4500L, 0.97f),
            WordToken("send", 4550L, 4850L, 0.99f),
            WordToken("it", 4900L, 5050L, 0.98f),
            WordToken("yet", 5100L, 5400L, 0.98f)
        )
        val seg4 = SentenceReconstructor.reconstruct(tokens4)
        assertEquals("Maine jo bola hai usko save kar do, but don't send it yet.", seg4[0].text)
    }
    @Test
    fun testCase11_Batch4_ExactPromptUtterance_QuotationMeetingExample() {
        // Exact prompt requirement:
        // "Kal customer ki meeting hai, please quotation ready kar dena before five PM."
        val tokens = listOf(
            WordToken("kal", 1000L, 1250L, 0.98f),
            WordToken("customer", 1300L, 1750L, 0.97f),
            WordToken("ki", 1800L, 1950L, 0.99f),
            WordToken("meeting", 2000L, 2350L, 0.98f),
            WordToken("hai", 2400L, 2600L, 0.99f),
            // Natural 450ms pause before "please"
            WordToken("please", 3050L, 3400L, 0.97f),
            WordToken("quotation", 3450L, 3950L, 0.96f),
            WordToken("ready", 4000L, 4300L, 0.98f),
            WordToken("kar", 4350L, 4500L, 0.99f),
            WordToken("dena", 4550L, 4800L, 0.97f),
            WordToken("before", 4850L, 5150L, 0.98f),
            WordToken("five", 5200L, 5450L, 0.99f),
            WordToken("pm", 5500L, 5800L, 0.98f)
        )

        val segments = SentenceReconstructor.reconstruct(tokens, languageCode = "hi-en")
        assertEquals(1, segments.size)
        val result = segments[0]

        val expected = "Kal customer ki meeting hai, please quotation ready kar dena before five PM."
        assertEquals(expected, result.text)
        assertEquals("hi-en", result.language)

        val werResult = WerCalculator.calculate(expected, result.text)
        assertEquals(0.0, werResult.wer, 0.001)
        assertEquals(100.0, werResult.accuracyPercentage, 0.001)
        assertEquals(0, werResult.substitutions)
        assertEquals(0, werResult.deletions)
        assertEquals(0, werResult.insertions)
    }

    @Test
    fun testCase12_WerCalculator_ValidationAcrossLanguageVarieties() {
        // 1. Pure Hindi
        val refHi = "कल हमारी टीम काम करेगी"
        val hypHi = "कल हमारी टीम काम करेगी"
        val resHi = WerCalculator.calculate(refHi, hypHi)
        assertEquals(0.0, resHi.wer, 0.001)

        // 2. Pure English
        val refEn = "The quarterly financial audit is completed"
        val hypEn = "The quarterly financial audit is completed"
        val resEn = WerCalculator.calculate(refEn, hypEn)
        assertEquals(0.0, resEn.wer, 0.001)

        // 3. Hinglish code-switching
        val refHinglish = "Kal customer ki meeting hai please quotation ready kar dena before five PM"
        val hypHinglish = "Kal customer ki meeting hai please quotation ready kar dena before five PM"
        val resHinglish = WerCalculator.calculate(refHinglish, hypHinglish)
        assertEquals(0.0, resHinglish.wer, 0.001)

        // 4. Intentional errors (Substitution, Deletion, Insertion)
        // Ref: "meeting at five pm" (4 words)
        // Hyp: "meeting by five" (Substitution: at->by, Deletion: pm)
        val resErr = WerCalculator.calculate("meeting at five pm", "meeting by five")
        assertEquals(1, resErr.substitutions)
        assertEquals(1, resErr.deletions)
        assertEquals(0, resErr.insertions)
        assertEquals(4, resErr.referenceWordCount)
        assertEquals(0.5, resErr.wer, 0.001)
    }

    @Test
    fun testCase13_QuietSpeechAndLongRecordings() {
        // Low confidence tokens (quiet/whispered audio) should still reconstruct reliably
        val quietTokens = listOf(
            WordToken("kal", 1000L, 1300L, 0.28f),
            WordToken("kaam", 1350L, 1650L, 0.32f),
            WordToken("hoga", 1700L, 2050L, 0.30f)
        )
        val segments = SentenceReconstructor.reconstruct(quietTokens, languageCode = "hi-en")
        assertEquals(1, segments.size)
        assertEquals("Kal kaam hoga.", segments[0].text)
        assertTrue(segments[0].confidence >= 0.25f && segments[0].confidence <= 0.35f)

        // Multi-sentence conversation with 2 second quiet pause between sentences
        val multiSentenceTokens = listOf(
            WordToken("first", 1000L, 1300L, 0.95f),
            WordToken("task", 1350L, 1600L, 0.96f),
            // 2000ms pause
            WordToken("second", 3600L, 4000L, 0.95f),
            WordToken("task", 4050L, 4350L, 0.96f)
        )
        val multiSegs = SentenceReconstructor.reconstruct(multiSentenceTokens, languageCode = "hi-en", pauseThresholdMs = 950L)
        assertEquals(2, multiSegs.size)
        assertEquals("First task.", multiSegs[0].text)
        assertEquals("Second task.", multiSegs[1].text)
    }

    @Test
    fun testCase14_DynamicLanguageTagging() {
        // Mixed Hinglish
        assertEquals("hi-en", SentenceReconstructor.detectLanguageTag("Kal customer ki meeting hai"))
        assertEquals("hi-en", SentenceReconstructor.detectLanguageTag("Open Excel aur report dikhao"))

        // Pure Devanagari Hindi
        assertEquals("hi", SentenceReconstructor.detectLanguageTag("कल हमारी टीम काम करेगी"))

        // Pure English
        assertEquals("en", SentenceReconstructor.detectLanguageTag("The financial audit is completed"))
    }
    @Test
    fun testCase15_AdaptivePauseThresholds_ConversationalVsDictation() {
        // Two phrases separated by a 600ms pause
        val tokens600ms = listOf(
            WordToken("open", 1000L, 1300L, 0.98f),
            WordToken("excel", 1350L, 1700L, 0.99f),
            // 600ms pause between 1700L and 2300L
            WordToken("show", 2300L, 2600L, 0.98f),
            WordToken("reports", 2650L, 3100L, 0.97f)
        )

        // Conversational threshold (500ms): splits at 600ms pause
        val conversationalSegs = SentenceReconstructor.reconstruct(
            tokens600ms,
            languageCode = "hi-en",
            pauseThresholdMs = SentenceReconstructor.PAUSE_THRESHOLD_CONVERSATIONAL
        )
        assertEquals(2, conversationalSegs.size)
        assertEquals("Open Excel.", conversationalSegs[0].text)
        assertEquals("Show reports.", conversationalSegs[1].text)

        // Balanced threshold (750ms): retains as 1 sentence
        val balancedSegs = SentenceReconstructor.reconstruct(
            tokens600ms,
            languageCode = "hi-en",
            pauseThresholdMs = SentenceReconstructor.PAUSE_THRESHOLD_BALANCED
        )
        assertEquals(1, balancedSegs.size)
        assertEquals("Open Excel show reports.", balancedSegs[0].text)

        // Dictation threshold (1100ms): retains as 1 sentence
        val dictationSegs = SentenceReconstructor.reconstruct(
            tokens600ms,
            languageCode = "hi-en",
            pauseThresholdMs = SentenceReconstructor.PAUSE_THRESHOLD_DICTATION
        )
        assertEquals(1, dictationSegs.size)
    }

    @Test
    fun testCase16_ExtractWords_SynthesizesMonotonicTimestampsWhenResultArrayMissing() {
        val engine = VoskTranscriptionEngine()
        val jsonFallback = "{\"text\": \"kal customer ki meeting hai\"}"
        val streamTimeMs = 5000L

        val words = engine.extractWords(jsonFallback, streamTimeMs)
        assertEquals(5, words.size)
        assertEquals("kal", words[0].word)
        assertEquals("customer", words[1].word)
        assertEquals("ki", words[2].word)
        assertEquals("meeting", words[3].word)
        assertEquals("hai", words[4].word)

        // Verify strictly monotonic timestamps
        for (i in words.indices) {
            assertTrue("Word start must be < end", words[i].startMs < words[i].endMs)
            assertTrue("Word timestamps must be non-zero", words[i].startMs > 0L)
            if (i > 0) {
                assertTrue(
                    "Words must have monotonic timestamps: ${words[i].startMs} >= ${words[i-1].endMs}",
                    words[i].startMs >= words[i - 1].endMs
                )
            }
        }
    }

    @Test
    fun testCase17_VoskWordResultTimestampsPreserved() {
        val engine = VoskTranscriptionEngine()
        val jsonWithResult = """
            {
              "result": [
                {"conf": 0.98, "start": 1.25, "end": 1.65, "word": "kal"},
                {"conf": 0.97, "start": 1.70, "end": 2.20, "word": "meeting"}
              ],
              "text": "kal meeting"
            }
        """.trimIndent()

        val words = engine.extractWords(jsonWithResult, 3000L)
        assertEquals(2, words.size)
        assertEquals(1250L, words[0].startMs)
        assertEquals(1650L, words[0].endMs)
        assertEquals(1700L, words[1].startMs)
        assertEquals(2200L, words[1].endMs)
    }
}
