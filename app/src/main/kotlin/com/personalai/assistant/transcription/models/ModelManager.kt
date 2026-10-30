package com.personalai.assistant.transcription.models

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException

class InsufficientDiskSpaceException(message: String) : IOException(message)

sealed class ModelDownloadStage {
    object Idle : ModelDownloadStage()
    object Connecting : ModelDownloadStage()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadStage()
    object Extracting : ModelDownloadStage()
    object Verifying : ModelDownloadStage()
    object Completed : ModelDownloadStage()
    data class Failed(val error: String) : ModelDownloadStage()
    object Cancelled : ModelDownloadStage()
}

sealed class ModelInstallResult {
    data class Success(val modelId: String, val modelDir: File) : ModelInstallResult()
    data class Error(val modelId: String, val message: String, val cause: Throwable? = null) : ModelInstallResult()
    data class Cancelled(val modelId: String) : ModelInstallResult()
}

class ModelManager internal constructor(
    private val context: Context?,
    customModelsDir: File? = null,
    customCacheDir: File? = null,
    private val customPrefs: SharedPreferences? = null
) {
    constructor(context: Context) : this(context, null, null, null)

    private val prefs: SharedPreferences? = customPrefs ?: context?.getSharedPreferences("ai_models_prefs", Context.MODE_PRIVATE)
    private val modelsDir: File = customModelsDir ?: File(context?.filesDir ?: File("."), "models").apply { if (!exists()) mkdirs() }
    private val cacheDir: File = customCacheDir ?: context?.cacheDir ?: File(modelsDir, ".cache").apply { if (!exists()) mkdirs() }

    private val _modelsState = MutableStateFlow<List<ModelInfo>>(emptyList())
    val modelsState: StateFlow<List<ModelInfo>> = _modelsState.asStateFlow()

    private val _activeModelId = MutableStateFlow(
        prefs?.getString(KEY_ACTIVE_MODEL, AvailableModels.INDIAN_ENGLISH.id) ?: AvailableModels.INDIAN_ENGLISH.id
    )
    val activeModelId: StateFlow<String> = _activeModelId.asStateFlow()

    init {
        cleanStaleTemporaryFiles()
        refreshModelsState()
    }

    /**
     * Cleans up stale temporary download files and interrupted staging directories on startup.
     */
    fun cleanStaleTemporaryFiles() {
        try {
            if (cacheDir.exists()) {
                cacheDir.listFiles { _, name -> name.startsWith("model_dl_") || name.endsWith(".tmp") }?.forEach {
                    it.delete()
                }
            }
            if (modelsDir.exists()) {
                modelsDir.listFiles { f -> f.isDirectory && (f.name.contains("_staging_") || f.name.contains("_backup_")) }?.forEach {
                    it.deleteRecursively()
                }
            }
        } catch (_: Exception) {
            // Ignore startup cleanup exceptions
        }
    }

    fun refreshModelsState() {
        val currentActive = _activeModelId.value
        val updated = AvailableModels.ALL_MODELS.map { model ->
            val dir = File(modelsDir, model.id)
            val isDownloaded = isModelDirectoryValid(dir)
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

    /**
     * Resolves the exact model directory for the given [modelId].
     * Never silently substitutes an unrelated language model.
     */
    fun getModelDirectory(modelId: String?): File? {
        if (modelId.isNullOrBlank()) return null
        val dir = File(modelsDir, modelId)
        return resolveModelDir(dir)
    }

    fun isModelAvailable(modelId: String): Boolean {
        return getModelDirectory(modelId) != null
    }

    private fun isModelDirectoryValid(dir: File): Boolean {
        return resolveModelDir(dir) != null
    }

    fun resolveModelDir(dir: File): File? {
        if (!dir.exists() || !dir.isDirectory) return null
        if (isValidVoskModelStructure(dir)) {
            return dir
        }
        // If unpacked inside a nested subfolder (e.g. vosk-model-small-hi-0.22/vosk-model-small-hi-0.22)
        val children = dir.listFiles { f -> f.isDirectory } ?: return null
        for (child in children) {
            if (isValidVoskModelStructure(child)) {
                return child
            }
        }
        return null
    }

    /**
     * Validates that the directory contains required Vosk acoustic and graph/conf components.
     * Prevents activation of corrupted or incomplete extractions.
     */
    fun isValidVoskModelStructure(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val amDir = File(dir, "am")
        val finalMdl = File(dir, "final.mdl")
        val confDir = File(dir, "conf")
        val graphDir = File(dir, "graph")

        val hasAm = (amDir.isDirectory && (amDir.list()?.isNotEmpty() == true)) || (finalMdl.isFile && finalMdl.length() > 0)
        val hasConf = confDir.isDirectory && (confDir.list()?.isNotEmpty() == true)
        val hasGraph = graphDir.isDirectory && (graphDir.list()?.isNotEmpty() == true)

        return hasAm && (hasConf || hasGraph)
    }

    /**
     * Sets the active model, strictly verifying that the target model is available and complete.
     */
    fun setActiveModel(modelId: String): Boolean {
        if (!isModelAvailable(modelId)) {
            return false
        }
        prefs?.edit()?.putString(KEY_ACTIVE_MODEL, modelId)?.apply()
        _activeModelId.value = modelId
        refreshModelsState()
        return true
    }

    /**
     * Downloads and installs a speech model with progress callback.
     * Returns true on success, false on failure or cancellation.
     */
    suspend fun downloadModel(
        modelId: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        val result = installModel(
            modelId = modelId,
            onStage = { stage ->
                if (stage is ModelDownloadStage.Downloading) {
                    onProgress(stage.progress)
                }
            }
        )
        return result is ModelInstallResult.Success
    }

    /**
     * Secure, atomic model download and installation pipeline.
     */
    suspend fun installModel(
        modelId: String,
        onStage: (ModelDownloadStage) -> Unit = {}
    ): ModelInstallResult = withContext(Dispatchers.IO) {
        val model = AvailableModels.ALL_MODELS.find { it.id == modelId }
            ?: return@withContext ModelInstallResult.Error(modelId, "Unknown model ID: $modelId")

        // 1. Verify available disk space
        val usableSpace = modelsDir.usableSpace
        val minRequiredSpace = (model.sizeBytes * 2.5).toLong().coerceAtLeast(120L * 1024 * 1024)
        if (usableSpace in 1 until minRequiredSpace) {
            val freeMb = usableSpace / (1024 * 1024)
            val reqMb = minRequiredSpace / (1024 * 1024)
            val err = "Insufficient storage: requires at least ${reqMb}MB free space, but only ${freeMb}MB is available."
            onStage(ModelDownloadStage.Failed(err))
            return@withContext ModelInstallResult.Error(modelId, err, InsufficientDiskSpaceException(err))
        }

        if (!cacheDir.exists()) cacheDir.mkdirs()
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val tempZipFile = File(cacheDir, "model_dl_${model.id}_${System.currentTimeMillis()}.tmp")
        val stagingDir = File(modelsDir, "${model.id}_staging_${System.currentTimeMillis()}")
        var connection: HttpURLConnection? = null

        try {
            onStage(ModelDownloadStage.Connecting)

            // 2. Open connection with redirects & timeouts
            var targetUrl = model.downloadUrl
            var redirectCount = 0
            while (redirectCount < 5) {
                currentCoroutineContext().ensureActive()
                val url = URL(targetUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "JARVIS-Personal-AI-Assistant/1.0")
                    setRequestProperty("Accept-Encoding", "identity")
                }
                conn.connect()
                val code = conn.responseCode
                if (code in 301..308 && code != 304 && code != 305) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!location.isNullOrBlank()) {
                        targetUrl = location
                        redirectCount++
                        continue
                    }
                }
                connection = conn
                break
            }

            val conn = connection ?: throw IOException("Failed to establish HTTP connection to ${model.downloadUrl}")
            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Server returned HTTP error: $responseCode ${conn.responseMessage}")
            }

            val fileLength = conn.contentLengthLong.takeIf { it > 0 } ?: model.sizeBytes

            // 3. Stream download into temporary file
            conn.inputStream.use { rawIn ->
                BufferedInputStream(rawIn).use { bis ->
                    FileOutputStream(tempZipFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var total: Long = 0
                        var count: Int
                        while (bis.read(buffer).also { count = it } != -1) {
                            currentCoroutineContext().ensureActive()
                            fos.write(buffer, 0, count)
                            total += count
                            if (fileLength > 0) {
                                val progress = (total.toFloat() / fileLength.toFloat()).coerceIn(0f, 1f)
                                onStage(ModelDownloadStage.Downloading(progress, total, fileLength))
                            }
                        }
                        fos.flush()
                    }
                }
            }

            // 4. Extract archive into isolated staging directory
            onStage(ModelDownloadStage.Extracting)
            if (!stagingDir.exists()) stagingDir.mkdirs()
            unzip(tempZipFile, stagingDir)

            // 5. Verify integrity of extracted model structure
            onStage(ModelDownloadStage.Verifying)
            val resolvedStagingModelDir = resolveModelDir(stagingDir)
            if (resolvedStagingModelDir == null || !isValidVoskModelStructure(resolvedStagingModelDir)) {
                throw IOException("Extracted model failed validation. Missing required acoustic (am) or graph/conf assets.")
            }

            // 6. Atomic installation to final target directory
            val finalTargetDir = File(modelsDir, model.id)
            if (finalTargetDir.exists()) {
                val backupDir = File(modelsDir, "${model.id}_backup_${System.currentTimeMillis()}")
                if (!finalTargetDir.renameTo(backupDir)) {
                    finalTargetDir.deleteRecursively()
                } else {
                    backupDir.deleteRecursively()
                }
            }

            val renamed = stagingDir.renameTo(finalTargetDir)
            if (!renamed) {
                copyDirectory(stagingDir, finalTargetDir)
                stagingDir.deleteRecursively()
            }

            // Clean up temporary download file
            if (tempZipFile.exists()) tempZipFile.delete()

            // 7. Auto-activate if no active model exists or if this was the chosen active model
            if (_activeModelId.value == modelId || getActiveModelDirectory() == null) {
                setActiveModel(modelId)
            } else {
                refreshModelsState()
            }

            onStage(ModelDownloadStage.Completed)
            ModelInstallResult.Success(modelId, finalTargetDir)
        } catch (e: CancellationException) {
            cleanUpArtifacts(tempZipFile, stagingDir)
            onStage(ModelDownloadStage.Cancelled)
            ModelInstallResult.Cancelled(modelId)
        } catch (e: Exception) {
            cleanUpArtifacts(tempZipFile, stagingDir)
            val message = e.localizedMessage ?: "Installation failed"
            onStage(ModelDownloadStage.Failed(message))
            ModelInstallResult.Error(modelId, message, e)
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun cleanUpArtifacts(tempZip: File, stagingDir: File) {
        try {
            if (tempZip.exists()) tempZip.delete()
            if (stagingDir.exists()) stagingDir.deleteRecursively()
        } catch (_: Exception) {
        }
    }

    private fun copyDirectory(source: File, target: File) {
        if (!target.exists()) target.mkdirs()
        source.listFiles()?.forEach { file ->
            val dest = File(target, file.name)
            if (file.isDirectory) {
                copyDirectory(file, dest)
            } else {
                file.copyTo(dest, overwrite = true)
            }
        }
    }

    /**
     * Deletes a model and ensures active-model settings never point to a deleted model.
     */
    fun deleteModel(modelId: String): Boolean {
        val targetDir = File(modelsDir, modelId)
        val wasActive = (_activeModelId.value == modelId)
        var deleted = false

        if (targetDir.exists()) {
            deleted = targetDir.deleteRecursively()
        }

        if (wasActive) {
            // Find another installed, valid model to fall back to
            val alternative = AvailableModels.ALL_MODELS.firstOrNull { model ->
                model.id != modelId && isModelAvailable(model.id)
            }
            if (alternative != null) {
                prefs?.edit()?.putString(KEY_ACTIVE_MODEL, alternative.id)?.apply()
                _activeModelId.value = alternative.id
            } else {
                prefs?.edit()?.remove(KEY_ACTIVE_MODEL)?.apply()
                _activeModelId.value = ""
            }
        }

        refreshModelsState()
        return deleted
    }

    fun unzip(zipFile: File, targetDirectory: File) = Companion.unzip(zipFile, targetDirectory)

    companion object {
        private const val KEY_ACTIVE_MODEL = "key_active_stt_model"

        /**
         * Extracts an archive with Zip Slip path traversal and zip bomb mitigations.
         */
        fun unzip(zipFile: File, targetDirectory: File, maxTotalBytes: Long = 600L * 1024 * 1024) {
            val canonicalTarget = targetDirectory.canonicalPath
            var totalBytes: Long = 0
            var entryCount = 0

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > 10000) {
                        throw SecurityException("ZIP archive contains excessive entries (zip bomb protection).")
                    }

                    val name = entry.name
                    val file = File(targetDirectory, name)
                    val canonicalFile = file.canonicalPath
                    if (!canonicalFile.startsWith(canonicalTarget + File.separator) && canonicalFile != canonicalTarget) {
                        throw SecurityException("Zip Slip path traversal vulnerability detected in entry: $name")
                    }

                    if (entry.isDirectory) {
                        if (!file.exists() && !file.mkdirs()) {
                            throw IOException("Failed to create directory: ${file.path}")
                        }
                    } else {
                        file.parentFile?.let { p ->
                            if (!p.exists() && !p.mkdirs()) {
                                throw IOException("Failed to create parent directory: ${p.path}")
                            }
                        }
                        FileOutputStream(file).use { fos ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                totalBytes += len
                                if (totalBytes > maxTotalBytes) {
                                    throw SecurityException("ZIP extraction exceeded maximum size limit ($maxTotalBytes bytes).")
                                }
                                fos.write(buffer, 0, len)
                            }
                            fos.flush()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (entryCount == 0) {
                throw IOException("Corrupt or empty ZIP archive: no valid entries found in ${zipFile.name}")
            }
        }

        @Volatile
        private var INSTANCE: ModelManager? = null

        fun getInstance(context: Context): ModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
