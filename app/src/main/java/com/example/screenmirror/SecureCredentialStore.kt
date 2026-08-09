package com.example.screenmirror

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureCredentialStore {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "screenmirror_turn_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREF_NAME = "secure_credentials"
    private const val IV_PREFIX = "iv_"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        keyStore.getEntry(KEY_ALIAS, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    fun encrypt(context: Context, key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("$IV_PREFIX$key", Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    fun decrypt(context: Context, key: String, defaultValue: String = ""): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(key, null) ?: return defaultValue
        val iv = prefs.getString("$IV_PREFIX$key", null) ?: return defaultValue

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
            val decrypted = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP))
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun migrateFromPlainText(context: Context) {
        val legacyPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val keys = mapOf(
            "turn_url_enc" to legacyPrefs.getString("turn_url", null),
            "turn_user_enc" to legacyPrefs.getString("turn_user", null),
            "turn_pass_enc" to legacyPrefs.getString("turn_pass", null)
        )
        val securePrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        keys.forEach { (encKey, plainValue) ->
            if (plainValue != null && !securePrefs.contains(encKey)) {
                val rawKey = encKey.removeSuffix("_enc")
                encrypt(context, rawKey, plainValue)
            }
        }
    }
}
