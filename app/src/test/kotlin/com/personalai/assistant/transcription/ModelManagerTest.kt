package com.personalai.assistant.transcription

import android.content.SharedPreferences
import com.personalai.assistant.transcription.models.AvailableModels
import com.personalai.assistant.transcription.models.InsufficientDiskSpaceException
import com.personalai.assistant.transcription.models.ModelDownloadStage
import com.personalai.assistant.transcription.models.ModelInstallResult
import com.personalai.assistant.transcription.models.ModelManager
import kotlinx.coroutines.test.runTest
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
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModelManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private val toRemove = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) temp[key] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) toRemove.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) data.clear()
            for (k in toRemove) data.remove(k)
            data.putAll(temp)
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String> ?: defValues)
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private fun createValidVoskModelDir(parentDir: File, modelName: String): File {
        val dir = File(parentDir, modelName)
        dir.mkdirs()
        val amDir = File(dir, "am").apply { mkdirs() }
        File(amDir, "final.mdl").writeBytes(ByteArray(1024))
        val confDir = File(dir, "conf").apply { mkdirs() }
        File(confDir, "model.conf").writeText("--sample-frequency=16000\n")
        val graphDir = File(dir, "graph").apply { mkdirs() }
        File(graphDir, "HCLG.fst").writeBytes(ByteArray(2048))
        return dir
    }

    private fun createZipArchive(zipFile: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for ((name, bytes) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }

    @Test
    fun test1_ZipSlipPathTraversal_detectedAndBlocked() {
        val maliciousZip = tempFolder.newFile("malicious.zip")
        val targetDir = tempFolder.newFolder("safe_target")

        createZipArchive(
            maliciousZip,
            mapOf("../evil_script.sh" to "echo hacked".toByteArray())
        )

        try {
            ModelManager.unzip(maliciousZip, targetDir)
            fail("Expected SecurityException on Zip Slip traversal attempt")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Zip Slip") == true)
        }

        // Verify evil script was never written outside target
        assertFalse(File(targetDir.parentFile, "evil_script.sh").exists())
    }

    @Test
    fun test2_IncompleteModel_missingRequiredAssets_failsValidation() {
        val modelsFolder = tempFolder.newFolder("models_test2")
        val cacheFolder = tempFolder.newFolder("cache_test2")
        val fakePrefs = FakeSharedPreferences()
        val manager = ModelManager(null, modelsFolder, cacheFolder, fakePrefs)

        // Empty directory
        val emptyDir = File(modelsFolder, "empty_model").apply { mkdirs() }
        assertFalse("Empty directory must fail validation", manager.isValidVoskModelStructure(emptyDir))

        // Directory with only empty 'am' folder
        val onlyAmDir = File(modelsFolder, "only_am").apply { mkdirs() }
        File(onlyAmDir, "am").mkdirs()
        assertFalse("Directory with empty am folder must fail validation", manager.isValidVoskModelStructure(onlyAmDir))

        // Directory with am but missing conf and graph
        val amWithFiles = File(modelsFolder, "am_only_files").apply { mkdirs() }
        File(amWithFiles, "am").mkdirs()
        File(File(amWithFiles, "am"), "final.mdl").writeText("data")
        assertFalse("Directory missing both conf and graph must fail validation", manager.isValidVoskModelStructure(amWithFiles))

        // Setting active on incomplete model must fail
        assertFalse(manager.setActiveModel("empty_model"))
    }

    @Test
    fun test3_ValidVoskModel_passesValidationAndCanBeActivated() {
        val modelsFolder = tempFolder.newFolder("models_test3")
        val cacheFolder = tempFolder.newFolder("cache_test3")
        val fakePrefs = FakeSharedPreferences()
        val manager = ModelManager(null, modelsFolder, cacheFolder, fakePrefs)

        val validDir = createValidVoskModelDir(modelsFolder, AvailableModels.INDIAN_ENGLISH.id)
        assertTrue(manager.isValidVoskModelStructure(validDir))
        assertTrue(manager.isModelAvailable(AvailableModels.INDIAN_ENGLISH.id))

        val activated = manager.setActiveModel(AvailableModels.INDIAN_ENGLISH.id)
        assertTrue("Valid model must be successfully activated", activated)
        assertEquals(AvailableModels.INDIAN_ENGLISH.id, manager.activeModelId.value)
        assertEquals(AvailableModels.INDIAN_ENGLISH.id, fakePrefs.getString("key_active_stt_model", null))
        assertNotNull(manager.getActiveModelDirectory())
    }

    @Test
    fun test4_ActiveModelDeletion_switchesToAlternativeDownloadedModel() {
        val modelsFolder = tempFolder.newFolder("models_test4")
        val cacheFolder = tempFolder.newFolder("cache_test4")
        val fakePrefs = FakeSharedPreferences()

        // Install two models: INDIAN_ENGLISH and HINDI
        createValidVoskModelDir(modelsFolder, AvailableModels.INDIAN_ENGLISH.id)
        createValidVoskModelDir(modelsFolder, AvailableModels.HINDI.id)

        val manager = ModelManager(null, modelsFolder, cacheFolder, fakePrefs)
        manager.setActiveModel(AvailableModels.INDIAN_ENGLISH.id)
        assertEquals(AvailableModels.INDIAN_ENGLISH.id, manager.activeModelId.value)

        // Delete active model
        val deleted = manager.deleteModel(AvailableModels.INDIAN_ENGLISH.id)
        assertTrue(deleted)
        assertFalse(manager.isModelAvailable(AvailableModels.INDIAN_ENGLISH.id))

        // Must automatically fall back to the available HINDI model
        assertEquals("Active model must switch to available fallback", AvailableModels.HINDI.id, manager.activeModelId.value)
        assertEquals(AvailableModels.HINDI.id, fakePrefs.getString("key_active_stt_model", null))
        assertNotNull(manager.getActiveModelDirectory())
    }

    @Test
    fun test5_ActiveModelDeletion_clearsPreferencesWhenNoAlternativesExist() {
        val modelsFolder = tempFolder.newFolder("models_test5")
        val cacheFolder = tempFolder.newFolder("cache_test5")
        val fakePrefs = FakeSharedPreferences()

        // Install only one model
        createValidVoskModelDir(modelsFolder, AvailableModels.INDIAN_ENGLISH.id)

        val manager = ModelManager(null, modelsFolder, cacheFolder, fakePrefs)
        manager.setActiveModel(AvailableModels.INDIAN_ENGLISH.id)
        assertEquals(AvailableModels.INDIAN_ENGLISH.id, manager.activeModelId.value)

        // Delete the only installed model
        val deleted = manager.deleteModel(AvailableModels.INDIAN_ENGLISH.id)
        assertTrue(deleted)

        // Active model must become empty string and preference cleared
        assertEquals("", manager.activeModelId.value)
        assertNull(fakePrefs.getString("key_active_stt_model", null))
        assertNull(manager.getActiveModelDirectory())
    }

    @Test
    fun test6_StaleTemporaryFiles_cleanedUpOnInit() {
        val modelsFolder = tempFolder.newFolder("models_test6")
        val cacheFolder = tempFolder.newFolder("cache_test6")
        val fakePrefs = FakeSharedPreferences()

        // Create stale tmp file in cache
        val staleTmp = File(cacheFolder, "model_dl_test_12345.tmp").apply { writeText("abandoned download") }
        // Create stale staging dir in models
        val staleStaging = File(modelsFolder, "vosk-model_staging_99999").apply { mkdirs() }
        File(staleStaging, "partial.bin").writeText("half extracted")

        assertTrue(staleTmp.exists())
        assertTrue(staleStaging.exists())

        // Initializing ModelManager triggers cleanStaleTemporaryFiles()
        val manager = ModelManager(null, modelsFolder, cacheFolder, fakePrefs)

        assertFalse("Stale .tmp download file must be cleaned up on init", staleTmp.exists())
        assertFalse("Stale _staging_ directory must be cleaned up on init", staleStaging.exists())
    }

    @Test
    fun test7_CorruptZipArchive_handledGracefullyWithoutCrashing() {
        val corruptZip = tempFolder.newFile("corrupt_model.zip")
        corruptZip.writeText("This is not a valid zip archive file contents.")
        val extractTarget = tempFolder.newFolder("corrupt_extract")

        try {
            ModelManager.unzip(corruptZip, extractTarget)
            fail("Expected IOException on corrupt ZIP archive")
        } catch (e: Exception) {
            assertTrue(e is IOException || e is java.util.zip.ZipException)
        }
    }

    @Test
    fun test8_AtomicUnpackAndValidation_nestedDirectoryResolution() {
        val modelsFolder = tempFolder.newFolder("models_test8")
        val cacheFolder = tempFolder.newFolder("cache_test8")
        val fakePrefs = FakeSharedPreferences()
        val manager = ModelManager(null, modelsFolder, cacheFolder, fakePrefs)

        // Create nested directory structure: models/model_id/nested_subfolder/am & conf
        val outerDir = File(modelsFolder, "nested_model").apply { mkdirs() }
        val innerDir = File(outerDir, "vosk-model-small-hi-0.22").apply { mkdirs() }
        val amDir = File(innerDir, "am").apply { mkdirs() }
        File(amDir, "final.mdl").writeText("acoustic data")
        val confDir = File(innerDir, "conf").apply { mkdirs() }
        File(confDir, "model.conf").writeText("config data")

        val resolved = manager.resolveModelDir(outerDir)
        assertNotNull("Nested model directory should be resolved", resolved)
        assertEquals(innerDir.canonicalPath, resolved!!.canonicalPath)
    }

    @Test
    fun test9_InsufficientDiskSpaceException_hierarchy() {
        val ex: Throwable = InsufficientDiskSpaceException("Test disk space")
        assertTrue("InsufficientDiskSpaceException must be an IOException", ex is IOException)
        assertEquals("Test disk space", ex.message)
    }
}
