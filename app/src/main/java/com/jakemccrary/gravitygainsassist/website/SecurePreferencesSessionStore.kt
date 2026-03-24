package com.jakemccrary.gravitygainsassist.website

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurePreferencesSessionStore(
    context: Context,
    private val tokenCipher: TokenCipher = AndroidKeyStoreTokenCipher(),
) : SessionStore {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): StoredSessionRecord {
        val encryptedToken = sharedPreferences.getString(TOKEN_KEY, null)
        val isInvalid = sharedPreferences.getBoolean(INVALID_KEY, false)
        if (encryptedToken.isNullOrBlank()) {
            return StoredSessionRecord()
        }

        return try {
            StoredSessionRecord(
                token = tokenCipher.decrypt(encryptedToken),
                isInvalid = isInvalid,
            )
        } catch (_: Throwable) {
            clear()
            StoredSessionRecord()
        }
    }

    override fun write(record: StoredSessionRecord) {
        if (record.token.isNullOrBlank()) {
            clear()
            return
        }

        sharedPreferences.edit()
            .putString(TOKEN_KEY, tokenCipher.encrypt(record.token))
            .putBoolean(INVALID_KEY, record.isInvalid)
            .apply()
    }

    override fun clear() {
        sharedPreferences.edit()
            .remove(TOKEN_KEY)
            .remove(INVALID_KEY)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "grip_gains_session"
        const val TOKEN_KEY = "token"
        const val INVALID_KEY = "invalid"
    }
}

interface TokenCipher {
    fun encrypt(plainText: String): String

    fun decrypt(cipherText: String): String
}

class AndroidKeyStoreTokenCipher : TokenCipher {
    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + encryptedBytes
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    override fun decrypt(cipherText: String): String {
        val combined = Base64.decode(cipherText, Base64.NO_WRAP)
        require(combined.size > IV_LENGTH_BYTES) { "Encrypted token payload was invalid." }
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val encryptedBytes = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, iv),
        )
        return cipher.doFinal(encryptedBytes).toString(Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "gravity_gains_assist_session_key"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
