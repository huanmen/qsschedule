package cn.qztc.timetable.feature.settings

import android.content.Context
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import org.json.JSONObject

/** Published leave and makeup dates; an unpublished year is never guessed. */
internal object OfficialHolidayApi {
    private val sources = listOf(
        "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/%d.json",
        "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/%d.json",
    )
    fun load(context: Context, years: List<Int>, semesterId: String): List<AcademicDay> =
        years.distinct().flatMap { year -> loadYear(context, year, semesterId) }

    internal fun parse(body: String, year: Int, semesterId: String): List<AcademicDay> {
        require(body.length <= 200_000)
        val root = JSONObject(body)
        val published = root.has("year")
        if (published) require(root.getInt("year") == year)
        val array = root.getJSONArray("days")
        require(array.length() in 1..100)
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val date = LocalDate.parse(item.getString("date"))
            require(date.year == year)
            val makeup = if (published) !item.getBoolean("isOffDay") else {
                require(item.getString("kind") in setOf("MAKEUP", "HOLIDAY"))
                item.getString("kind") == "MAKEUP"
            }
            AcademicDay("official:$year:$date", semesterId, date,
                if (makeup) AcademicDayKind.MAKEUP else AcademicDayKind.HOLIDAY,
                item.getString("name").also { require(it.isNotBlank() && it.length <= 50) },
                useCurrentWeek = makeup)
        }.also { require(it.map { day -> day.date }.distinct().size == it.size) }
    }
    fun cached(context: Context, year: Int, semesterId: String): List<AcademicDay> = runCatching {
        val body = context.getSharedPreferences("official-holidays", Context.MODE_PRIVATE)
            .getString("year:$year", null) ?: return@runCatching emptyList()
        parse(body, year, semesterId)
    }.getOrDefault(emptyList())

    private fun loadYear(context: Context, year: Int, semesterId: String): List<AcademicDay> {
        require(year in 2000..2100)
        for (source in sources) {
            val days = runCatching {
                val connection = URL(source.format(year)).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 8_000; connection.readTimeout = 8_000
                    connection.useCaches = false
                    require(connection.responseCode in 200..299)
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val chars = CharArray(200_001)
                        var size = 0
                        while (size < chars.size) {
                            val count = reader.read(chars, size, chars.size - size)
                            if (count < 0) break
                            size += count
                        }
                        require(size <= 200_000)
                        String(chars, 0, size)
                    }
                    val parsed = parse(body, year, semesterId)
                    context.getSharedPreferences("official-holidays", Context.MODE_PRIVATE).edit()
                        .putString("year:$year", body).putLong("fetched:$year", System.currentTimeMillis()).apply()
                    parsed
                } finally { connection.disconnect() }
            }.getOrNull()
            if (days != null) return days
        }
        return cached(context, year, semesterId)
    }
}
