package cn.qztc.timetable.education

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal data class HomeworkAttachment(val name: String, val url: String)
internal data class HomeworkItem(
    val id: String, val courseId: String, val taskId: String, val courseName: String, val title: String,
    val publisher: String, val status: String, val deadline: String, val startAt: String,
    val submittedAt: String, val grade: String, val fullMark: String, val content: String,
    val attachments: List<HomeworkAttachment>, val targets: String, val reviewMethod: String,
    val reviewStart: String, val reviewEnd: String, val multiSubmit: Boolean?, val detailUrl: String,
    val detailComplete: Boolean,
    val archiveTerm: String = "", val submissionContent: String = "", val feedback: String = "", val archiveLabel: String = "",
) {
    fun needsSubmission(now: LocalDateTime = LocalDateTime.now()): Boolean =
        status in setOf("pending", "returned") && archiveTerm.isBlank() && deadlineDate?.isBefore(now) != true
    fun refreshedBy(incoming: HomeworkItem): HomeworkItem = if (incoming.detailComplete) incoming else incoming.copy(
        content = content, attachments = attachments, targets = targets, reviewMethod = reviewMethod,
        reviewStart = reviewStart, reviewEnd = reviewEnd, multiSubmit = multiSubmit,
        fullMark = incoming.fullMark.ifBlank { fullMark },
        submittedAt = incoming.submittedAt.ifBlank { submittedAt },
        submissionContent = incoming.submissionContent.ifBlank { submissionContent }, feedback = incoming.feedback.ifBlank { feedback },
        detailComplete = detailComplete,
    )
    val deadlineDate: LocalDateTime? get() = runCatching {
        LocalDateTime.parse(deadline.replace(' ', 'T').take(19), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }.getOrNull()
    fun label(now: LocalDateTime = LocalDateTime.now()): String = when {
        status == "submitted" -> "已提交"
        status == "returned" -> "已退回 · 请修订"
        status == "unknown" -> "状态待确认"
        archiveTerm.isNotBlank() -> "未参与 · 归档"
        deadlineDate?.isBefore(now) == true -> "已截止 · 未提交"
        runCatching { LocalDateTime.parse(startAt.replace(' ', 'T').take(19)) }.getOrNull()?.isAfter(now) == true -> "未开始"
        else -> "待提交"
    }
}

internal object HomeworkPayload {
    fun resourceUrl(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.port in listOf(-1, 443)
    }.getOrDefault(false)
    fun merge(previous: JSONObject?, incoming: JSONObject): JSONObject {
        val rows = linkedMapOf<String, JSONObject>()
        fun add(payload: JSONObject?, fresh: Boolean) {
            val array = payload?.optJSONArray("rows") ?: return
            repeat(array.length()) { index ->
                val item = JSONObject(array.getJSONObject(index).toString())
                val id = item.getString("id")
                val old = rows[id]
                if (fresh && old?.optBoolean("detailComplete") == true && !item.optBoolean("detailComplete")) {
                    listOf("content", "attachments", "targets", "reviewMethod", "reviewStart", "reviewEnd", "multiSubmit", "fullMark", "submittedAt", "submissionContent", "feedback")
                        .forEach { field -> if (old.has(field)) item.put(field, old.get(field)) }
                    item.put("detailComplete", true)
                }
                rows[id] = item
            }
        }
        add(previous, false); add(incoming, true)
        val courses = linkedMapOf<String, JSONObject>()
        listOf(previous, incoming).forEach { payload -> payload?.optJSONArray("courses")?.let { array ->
            repeat(array.length()) { index -> val course = array.getJSONObject(index); courses[course.optString("id")] = course }
        } }
        return JSONObject(incoming.toString()).put("rows", JSONArray(rows.values.toList()))
            .put("courses", JSONArray(courses.values.toList())).also { read(it) }
    }

    fun trustedUrl(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme == "https" && uri.host == "eol.qztc.edu.cn" && uri.userInfo == null && uri.port in listOf(-1, 443)
    }.getOrDefault(false)

    fun read(root: JSONObject): List<HomeworkItem> {
        val rows = root.getJSONArray("rows")
        require(rows.length() <= 10_000)
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            fun field(name: String, max: Int = 300): String = row.optString(name).takeUnless { it == "null" }.orEmpty().take(max)
            val course = field("courseId"); val task = field("taskId")
            require(course.matches(Regex("\\d+")) && task.matches(Regex("\\d+")))
            val title = field("title"); require(title.isNotBlank())
            val url = field("detailUrl", 1000); require(trustedUrl(url))
            val attachments = row.optJSONArray("attachments") ?: JSONArray()
            require(attachments.length() <= 30)
            val archive = field("archiveTerm"); require(archive.isEmpty() || archive.matches(Regex("\\d+")))
            HomeworkItem("$course:$task" + if (archive.isEmpty()) "" else ":archive:$archive", course, task, field("courseName"), title,
                field("publisher"), field("status").takeIf { it in setOf("pending", "submitted", "returned", "unknown") } ?: "unknown",
                field("deadline"), field("startAt"), field("submittedAt"), field("grade"), field("fullMark"),
                field("content", 100_000), (0 until attachments.length()).mapNotNull { j ->
                    val item = attachments.getJSONObject(j); val link = item.optString("url")
                    if (!resourceUrl(link)) null else HomeworkAttachment(item.optString("name").take(200), link)
                }, field("targets", 3000), field("reviewMethod"), field("reviewStart"), field("reviewEnd"),
                if (row.isNull("multiSubmit")) null else row.optBoolean("multiSubmit"), url,
                row.optBoolean("detailComplete"), archive, field("submissionContent", 100_000), field("feedback", 10_000), field("archiveLabel"))
        }.also { require(it.map { item -> item.id }.distinct().size == it.size) }
    }
}

/** Private, account-scoped results only. Authentication cookies never enter this cache. */
internal class HomeworkStore(context: Context, username: String) {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences("homework-results", Context.MODE_PRIVATE)
    val key = MessageDigest.getInstance("SHA-256").digest(username.trim().toByteArray())
        .joinToString("") { "%02x".format(it) }
    fun snapshot(): JSONObject? = runCatching { preferences.getString(key, null)?.let(::JSONObject) }.getOrNull()
    fun save(payload: JSONObject) {
        HomeworkPayload.read(payload)
        preferences.edit().putString(key, JSONObject(payload.toString()).put("savedAt", System.currentTimeMillis()).toString()).apply()
        val items = HomeworkPayload.read(payload).associateBy { homeworkCalendarId(key, it.id) }
        val agenda = AcademicAgendaStore(appContext)
        agenda.save(agenda.read().map { entry -> items[entry.id]?.let { item ->
            entry.copy(date = item.deadlineDate?.toLocalDate() ?: entry.date, time = item.deadlineDate?.toLocalTime() ?: entry.time,
                stage = "作业截止 · ${item.label()}")
        } ?: entry })
        HomeworkReminderScheduler.activate(appContext, key)
    }
    fun mergeAndSave(payload: JSONObject): JSONObject = HomeworkPayload.merge(snapshot(), payload).also(::save)
}
