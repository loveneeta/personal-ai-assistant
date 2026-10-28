package com.personalai.assistant.transcription.models

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val language: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val approxRamFormatted: String,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val isActive: Boolean = false
)

object AvailableModels {
    val INDIAN_ENGLISH = ModelInfo(
        id = "vosk-model-small-en-in-0.4",
        name = "Indian English Accent",
        description = "Acoustically tuned for Indian English accents, local business terminology, and speech cadences.",
        language = "English (India)",
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
        sizeBytes = 37748736L,
        sizeFormatted = "36 MB",
        approxRamFormatted = "~80 MB RAM"
    )

    val HINDI = ModelInfo(
        id = "vosk-model-small-hi-0.22",
        name = "Hindi (हिन्दी)",
        description = "Lightweight offline Hindi acoustic model for pure Hindi conversations and meetings.",
        language = "Hindi",
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
        sizeBytes = 44040192L,
        sizeFormatted = "42 MB",
        approxRamFormatted = "~90 MB RAM"
    )

    val GENERAL_ENGLISH = ModelInfo(
        id = "vosk-model-small-en-us-0.15",
        name = "English (Universal)",
        description = "Standard compact English speech recognition model.",
        language = "English",
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
        sizeBytes = 41943040L,
        sizeFormatted = "40 MB",
        approxRamFormatted = "~80 MB RAM"
    )

    val ALL_MODELS = listOf(INDIAN_ENGLISH, HINDI, GENERAL_ENGLISH)
}
