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

/** Fish Audio API keys are encrypted using an app-local Android Keystore key. */
class FishAudioCredentialVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("speech_secrets", Context.MODE_PRIVATE)

    fun save(key: String) {
        val cleanKey = key.trim()
        require(cleanKey.isNotBlank()) { "Enter the Fish Audio API key." }
        require(cleanKey.all { it.code in 33..126 }) { "Enter one Fish Audio API key using printable characters only." }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.iv + cipher.doFinal(cleanKey.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "Could not save the Fish Audio key securely."
        }
    }

    fun hasKey(): Boolean = !preferences.getString(KEY, null).isNullOrBlank()

    fun key(): String? {
        val encoded = preferences.getString(KEY, null) ?: return null
        return runCatching {
            val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
            require(encrypted.size > IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, encrypted.copyOfRange(0, IV_BYTES)))
            }
            String(cipher.doFinal(encrypted.copyOfRange(IV_BYTES, encrypted.size)), Charsets.UTF_8)
                .takeIf { it.isNotBlank() && it.all { character -> character.code in 33..126 } }
        }.getOrNull()
    }

    fun clear() { preferences.edit().remove(KEY).commit() }

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
        private const val ALIAS = "dev.yomu.speech.fishaudio.aes"
        private const val KEY = "fishaudio.key.encrypted"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}
