package cn.qztc.timetable.education

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import cn.qztc.timetable.integration.qnu.QnuGradeRecord
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

internal data class GradeSnapshot(val records: List<QnuGradeRecord>, val updatedAt: Long)

/** One encrypted snapshot, keyed to the account; never backed up or exported. */
internal class GradeCache(context: Context, namespace: String = "qnu_grade_history") {
    private val preferences = context.applicationContext.getSharedPreferences(namespace, Context.MODE_PRIVATE)

    fun load(account: String): GradeSnapshot? = runCatching {
        val text = preferences.getString("payload", null) ?: return null
        val buffer = ByteBuffer.wrap(Base64.decode(text, Base64.NO_WRAP))
        val size = buffer.get().toInt() and 0xff
        require(size in 12..16 && buffer.remaining() > size)
        val iv = ByteArray(size).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        val json = JSONObject(cipher.doFinal(encrypted).toString(Charsets.UTF_8))
        if (json.getString("account") != accountKey(account) || json.getInt("version") != 1) return null
        val rows = json.getJSONArray("records")
        GradeSnapshot((0 until rows.length()).map { i ->
            val r = rows.getJSONObject(i)
            fun field(name: String) = if (r.isNull(name)) null else r.getString(name)
            QnuGradeRecord(field("code"), r.getString("name"), field("score"), field("gpa"), field("credits"),
                field("assessment"), field("selection"), field("status"), field("year"), field("term"), field("exam"), field("sequence"),
                r.optJSONObject("officialFields")?.let { fields -> fields.keys().asSequence().associateWith { fields.getString(it) } }.orEmpty())
        }, json.getLong("updatedAt"))
    }.getOrNull()

    fun save(account: String, snapshot: GradeSnapshot) {
        val records = JSONArray()
        snapshot.records.forEach { r -> records.put(JSONObject().apply {
            put("name", r.courseName); put("code", r.courseCode); put("score", r.score); put("gpa", r.gpa)
            put("credits", r.credits); put("assessment", r.assessment); put("selection", r.selection); put("status", r.status)
            put("year", r.year); put("term", r.term); put("exam", r.examType); put("sequence", r.courseSequence)
            put("officialFields", JSONObject(r.officialFields))
        }) }
        val json = JSONObject().put("version", 1).put("account", accountKey(account)).put("updatedAt", snapshot.updatedAt).put("records", records)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size).put(cipher.iv.size.toByte()).put(cipher.iv).put(encrypted).array()
        check(preferences.edit().putString("payload", Base64.encodeToString(payload, Base64.NO_WRAP)).commit())
    }

    fun clear() { preferences.edit().clear().apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            generateKey()
        }
    }
    private fun accountKey(account: String): String = MessageDigest.getInstance("SHA-256").digest(account.trim().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private companion object { const val KEY_ALIAS = "qztc_grade_history_v1" }
}
