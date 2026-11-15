package com.personalai.assistant.playback

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Centralized, Production-Grade On-Device Text-To-Speech (TTS) Engine for J.A.R.V.I.S.
 *
 * Guarantees vocal AI responses across Chat, Voice Orb, and Always-On states:
 * 1. Automatically binds to Google Speech Services / System TTS.
 * 2. Uses assistant-priority [AudioAttributes] so voice is loud and clear.
 * 3. Sanitizes markdown formatting into natural vocal cadence.
 * 4. Queues requests if spoken while TTS is warming up so responses are never lost.
 * 5. Supports multilingual English and Hindi/Hinglish speech output.
 */
class JarvisTtsEngine private constructor(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "JarvisTtsEngine"
        private const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"

        @Volatile
        private var INSTANCE: JarvisTtsEngine? = null

        fun getInstance(context: Context): JarvisTtsEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JarvisTtsEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var tts: TextToSpeech? = null
    private val isReady = AtomicBoolean(false)
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val utteranceCounter = AtomicLong(0L)
    private val pendingQueue = ConcurrentLinkedQueue<Pair<String, (() -> Unit)?>>()
    private val completionCallbacks = mutableMapOf<String, (() -> Unit)?>()

    init {
        initializeTts()
    }

    private fun initializeTts() {
        try {
            // First attempt to bind directly to Google TTS engine for best speech quality
            tts = TextToSpeech(context, this, GOOGLE_TTS_PACKAGE)
            Log.i(TAG, "Initializing JarvisTtsEngine with Google TTS engine...")
        } catch (e: Throwable) {
            Log.w(TAG, "Google TTS engine unavailable, falling back to default system TTS: ${e.message}")
            try {
                tts = TextToSpeech(context, this)
            } catch (e2: Throwable) {
                Log.e(TAG, "Failed to initialize default TextToSpeech: ${e2.message}", e2)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            Log.i(TAG, "TextToSpeech initialized successfully with engine: ${engine.defaultEngine}")

            // 1. Configure audio attributes for high-clarity voice output
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                engine.setAudioAttributes(audioAttributes)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to set audio attributes on TTS: ${e.message}")
            }

            // 2. Configure natural vocal acoustic parameters
            try {
                engine.setPitch(0.94f) // Slightly deeper, refined executive resonance
                engine.setSpeechRate(1.03f) // Natural conversational rhythm
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to set pitch/rate on TTS: ${e.message}")
            }

            // 3. Set default language and select highest-quality natural neural voice
            var langSet = false
            try {
                val inLocale = Locale.forLanguageTag("en-IN")
                if (engine.isLanguageAvailable(inLocale) >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = inLocale
                    selectBestNaturalVoice(engine, inLocale)
                    langSet = true
                    Log.i(TAG, "Configured TTS language to Indian English (en-IN)")
                }
            } catch (_: Throwable) {}

            if (!langSet) {
                try {
                    engine.language = Locale.ENGLISH
                    selectBestNaturalVoice(engine, Locale.ENGLISH)
                    Log.i(TAG, "Configured TTS language to Locale.ENGLISH")
                } catch (_: Throwable) {
                    engine.language = Locale.getDefault()
                }
            }

            // 4. Set Utterance Progress Listener
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    Log.d(TAG, "TTS vocalization started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    Log.d(TAG, "TTS vocalization finished: $utteranceId")
                    utteranceId?.let { id ->
                        synchronized(completionCallbacks) {
                            completionCallbacks.remove(id)?.invoke()
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    Log.e(TAG, "TTS vocalization error on utterance: $utteranceId")
                    utteranceId?.let { id ->
                        synchronized(completionCallbacks) {
                            completionCallbacks.remove(id)?.invoke()
                        }
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    Log.e(TAG, "TTS vocalization error code $errorCode on utterance: $utteranceId")
                    utteranceId?.let { id ->
                        synchronized(completionCallbacks) {
                            completionCallbacks.remove(id)?.invoke()
                        }
                    }
                }
            })

            isReady.set(true)

            // 4. Drain any speech requests queued while TTS was warming up
            while (!pendingQueue.isEmpty()) {
                val item = pendingQueue.poll() ?: break
                speak(item.first, onDone = item.second)
            }
        } else {
            Log.e(TAG, "TextToSpeech onInit failed with status code: $status")
            isReady.set(false)
        }
    }

    /**
     * Speaks the provided text out loud using natural acoustic vocalization.
     * Sanitizes markdown symbols, bullet points, and code markers before speaking.
     */
    fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        onDone: (() -> Unit)? = null
    ) {
        val cleanSpeech = sanitizeForSpeech(text)
        if (cleanSpeech.isBlank()) return

        if (!isReady.get() || tts == null) {
            Log.d(TAG, "TTS not ready yet, queuing speech: '${cleanSpeech.take(50)}...'")
            pendingQueue.add(cleanSpeech to onDone)
            return
        }

        val engine = tts ?: return

        // Context-aware language switching: if Devanagari is present, speak in Hindi
        if (containsDevanagari(cleanSpeech)) {
            try {
                val hiLocale = Locale.forLanguageTag("hi-IN")
                if (engine.isLanguageAvailable(hiLocale) >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = hiLocale
                    selectBestNaturalVoice(engine, hiLocale)
                }
            } catch (_: Throwable) {}
        } else {
            try {
                val inLocale = Locale.forLanguageTag("en-IN")
                if (engine.isLanguageAvailable(inLocale) >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = inLocale
                    selectBestNaturalVoice(engine, inLocale)
                }
            } catch (_: Throwable) {}
        }

        val utteranceId = "JARVIS_VOICE_${utteranceCounter.incrementAndGet()}"
        if (onDone != null) {
            synchronized(completionCallbacks) {
                completionCallbacks[utteranceId] = onDone
            }
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        _isSpeaking.value = true
        val result = engine.speak(cleanSpeech, queueMode, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS speak failed with return code $result for utterance: $utteranceId")
            _isSpeaking.value = false
        } else {
            Log.i(TAG, "Vocalizing speech (${cleanSpeech.length} chars): '${cleanSpeech.take(60)}...'")
        }
    }

    /**
     * Immediately stops ongoing vocalization.
     */
    fun stop() {
        try {
            tts?.stop()
        } catch (_: Throwable) {}
        _isSpeaking.value = false
        pendingQueue.clear()
        synchronized(completionCallbacks) {
            completionCallbacks.clear()
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {}
        tts = null
        isReady.set(false)
        _isSpeaking.value = false
    }

    /**
     * Strips markdown symbols, asterisks, URLs, and code blocks into natural spoken prose.
     * Expands common abbreviations and adds cadence pauses for realistic human speech.
     */
    private fun sanitizeForSpeech(raw: String): String {
        var text = raw

        // Remove markdown bold/italics (e.g. **word** -> word, *word* -> word)
        text = text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        text = text.replace(Regex("\\*([^*]+)\\*"), "$1")
        text = text.replace(Regex("__([^_]+)__"), "$1")
        text = text.replace(Regex("_([^_]+)_"), "$1")

        // Remove markdown headers (# Title -> Title)
        text = text.replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")

        // Remove markdown links [Label](url) -> Label
        text = text.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")

        // Remove URLs
        text = text.replace(Regex("https?://\\S+"), "")

        // Remove code blocks and backticks
        text = text.replace(Regex("```[\\s\\S]*?```"), "")
        text = text.replace(Regex("`([^`]+)`"), "$1")

        // Clean bullet points with a conversational pause
        text = text.replace(Regex("^[\\-*•]\\s+", RegexOption.MULTILINE), "")

        // Convert common abbreviations into natural vocal words
        text = text.replace(Regex("(?i)\\bmin\\b"), "minutes")
        text = text.replace(Regex("(?i)\\bsec\\b"), "seconds")
        text = text.replace(Regex("(?i)\\bhr\\b"), "hours")
        text = text.replace(Regex("(?i)\\bhrs\\b"), "hours")
        text = text.replace(Regex("(?i)\\bapprox\\.?"), "approximately")
        text = text.replace(Regex("(?i)\\bvs\\.?"), "versus")
        text = text.replace(Regex("(?i)\\be\\.g\\.?"), "for example")
        text = text.replace(Regex("(?i)\\bi\\.e\\.?"), "that is")

        // Replace newlines with natural pauses
        text = text.replace(Regex("\n+"), ". ")

        // Collapse duplicate whitespace
        text = text.replace(Regex("\\s+"), " ").trim()

        return text
    }

    /**
     * Queries available on-device voices and selects the highest-quality neural voice.
     */
    private fun selectBestNaturalVoice(engine: TextToSpeech, targetLocale: Locale) {
        try {
            val availableVoices = engine.voices ?: return
            val candidateVoices = availableVoices.filter { voice ->
                voice.locale.language.equals(targetLocale.language, ignoreCase = true) &&
                        !voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }

            if (candidateVoices.isNotEmpty()) {
                val best = candidateVoices.maxByOrNull { voice ->
                    var score = 0
                    if (voice.quality >= Voice.QUALITY_VERY_HIGH) score += 50
                    else if (voice.quality >= Voice.QUALITY_HIGH) score += 30
                    if (voice.name.contains("neural", ignoreCase = true) || voice.name.contains("wavenet", ignoreCase = true)) score += 40
                    if (voice.name.contains("network", ignoreCase = true)) score += 15
                    if (targetLocale.country.isNotBlank() && voice.locale.country.equals(targetLocale.country, ignoreCase = true)) score += 20
                    score
                }
                if (best != null) {
                    engine.voice = best
                    Log.i(TAG, "Selected natural neural voice: ${best.name} (quality=${best.quality}, locale=${best.locale})")
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Voice selection fallback: ${e.message}")
        }
    }

    /**
     * Creates an Intent to launch Android's Text-to-Speech settings so the user
     * can download high-definition Google Neural voice packs directly on their device.
     */
    fun createTtsSettingsIntent(): Intent {
        return Intent("com.android.settings.TTS_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun containsDevanagari(text: String): Boolean {
        for (ch in text) {
            val block = Character.UnicodeBlock.of(ch)
            if (block == Character.UnicodeBlock.DEVANAGARI) return true
        }
        return false
    }
}
