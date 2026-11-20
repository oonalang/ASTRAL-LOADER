package kryvex.loader

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class RememberedKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences("kryvex_secure_login", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean("remember_key", false)

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            preferences.edit().putBoolean("remember_key", true).apply()
        } else {
            preferences.edit().remove("remember_key").remove("key_data").remove("key_iv").apply()
        }
    }

    fun save(key: String) {
        if (!isEnabled() || key.isBlank()) return
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
            preferences.edit()
                .putString("key_data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .apply()
        }
    }

    fun restore(): String? {
        if (!isEnabled()) return null
        return runCatching {
            val ciphertext = preferences.getString("key_data", null) ?: return null
            val iv = preferences.getString("key_iv", null) ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrElse {
            preferences.edit().remove("key_data").remove("key_iv").apply()
            null
        }
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("kryvex_saved_license", null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                "kryvex_saved_license",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
