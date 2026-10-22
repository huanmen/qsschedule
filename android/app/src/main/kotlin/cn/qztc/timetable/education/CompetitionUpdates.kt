package cn.qztc.timetable.education

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import cn.qztc.timetable.MainActivity
import cn.qztc.timetable.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit

internal object CompetitionUpdates {
    private const val WORK = "followed-competition-updates"
    fun pending(context: Context): Set<String> = context.getSharedPreferences(WORK, 0).getStringSet("pending", emptySet()).orEmpty().toSet()
    @Synchronized fun addPending(context: Context, names: Set<String>) {
        context.getSharedPreferences(WORK, 0).edit().putStringSet("pending", pending(context) + names).apply()
    }
    @Synchronized fun consume(context: Context, names: Set<String>) {
        context.getSharedPreferences(WORK, 0).edit().putStringSet("pending", pending(context) - names).apply()
    }
    fun follow(context: Context, names: JSONArray) {
        context.getSharedPreferences(WORK, Context.MODE_PRIVATE).edit().putString("names", names.toString()).apply()
        val manager = WorkManager.getInstance(context)
        if (cn.qztc.timetable.plugin.AcademicRecordsPlugin.installedVersion(context) == 0 || names.length() == 0) { manager.cancelUniqueWork(WORK); return }
        manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CompetitionUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        manager.enqueueUniqueWork("$WORK-initial", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CompetitionUpdateWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
}

class CompetitionUpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (cn.qztc.timetable.plugin.AcademicRecordsPlugin.installedVersion(applicationContext) == 0) return@withContext Result.success()
        val prefs = applicationContext.getSharedPreferences("followed-competition-updates", Context.MODE_PRIVATE)
        try {
            val names = JSONArray(prefs.getString("names", "[]"))
            val followed = (0 until names.length()).map { names.getString(it).replace(Regex("\\s+"), "") }.toSet()
            if (followed.isEmpty()) return@withContext Result.success()
            val connection = URL("https://huanmen.com.cn/qsschedule/api/competition-catalog").openConnection().apply { connectTimeout = 15000; readTimeout = 20000 }
            val feed = connection.getInputStream().use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val size = stream.read(buffer)
                    if (size < 0) break
                    require(output.size() + size <= 4_000_000)
                    output.write(buffer, 0, size)
                }
                JSONObject(output.toString("UTF-8"))
            }
            require(feed.getInt("schemaVersion") == 2)
            val previous = JSONObject(prefs.getString("stages", "{}") ?: "{}")
            val latest = JSONObject()
            val changed = mutableListOf<String>()
            val items = feed.getJSONArray("items")
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val name = item.getString("name")
                if (name.replace(Regex("\\s+"), "") !in followed) continue
                if (item.optBoolean("stale")) continue
                val id = item.getString("id")
                val stages = JSONObject().put("nodes", item.optJSONArray("calendarNodes") ?: JSONArray())
                    .put("location", item.optJSONObject("sections")?.optString("比赛地点").orEmpty()).toString()
                latest.put(id, stages)
                if (previous.has(id) && previous.getString(id) != stages) changed.add(name)
            }
            if (changed.isNotEmpty()) {
                CompetitionUpdates.addPending(applicationContext, changed.toSet())
            }
            for (id in previous.keys()) if (!latest.has(id)) latest.put(id, previous.get(id))
            prefs.edit().putString("stages", latest.toString()).apply()
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
