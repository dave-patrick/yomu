package dev.yomu.app.speech

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Azure speech secrets are encrypted by a non-exportable Android Keystore key and excluded from backup. */
class AzureCredentialVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("speech_secrets", Context.MODE_PRIVATE)

    fun save(key: String, region: String) {
        val cleanKey = key.trim()
        require(cleanKey.isNotBlank()) { "Enter one Azure Speech key." }
        require(cleanKey.all { it.code in 33..126 }) { "Enter one Azure Speech key at a time using printable characters only." }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.iv + cipher.doFinal(cleanKey.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(ENCRYPTED_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(REGION, region.trim().ifBlank { "eastus" }).commit()) { "Could not save provider settings." }
    }

    fun hasKey(): Boolean = credentials() != null
    fun region(): String = preferences.getString(REGION, "eastus") ?: "eastus"

    fun credentials(): SpeechCredentials? {
        val saved = preferences.getString(ENCRYPTED_KEY, null) ?: return null
        return runCatching {
            val encrypted = Base64.decode(saved, Base64.NO_WRAP)
            require(encrypted.size > IV_BYTES) { "Saved provider key is invalid." }
            val iv = encrypted.copyOfRange(0, IV_BYTES)
            val bytes = encrypted.copyOfRange(IV_BYTES, encrypted.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv)) }
            val key = String(cipher.doFinal(bytes), Charsets.UTF_8)
            if (key.isBlank() || !key.all { it.code in 33..126 }) {
                clear()
                return null
            }
            SpeechCredentials(key, region())
        }.getOrNull()
    }

    /** Returns the raw decrypted key string, mirroring ElevenLabsCredentialVault.key(). */
    fun key(): String? {
        val saved = preferences.getString(ENCRYPTED_KEY, null) ?: return null
        return runCatching {
            val encrypted = Base64.decode(saved, Base64.NO_WRAP)
            require(encrypted.size > IV_BYTES) { "Saved provider key is invalid." }
            val iv = encrypted.copyOfRange(0, IV_BYTES)
            val bytes = encrypted.copyOfRange(IV_BYTES, encrypted.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv)) }
            val key = String(cipher.doFinal(bytes), Charsets.UTF_8)
            if (key.isBlank() || !key.all { it.code in 33..126 }) {
                clear()
                return null
            }
            key
        }.getOrNull()
    }

    fun clear() { preferences.edit().remove(ENCRYPTED_KEY).remove(REGION).commit() }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }

    companion object {
        private const val ALIAS = "dev.yomu.speech.azure.aes"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val ENCRYPTED_KEY = "azure.key.encrypted"
        private const val REGION = "azure.region"
    }
}
