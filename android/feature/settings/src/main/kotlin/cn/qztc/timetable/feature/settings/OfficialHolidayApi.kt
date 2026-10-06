package cn.qztc.timetable.feature.settings

import android.content.Context
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

internal object OfficialHolidayApi {
    private const val ENDPOINT = "https://web.huanmen.com.cn/qztc/ditu/api/calendar/holidays"
    fun load(context: Context, years: List<Int>, semesterId: String): List<AcademicDay> =
        years.flatMap { year -> runCatching { loadYear(context, year, semesterId) }.getOrDefault(emptyList()) }

    fun cached(context: Context, year: Int, semesterId: String): List<AcademicDay> = runCatching {
        val body=context.getSharedPreferences("official-holidays",Context.MODE_PRIVATE).getString("year:$year",null)
        if(body==null) return@runCatching emptyList()
        val array=JSONObject(body).getJSONArray("days")
        (0 until array.length()).map { i -> val d=array.getJSONObject(i);val date=java.time.LocalDate.parse(d.getString("date"))
            AcademicDay("official:$year:$date",semesterId,date,if(d.getString("kind")=="MAKEUP")AcademicDayKind.MAKEUP else AcademicDayKind.HOLIDAY,d.optString("name")) }
    }.getOrDefault(emptyList())

    private fun loadYear(context: Context, year: Int, semesterId: String): List<AcademicDay> = try {
        val cache = context.getSharedPreferences("official-holidays", Context.MODE_PRIVATE)
        val connection = URL("$ENDPOINT?year=$year").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000; connection.readTimeout = 8_000
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            val body = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.also { cache.edit().putString("year:$year", it).apply() }
            } else throw IllegalStateException("年度节假日暂不可用（$year）")
            val root = JSONObject(body)
            root.getJSONArray("days").let { array -> (0 until array.length()).map { index ->
                val item=array.getJSONObject(index)
                AcademicDay("official:$year:${item.getString("date")}",semesterId,java.time.LocalDate.parse(item.getString("date")),
                    if(item.getString("kind")=="MAKEUP") AcademicDayKind.MAKEUP else AcademicDayKind.HOLIDAY,item.optString("name"),useCurrentWeek=item.getString("kind")=="MAKEUP")
            } }
        } finally { connection.disconnect() }
    } catch (error: Exception) {
        throw error
    }
}
