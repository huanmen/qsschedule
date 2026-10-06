package cn.qztc.timetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import cn.qztc.timetable.AppPreferences
import cn.qztc.timetable.AppPreferencesStore
import cn.qztc.timetable.MainActivity
import cn.qztc.timetable.R
import cn.qztc.timetable.qztcDataStore
import cn.qztc.timetable.core.ui.theme.CoursePalette
import cn.qztc.timetable.feature.timetable.automaticCourseColorToken
import androidx.compose.ui.graphics.toArgb
import cn.qztc.timetable.core.data.db.TimetableDatabase
import cn.qztc.timetable.core.data.repository.TimetableRepository
import cn.qztc.timetable.core.domain.calendar.AcademicCalendar
import cn.qztc.timetable.core.domain.model.PeriodSchemeFactory
import cn.qztc.timetable.core.domain.model.Semester
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class TodayWidgetProvider : AppWidgetProvider() {
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        refresh(context, intArrayOf(appWidgetId))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH || intent.action in TIME_ACTIONS) {
            val manager = AppWidgetManager.getInstance(context)
            refresh(context, manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java)))
        }
    }

    override fun onDisabled(context: Context) { WidgetClock.cancel(context) }

    private fun refresh(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val data = loadTodayWidgetData(context, ids.firstOrNull() ?: 0)
                val manager = AppWidgetManager.getInstance(context)
                val now = widgetNow(context)
                ids.forEach { id ->
                    // Re-centre on each clock refresh, while leaving free scrolling between ticks.
                    manager.updateAppWidget(id, buildTodayWidgetViews(context, id, data, now))
                }
                WidgetClock.schedule(context)
                if (Build.VERSION.SDK_INT < 31) {
                    manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_course_list)
                }
                // Collection binding in the launcher completes after the initial RemoteViews update.
                if (data.entries.isNotEmpty()) {
                    delay(800)
                    ids.forEach { id -> manager.partiallyUpdateAppWidget(id, widgetFocusViews(context, data.entries, now, id)) }
                    // Android's position scroller overshoots off-screen targets by
                    // a fading-edge distance. Once visible, scrollToVisible can
                    // align the clipped first row exactly to the top.
                    delay(500)
                    ids.forEach { id ->
                        val first = widgetPresentation(context, id, data.entries, now, false).firstIndex
                        manager.partiallyUpdateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_today).apply {
                            setScrollPosition(R.id.widget_course_list, first)
                        })
                    }
                    delay(350)
                    ids.forEach { id -> manager.partiallyUpdateAppWidget(id, revealWidgetCollection(context, false)) }
                }
            } catch (error: Exception) {
                // Preserve the last rendered timetable on a transient read failure.
                android.util.Log.e("TodayWidget", "Widget refresh failed: ${error.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "cn.qztc.timetable.widget.REFRESH_TODAY"
        private val TIME_ACTIONS = setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)

        fun requestUpdate(context: Context) {
            context.sendBroadcast(Intent(context, TodayWidgetProvider::class.java).setAction(ACTION_REFRESH))
            context.sendBroadcast(Intent(context, GlassTodayWidgetProvider::class.java).setAction(ACTION_REFRESH))
        }
    }
}

internal fun widgetFocusViews(context: Context, entries: List<TodayWidgetEntry>, now: LocalTime, widgetId: Int = 0) =
    RemoteViews(context.packageName, R.layout.widget_today).apply {
        val plan = widgetPresentation(context, widgetId, entries, now, false)
        setScrollPosition(R.id.widget_course_list, (plan.firstIndex + plan.visibleRows - 1).coerceAtMost(entries.lastIndex).coerceAtLeast(0))
    }

internal data class TodayWidgetEntry(
    val courseName: String,
    val start: LocalTime,
    val end: LocalTime,
    val room: String,
    val period: Int,
    val color: Int = Color.rgb(46, 139, 111),
    val reminderMessage: String = "",
    val teacher: String = "",
)

internal data class TodayWidgetData(
    val entries: List<TodayWidgetEntry> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
    val date: LocalDate = LocalDate.now(),
    val compact: Boolean = false,
    val holidayName: String? = null,
)

internal suspend fun loadTodayWidgetData(context: Context, widgetId: Int = 0): TodayWidgetData {
    val previewPrefs = context.getSharedPreferences("widget_preview", Context.MODE_PRIVATE)
    if (cn.qztc.timetable.BuildConfig.DEBUG && previewPrefs.getBoolean("test_mode", false)) {
        return TodayWidgetData(
            entries = widgetTestEntries(),
            preferences = AppPreferencesStore(context.qztcDataStore).preferences.first(),
            date = LocalDate.now(),
            compact = widgetId > 0 && widgetIsCompact(context, widgetId),
        )
    }
    val prefs = AppPreferencesStore(context.qztcDataStore).preferences.first()
    val today = LocalDate.now()
    val database = TimetableDatabase.build(context)
    try {
        val repository = TimetableRepository(database)
        val semester = Semester.forReminders(repository.semesters(), today)
            ?: return TodayWidgetData(preferences = prefs, date = today,
                compact = widgetId > 0 && widgetIsCompact(context, widgetId),
                holidayName = widgetHolidayName(AcademicCalendar.EMPTY, today))
        val periods = (repository.periodScheme(semester.id)?.periods
            ?: PeriodSchemeFactory.forSemester(semester.id).periods).associateBy { it.no }
        val state = repository.state(semester.id)
        val day = widgetDaySchedule(semester, state, AcademicCalendar(repository.academicDays(semester.id)), today)
        val courses = state.courses.associateBy { it.id }
        val palette = state.courses.sortedBy { it.id }.mapIndexed { index, course -> course.id to automaticCourseColorToken(index) }.toMap()
        val entries = day.occurrences.asSequence()
            .mapNotNull { occurrence ->
                val course = courses[occurrence.courseId] ?: return@mapNotNull null
                val first = periods[occurrence.periodStart] ?: return@mapNotNull null
                val last = periods[occurrence.periodEnd] ?: first
                val token = course.color ?: palette.getValue(course.id)
                val color = CoursePalette.getOrNull(token)?.bar?.toArgb() ?: token
                TodayWidgetEntry(
                    course.name, first.startTime, last.endTime, occurrence.room.orEmpty(), occurrence.periodStart, color,
                    prefs.courseReminderMessages[course.id].orEmpty(), occurrence.teacher.orEmpty(),
                )
            }
            .sortedWith(compareBy(TodayWidgetEntry::period, TodayWidgetEntry::courseName))
            .toList()
        return TodayWidgetData(entries, prefs, today, widgetId > 0 && widgetIsCompact(context, widgetId), day.holidayName)
    } finally {
        database.close()
    }
}

/** Synthetic rows used only when the debug preview explicitly enables test mode. */
internal fun widgetTestEntries(): List<TodayWidgetEntry> =
    listOf(8, 10, 12, 14, 16, 19).mapIndexed { index, hour ->
        TodayWidgetEntry(
            "测试课程${index + 1}" + if (index == 4) " · 这是一门用于检查长名称与提醒分栏的课程" else "",
            LocalTime.of(hour, 0), LocalTime.of(hour + 1, 40),
            if (index == 3) "" else "测试教室 ${index + 1}01", index + 1,
            listOf(0xff2e8b6f.toInt(), 0xff3a7cb8.toInt(), 0xffc08a2d.toInt(),
                0xff7a6bc6.toInt(), 0xff5273b8.toInt(), 0xffc96a57.toInt())[index],
            when (index) {
                2 -> "带教材和实验记录"
                3 -> "课前交作业"
                4 -> "带计算器、实验报告及教材，课前提交上周作业"
                else -> ""
            },
            "测试教师${index + 1}",
        )
    }

internal fun buildTodayWidgetViews(
    context: Context,
    appWidgetId: Int,
    data: TodayWidgetData,
    now: LocalTime = LocalTime.now(),
    focusCurrent: Boolean = true,
): RemoteViews {
    val entries = data.entries
    val views = RemoteViews(context.packageName, R.layout.widget_today)
    views.setInt(R.id.widget_background, "setImageAlpha", kotlin.math.round(data.preferences.widgetBackgroundAlpha.coerceIn(0f, 1f) * 255).toInt())
    val launch = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
    views.setOnClickPendingIntent(
        R.id.widget_header,
        PendingIntent.getActivity(context, appWidgetId, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
    )
    // Collection items require a mutable template to merge their fill-in intents.
    views.setPendingIntentTemplate(
        R.id.widget_course_list,
        PendingIntent.getActivity(context, appWidgetId + 100_000, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE),
    )
    val next = entries.firstOrNull { now < it.end }
    views.setTextViewText(R.id.widget_today_status, when {
        data.holidayName != null -> data.holidayName
        entries.isEmpty() -> "无课日"
        next == null -> "今日已结束"
        now >= next.start -> "上课中"
        else -> "下一节 ${next.start}"
    })
    views.setTextViewText(R.id.widget_today_date, data.date.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)) +
        if (entries.isNotEmpty()) " · ${entries.size}节课" else "")
    data.preferences.textPrimaryColor?.let { views.setTextColor(R.id.widget_today_title, it) }
    data.preferences.textTertiaryColor?.let { views.setTextColor(R.id.widget_today_date, it) }
    views.setEmptyView(R.id.widget_course_list, R.id.widget_empty)
    val presentation = widgetPresentation(context, appWidgetId, entries, now, false)
    if (Build.VERSION.SDK_INT >= 31) {
        val items = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1).setHasStableIds(false)
        entries.forEachIndexed { index, entry ->
            items.addItem(index.toLong(), buildWidgetCourseRow(context, entry, data.preferences, now, entry == next,
                entries.getOrNull(index + 1)?.start, presentation.rowHeightsDp[index], presentation.compact))
        }
        views.setRemoteAdapter(R.id.widget_course_list, items.build())
    } else {
        val service = Intent(context, TodayWidgetService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .setData(Uri.parse("qztc-widget://today/$appWidgetId"))
        views.setRemoteAdapter(R.id.widget_course_list, service)
    }
    // Reset a retained partial-row offset before the deferred focus places the
    // last visible course against the viewport bottom.
    if (focusCurrent && entries.isNotEmpty()) views.setScrollPosition(R.id.widget_course_list, 0)
    if (focusCurrent) views.showWidgetFocusPreview(context, data, now, presentation, false,
        PendingIntent.getActivity(context, appWidgetId, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    return views
}

internal fun widgetRowHeight(context: Context, id: Int): Float {
    if (id <= 0) return 64f
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220)
    // Keep the default widget at three visible rows, while the compact
    // resize target shows two. The collection retains all courses and the
    // ListView clips the remainder to the available height.
    val visibleRows = if (height >= 210) 3f else 2f
    return ((height - 64f) / visibleRows).coerceIn(52f, 78f)
}

private fun widgetIsCompact(context: Context, widgetId: Int): Boolean {
    if (widgetId <= 0) return false
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220)
    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220)
    return width < 220
}

internal fun buildWidgetCourseRow(
    context: Context,
    entry: TodayWidgetEntry,
    prefs: AppPreferences,
    now: LocalTime,
    isNext: Boolean,
    nextStart: LocalTime? = null,
    rowHeight: Float = 64f,
    compact: Boolean = false,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_today_course)
    val ongoing = now >= entry.start && now < entry.end
    val completed = now >= entry.end
    val accent = entry.color
    val activeInk = accent
    val ink = prefs.textPrimaryColor ?: Color.rgb(32, 52, 75)
    val secondary = prefs.textSecondaryColor ?: Color.rgb(99, 121, 142)
    val muted = prefs.textTertiaryColor ?: Color.rgb(124, 140, 157)
    fun activeText(value: String): CharSequence = if (ongoing) SpannableString(value).apply {
        setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    } else value
    views.setTextViewText(R.id.widget_course_period, "第${entry.period}节")
    views.setTextViewText(R.id.widget_course_start, activeText(entry.start.toString()))
    views.setTextViewText(R.id.widget_course_end, entry.end.toString())
    views.setTextViewText(R.id.widget_course_name, activeText(entry.courseName))
    views.setTextViewText(R.id.widget_course_room, entry.room.ifBlank { "教室待定" })
    val showReminder = !compact && entry.reminderMessage.isNotBlank()
    views.setTextViewText(R.id.widget_course_reminder, if (showReminder) entry.reminderMessage else "")
    views.setViewVisibility(R.id.widget_course_reminder, if (showReminder) View.VISIBLE else View.GONE)
    views.setTextColor(R.id.widget_course_period, if (ongoing || isNext) activeInk else if (completed) muted else secondary)
    views.setTextColor(R.id.widget_course_start, if (ongoing || isNext) activeInk else if (completed) muted else ink)
    views.setTextColor(R.id.widget_course_end, if (completed) muted else secondary)
    views.setTextColor(R.id.widget_course_name, if (ongoing || isNext) activeInk else if (completed) muted else ink)
    views.setTextColor(R.id.widget_course_room, if (ongoing || isNext) activeInk else if (completed) muted else secondary)
    views.setViewVisibility(R.id.widget_course_status, if (ongoing || isNext) View.VISIBLE else View.GONE)
    views.setTextViewText(R.id.widget_course_status, if (ongoing) "● 上课中" else "下一节")
    views.setTextColor(R.id.widget_course_status, activeInk)
    val actualRowHeight = rowHeight
    views.setInt(R.id.widget_course_name, "setMaxLines", widgetTextLines(context, rowHeight, 13f, false))
    views.setInt(R.id.widget_course_reminder, "setMaxLines", widgetTextLines(context, rowHeight, 10f, false))
    if (Build.VERSION.SDK_INT >= 31) {
        views.setViewLayoutHeight(R.id.widget_course_row, actualRowHeight, android.util.TypedValue.COMPLEX_UNIT_DIP)
    }
    views.setImageViewBitmap(R.id.widget_course_timeline, timelineBitmap(context, entry, nextStart, now, actualRowHeight))
    views.setOnClickFillInIntent(R.id.widget_course_row, Intent())
    return views
}

private fun timelineBitmap(context: Context, entry: TodayWidgetEntry, nextStart: LocalTime?, now: LocalTime, height: Float): Bitmap {
    val density = context.resources.displayMetrics.density
    val bitmap = Bitmap.createBitmap((14 * density).toInt().coerceAtLeast(1), (height * density).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val x = bitmap.width / 2f
    val gap = if (nextStart != null && nextStart > entry.end) 8 * density else 0f
    val end = bitmap.height - gap
    paint.strokeWidth = 3 * density
    paint.color = if (now >= entry.end) Color.argb(95, Color.red(entry.color), Color.green(entry.color), Color.blue(entry.color)) else entry.color
    canvas.drawLine(x, 0f, x, end, paint)
    paint.color = Color.rgb(188, 197, 205)
    canvas.drawLine(x, end, x, bitmap.height.toFloat(), paint)
    val position = widgetTimelinePosition(entry.start, entry.end, nextStart, now)
    val point = position.courseFraction?.let { it * end } ?: position.breakFraction?.let { end + it * gap }
    if (point != null) {
        val y = point.coerceIn(4 * density, bitmap.height - 4 * density)
        paint.color = if (position.courseFraction != null) entry.color else Color.rgb(140, 153, 164)
        canvas.drawCircle(x, y, 5 * density, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(x, y, 3.5f * density, paint)
    }
    return bitmap
}

/** Android 8–11 collection adapter; Android 12+ sends the complete collection directly. */
class TodayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = TodayCoursesFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
}

private class TodayCoursesFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private var data = TodayWidgetData()
    private var now = LocalTime.now()
    override fun onCreate() = Unit
    override fun onDestroy() = Unit
    override fun onDataSetChanged() {
        val updated = runBlocking(Dispatchers.IO) { runCatching { loadTodayWidgetData(context, widgetId) }.getOrNull() }
        if (updated != null) data = updated
        now = widgetNow(context)
    }
    override fun getCount() = data.entries.size
    override fun getViewAt(position: Int): RemoteViews? {
        val entry = data.entries.getOrNull(position) ?: return null
        val next = data.entries.firstOrNull { now < it.end }
        val presentation = widgetPresentation(context, widgetId, data.entries, now, false)
        return buildWidgetCourseRow(context, entry, data.preferences, now, entry == next, data.entries.getOrNull(position + 1)?.start, presentation.rowHeightsDp[position], presentation.compact)
    }
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = false
}
