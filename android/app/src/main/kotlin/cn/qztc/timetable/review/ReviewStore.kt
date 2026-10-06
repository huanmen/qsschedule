package cn.qztc.timetable.review

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class ReviewConnection(val endpoint: String = "", val model: String = "", val apiKey: String = "") {
    fun requestEndpoint(): String {
        val value = endpoint.trim().trimEnd('/')
        val path = URI(value).path.orEmpty()
        return when {
            path.isEmpty() -> "$value/v1/chat/completions"
            path.endsWith("/v1") -> "$value/chat/completions"
            else -> value
        }
    }
    fun validate() {
        val uri = runCatching { URI(endpoint.trim()) }.getOrNull()
        require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null && uri.query == null) { "请填写有效的 HTTPS API 地址" }
        require(endpoint.length <= 600 && model.isNotBlank() && model.length <= 100) { "请填写模型名称" }
        require(apiKey.length in 1..4096 && apiKey.none { it.isISOControl() }) { "请填写有效密钥" }
    }
    override fun toString() = "ReviewConnection(credentials=redacted)"
}

/** Model credentials and drafts remain on the device; no account or automatic publishing. */
class ReviewStore(context: Context) {
    private val prefs = context.getSharedPreferences("review_plugin", Context.MODE_PRIVATE)
    fun installed() = prefs.getInt("version", 0) > 0
    fun install() { prefs.edit().putInt("version", 1).apply() }
    fun delete() { prefs.edit().clear().apply(); runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) } }
    fun saveDraft(draft: ReviewDraft) { prefs.edit().putString("draft", Json.encodeToString(draft)).apply() }
    fun draft(): ReviewDraft = runCatching { Json.decodeFromString<ReviewDraft>(prefs.getString("draft", "")!!) }.getOrDefault(ReviewDraft())
    fun connection(): ReviewConnection = runCatching {
        val payload = Base64.decode(prefs.getString("connection", null) ?: return ReviewConnection(), Base64.NO_WRAP)
        require(payload.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12))) }
        Json.decodeFromString<ReviewConnection>(cipher.doFinal(payload.copyOfRange(12, payload.size)).toString(Charsets.UTF_8))
    }.getOrDefault(ReviewConnection())
    fun saveConnection(config: ReviewConnection) {
        config.validate()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.doFinal(Json.encodeToString(config).toByteArray())
        prefs.edit().putString("connection", Base64.encodeToString(cipher.iv + bytes, Base64.NO_WRAP)).apply()
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            generateKey()
        }
    }
    private companion object { const val ALIAS = "qztc_review_model_v1" }
}
