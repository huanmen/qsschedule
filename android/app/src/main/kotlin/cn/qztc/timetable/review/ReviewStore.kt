package cn.qztc.timetable.review

import android.content.Context
import cn.qztc.timetable.plugin.SecretStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI

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
    private val secrets = SecretStore(ALIAS)
    fun installed() = prefs.getInt("version", 0) > 0
    fun install() { prefs.edit().putInt("version", 1).apply() }
    fun delete() { prefs.edit().clear().apply(); runCatching { secrets.delete() } }
    fun saveDraft(draft: ReviewDraft) { prefs.edit().putString("draft", Json.encodeToString(draft)).apply() }
    fun draft(): ReviewDraft = runCatching { Json.decodeFromString<ReviewDraft>(prefs.getString("draft", "")!!) }.getOrDefault(ReviewDraft())
    fun connection(): ReviewConnection = runCatching {
        Json.decodeFromString<ReviewConnection>(secrets.decrypt(prefs.getString("connection", null) ?: return ReviewConnection()))
    }.getOrDefault(ReviewConnection())
    fun saveConnection(config: ReviewConnection) {
        config.validate()
        prefs.edit().putString("connection", secrets.encrypt(Json.encodeToString(config))).apply()
    }
    private companion object { const val ALIAS = "qztc_review_model_v1" }
}
