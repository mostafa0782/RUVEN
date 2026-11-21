package iq.ruven.app

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the rotating Supabase refresh token encrypted with a non-exportable
 * Android Keystore AES-GCM key. The legacy plaintext preference is migrated
 * once and removed after successful encryption.
 */
internal object SecureSessionStore {
    private const val PREFS_NAME = "ruven_auth_secure"
    private const val LEGACY_PREFS_NAME = "ruven_auth"
    private const val TOKEN_KEY = "refresh_token"
    private const val KEY_ALIAS = "ruven_refresh_token_aes_gcm_v1"
    private const val AAD = "iq.ruven.app.refresh-token.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val lock = Any()

    fun read(context: Context): String? = synchronized(lock) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(TOKEN_KEY, null)
        if (!stored.isNullOrBlank()) {
            return@synchronized try {
                decrypt(stored)
            } catch (_: Exception) {
                prefs.edit().remove(TOKEN_KEY).commit()
                null
            }
        }

        val legacyPrefs = app.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacy = legacyPrefs.getString(TOKEN_KEY, null)
        if (legacy.isNullOrBlank()) return@synchronized null

        try {
            saveLocked(prefs, legacy)
            legacyPrefs.edit().remove(TOKEN_KEY).commit()
            legacy
        } catch (_: Exception) {
            // Fail closed rather than continuing to persist a plaintext refresh token.
            legacyPrefs.edit().remove(TOKEN_KEY).commit()
            null
        }
    }

    fun save(context: Context, refreshToken: String) = synchronized(lock) {
        require(refreshToken.isNotBlank()) { "Refresh token must not be blank." }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        saveLocked(prefs, refreshToken)
        Unit
    }

    fun clear(context: Context) = synchronized(lock) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        }
        Unit
    }

    private fun saveLocked(prefs: SharedPreferences, token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(AAD.toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(prefs.edit().putString(TOKEN_KEY, encoded).commit()) {
            "Could not persist the encrypted session."
        }
    }

    private fun decrypt(stored: String): String {
        val parts = stored.split('.', limit = 2)
        require(parts.size == 2) { "Stored session format is invalid." }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD.toByteArray(StandardCharsets.UTF_8))
        return cipher.doFinal(encrypted).toString(StandardCharsets.UTF_8).also {
            require(it.isNotBlank()) { "Stored session is empty." }
        }
    }

    private fun getExistingKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("Session encryption key is missing.")
    }

    private fun getOrCreateKey(): SecretKey {
        try {
            return getExistingKey()
        } catch (_: Exception) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setKeySize(256)
                    .build()
            )
            return generator.generateKey()
        }
    }
}
