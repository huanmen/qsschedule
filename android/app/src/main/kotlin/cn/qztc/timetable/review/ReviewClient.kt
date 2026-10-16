package cn.qztc.timetable.review

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

object ReviewClient {
    @Serializable data class Message(val role: String, val content: String)
    @Serializable private data class Request(val model: String, val messages: List<Message>, val temperature: Double = .8, val max_tokens: Int = 1400, val stream: Boolean = false)
    data class Output(val text: String, val withinRange: Boolean)
    suspend fun generate(draft: ReviewDraft, config: ReviewConnection): Output = generateWith(draft, config, ::request)
    internal suspend fun generateWith(draft: ReviewDraft, config: ReviewConnection, transport: suspend (ReviewConnection, List<Message>) -> String): Output = withContext(Dispatchers.IO) {
        draft.validate(); config.validate()
        val messages = mutableListOf(Message("system", ReviewDraft.SYSTEM), Message("user", draft.prompt()))
        var best = ""
        var distance = Int.MAX_VALUE
        repeat(3) {
            currentCoroutineContext().ensureActive()
            val text = transport(config, messages.toList()).trim()
            require(text.isNotBlank()) { "模型返回了空内容，请重试" }
            if (ReviewText.inRange(text, draft)) return@withContext Output(text, true)
            val count = ReviewText.count(text)
            val gap = if (count < draft.minimum) draft.minimum - count else count - draft.maximum
            if (gap < distance) { best = text; distance = gap }
            messages += Message("assistant", text)
            messages += Message("user", "当前有 ${ReviewText.count(text)} 字，请修改为 ${draft.minimum}–${draft.maximum} 字（含标点、不含空白）。保留已有事实，不重复、不虚构、不截断句子，只输出正文。")
        }
        Output(best, false)
    }
    private fun request(config: ReviewConnection, messages: List<Message>): String =
        requestWith(config, messages) { URL(it).openConnection() as HttpURLConnection }

    internal fun requestWith(config: ReviewConnection, messages: List<Message>, open: (String) -> HttpURLConnection): String {
        config.validate()
        val conn = open(config.requestEndpoint())
        try {
            conn.requestMethod = "POST"; conn.instanceFollowRedirects = false
            conn.connectTimeout = 10_000; conn.readTimeout = 45_000; conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer " + config.apiKey)
            val bytes = Json { encodeDefaults = true }.encodeToString(Request(config.model, messages)).toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size); conn.outputStream.use { it.write(bytes) }
            when (conn.responseCode) {
                401, 403 -> error("模型鉴权失败，请检查密钥和权限")
                429 -> error("模型请求过于频繁或额度不足，请稍后重试")
                !in 200..299 -> error("模型服务暂不可用（HTTP ${conn.responseCode}）")
            }
            val out = java.io.ByteArrayOutputStream()
            conn.inputStream.use { cn.qztc.timetable.update.AppUpdateClient.copyLimited(it, out, 128 * 1024) }
            return parseResponse(out.toString("UTF-8"))
        } finally { conn.disconnect() }
    }
    internal fun parseResponse(body: String): String = runCatching {
        val root = Json.parseToJsonElement(body).jsonObject
        val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content") as? JsonPrimitive
        content?.takeIf { it.isString }?.contentOrNull ?: error("Missing content")
    }.getOrElse { error("模型返回格式不兼容") }
}
