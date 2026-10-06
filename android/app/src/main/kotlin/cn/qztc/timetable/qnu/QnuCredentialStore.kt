package cn.qztc.timetable.qnu

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class QnuSavedCredentials(
    val username: String,
    val password: String,
)

/** Stores the full credential payload as AES-GCM ciphertext backed by Android Keystore. */
class QnuCredentialStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = preferences.contains(KEY_PAYLOAD)

    fun load(): QnuSavedCredentials? = runCatching {
        val encoded = preferences.getString(KEY_PAYLOAD, null) ?: return null
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(payload)
        val ivSize = buffer.get().toInt() and 0xff
        require(ivSize in 12..16 && buffer.remaining() > ivSize)
        val iv = ByteArray(ivSize).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val json = JSONObject(cipher.doFinal(encrypted).toString(Charsets.UTF_8))
        QnuSavedCredentials(
            username = json.getString(JSON_USERNAME),
            password = json.getString(JSON_PASSWORD),
        )
    }.getOrNull()

    fun save(credentials: QnuSavedCredentials) {
        require(credentials.username.isNotBlank()) { "账号不能为空" }
        require(credentials.password.isNotEmpty()) { "密码不能为空" }
        val cleartext = JSONObject()
            .put(JSON_USERNAME, credentials.username.trim())
            .put(JSON_PASSWORD, credentials.password)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key())
        }
        val encrypted = cipher.doFinal(cleartext)
        val payload = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(encrypted)
            .array()
        preferences.edit()
            .putString(KEY_PAYLOAD, Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_PAYLOAD).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "qnu_encrypted_credentials"
        const val KEY_PAYLOAD = "credential_payload"
        const val KEY_ALIAS = "qztc_timetable_qnu_credentials_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val JSON_USERNAME = "username"
        const val JSON_PASSWORD = "password"
    }
}
