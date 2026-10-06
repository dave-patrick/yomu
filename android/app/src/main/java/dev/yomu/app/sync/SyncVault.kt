package dev.yomu.app.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SyncVault(context: Context) {
    private val prefs = context.getSharedPreferences("sync_secrets", Context.MODE_PRIVATE)
    fun server() = prefs.getString("server", "").orEmpty()
    fun token(): String? = runCatching {
        val raw = prefs.getString("key", null) ?: return null
        val bytes = Base64.decode(raw, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrNull()
    fun save(server: String, token: String) {
        require(server.startsWith("https://") && !server.contains('?') && !server.contains('#')) { "Enter your HTTPS Yomu website." }
        val uri = java.net.URI(server)
        require(uri.host != null && uri.userInfo == null && uri.path.orEmpty().trim('/').isEmpty()) { "Enter the website address without a path or login information." }
        require(token.trim().length >= 32) { "Enter your private Yomu access key." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val raw = cipher.iv + cipher.doFinal(token.trim().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("server", server.trimEnd('/')).putString("key", Base64.encodeToString(raw, Base64.NO_WRAP)).commit())
    }
    fun clear() { prefs.edit().clear().commit() }
    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("dev.yomu.sync", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("dev.yomu.sync", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
}
