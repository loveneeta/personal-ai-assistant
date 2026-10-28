package com.personalai.assistant.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testAesGcmEncryptionDecryptionRoundTrip() {
        val testData = "{\"message\":\"Zero cost offline encrypted personal backup\"}".toByteArray(StandardCharsets.UTF_8)
        val passphrase = "SecurePassword123!"

        // 1. Encrypt
        val random = SecureRandom()
        val salt = ByteArray(16).apply { random.nextBytes(this) }
        val iv = ByteArray(12).apply { random.nextBytes(this) }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 65536, 256)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(testData)

        // 2. Decrypt with correct passphrase
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val decryptedBytes = decryptCipher.doFinal(cipherText)

        assertEquals(String(testData, StandardCharsets.UTF_8), String(decryptedBytes, StandardCharsets.UTF_8))

        // 3. Decrypt with wrong passphrase must fail
        val wrongSpec = PBEKeySpec("WrongPassphrase".toCharArray(), salt, 65536, 256)
        val wrongKey = SecretKeySpec(factory.generateSecret(wrongSpec).encoded, "AES")
        val wrongCipher = Cipher.getInstance("AES/GCM/NoPadding")
        wrongCipher.init(Cipher.DECRYPT_MODE, wrongKey, GCMParameterSpec(128, iv))

        var failed = false
        try {
            wrongCipher.doFinal(cipherText)
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("Expected decryption with wrong passphrase to throw an exception", failed)
    }
}
