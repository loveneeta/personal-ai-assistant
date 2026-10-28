package com.personalai.assistant.transcription.models

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class ModelManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("ai_models_prefs", Context.MODE_PRIVATE)
    private val modelsDir = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    private val _modelsState = MutableStateFlow<List<ModelInfo>>(emptyList())
    val modelsState: StateFlow<List<ModelInfo>> = _modelsState.asStateFlow()

    private val _activeModelId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_MODEL, AvailableModels.INDIAN_ENGLISH.id) ?: AvailableModels.INDIAN_ENGLISH.id
    )
    val activeModelId: StateFlow<String> = _activeModelId.asStateFlow()

    init {
        refreshModelsState()
    }

    fun refreshModelsState() {
        val currentActive = _activeModelId.value
        val updated = AvailableModels.ALL_MODELS.map { model ->
            val dir = File(modelsDir, model.id)
            val isDownloaded = dir.exists() && dir.isDirectory && (dir.list()?.isNotEmpty() == true)
            model.copy(
                isDownloaded = isDownloaded,
                isActive = isDownloaded && (model.id == currentActive)
            )
        }
        _modelsState.value = updated
    }

    fun getActiveModelDirectory(): File? {
        return getModelDirectory(_activeModelId.value)
    }

    fun getModelDirectory(modelId: String?): File? {
        val targetId = modelId ?: _activeModelId.value
        val dir = File(modelsDir, targetId)
        resolveModelDir(dir)?.let { return it }

        // Fallback: search any downloaded model
        for (m in AvailableModels.ALL_MODELS) {
            val d = File(modelsDir, m.id)
            resolveModelDir(d)?.let { return it }
        }
        return null
    }

    private fun resolveModelDir(dir: File): File? {
        if (!dir.exists() || !dir.isDirectory) return null
        // If dir contains "am" or "conf" or "graph", it is the model directory
        if (File(dir, "am").exists() || File(dir, "conf").exists() || File(dir, "graph").exists()) {
            return dir
        }
        // If unpacked inside a subfolder (e.g. vosk-model-small-hi-0.22/vosk-model-small-hi-0.22)
        val children = dir.listFiles { f -> f.isDirectory } ?: return null
        for (child in children) {
            if (File(child, "am").exists() || File(child, "conf").exists() || File(child, "graph").exists()) {
                return child
            }
        }
        return null
    }

    fun setActiveModel(modelId: String) {
        val dir = File(modelsDir, modelId)
        if (dir.exists()) {
            prefs.edit().putString(KEY_ACTIVE_MODEL, modelId).apply()
            _activeModelId.value = modelId
            refreshModelsState()
        }
    }

    suspend fun downloadModel(modelId: String, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val model = AvailableModels.ALL_MODELS.find { it.id == modelId } ?: return@withContext false
        val zipFile = File(context.cacheDir, "${model.id}.zip")
        val targetDir = File(modelsDir, model.id)

        try {
            // Download
            val url = URL(model.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()

            val fileLength = connection.contentLength
            val input = BufferedInputStream(connection.inputStream)
            val output = FileOutputStream(zipFile)

            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int

            while (input.read(data).also { count = it } != -1) {
                total += count
                output.write(data, 0, count)
                if (fileLength > 0) {
                    val progress = total.toFloat() / fileLength.toFloat()
                    onProgress(progress)
                }
            }

            output.flush()
            output.close()
            input.close()

            // Extract ZIP
            if (!targetDir.exists()) targetDir.mkdirs()
            unzip(zipFile, targetDir)
            zipFile.delete()

            // Update active model if none active
            if (_activeModelId.value == modelId || getActiveModelDirectory() == null) {
                setActiveModel(modelId)
            } else {
                refreshModelsState()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            if (zipFile.exists()) zipFile.delete()
            false
        }
    }

    private fun unzip(zipFile: File, targetDirectory: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDirectory, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    fun deleteModel(modelId: String): Boolean {
        val targetDir = File(modelsDir, modelId)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
            refreshModelsState()
            return true
        }
        return false
    }

    companion object {
        private const val KEY_ACTIVE_MODEL = "key_active_stt_model"

        @Volatile
        private var INSTANCE: ModelManager? = null

        fun getInstance(context: Context): ModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
