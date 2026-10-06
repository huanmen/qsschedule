package cn.qztc.timetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import cn.qztc.timetable.MainActivity
import cn.qztc.timetable.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class GlassTodayWidgetProvider : AppWidgetProvider() {
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        refresh(context, intArrayOf(appWidgetId))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == TodayWidgetProvider.ACTION_REFRESH || intent.action in TIME_ACTIONS) {
            refresh(context, AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)))
        }
    }

    override fun onDisabled(context: Context) = Unit

    private fun refresh(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val data = loadTodayWidgetData(context, ids.first())
                val manager = AppWidgetManager.getInstance(context)
                val now = widgetNow(context)
                ids.forEach { id -> manager.updateAppWidget(id, buildGlassTodayWidgetViews(context, id, data, now)) }
                // Refresh the bound collection after schedule or size changes.
                manager.notifyAppWidgetViewDataChanged(ids, if (data.entries.isEmpty()) R.id.widget_course_list else R.id.widget_glass_course_list)
                if (data.entries.isNotEmpty()) {
                    delay(700)
                    ids.forEach { id -> manager.partiallyUpdateAppWidget(id, glassWidgetFocusViews(context, data.entries, now, id)) }
                    delay(500)
                    ids.forEach { id ->
                        val first = widgetPresentation(context, id, data.entries, now, true).firstIndex
                        manager.partiallyUpdateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_today_glass).apply {
                            setScrollPosition(R.id.widget_glass_course_list, first)
                        })
                    }
                    delay(350)
                    ids.forEach { id -> manager.partiallyUpdateAppWidget(id, revealWidgetCollection(context, true)) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val TIME_ACTIONS = setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
        private fun component(context: Context) = ComponentName(context, GlassTodayWidgetProvider::class.java)
    }
}

internal fun buildGlassTodayWidgetViews(
    context: Context,
    appWidgetId: Int,
    data: TodayWidgetData,
    now: LocalTime = LocalTime.now(),
): RemoteViews {
    if (data.entries.isEmpty()) {
        // A single surface, exactly like the first widget. Keep this widget's opacity preference.
        return buildTodayWidgetViews(context, appWidgetId, data.copy(
            preferences = data.preferences.copy(widgetBackgroundAlpha = data.preferences.glassWidgetBackgroundAlpha),
        ), now)
    }
    val views = RemoteViews(context.packageName, R.layout.widget_today_glass)
    views.setImageViewResource(R.id.widget_glass_header_background,
        if (data.entries.isEmpty()) R.drawable.widget_today_glass_header_empty_background
        else R.drawable.widget_today_glass_header_background)
    views.setInt(R.id.widget_glass_header_background, "setImageAlpha", kotlin.math.round(data.preferences.glassWidgetBackgroundAlpha.coerceIn(0f, 1f) * 255).toInt())
    val launch = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
    views.setOnClickPendingIntent(
        R.id.widget_glass_header,
        PendingIntent.getActivity(context, appWidgetId + 200_000, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
    )
    views.setPendingIntentTemplate(
        R.id.widget_glass_course_list,
        PendingIntent.getActivity(context, appWidgetId + 300_000, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE),
    )
    val next = data.entries.firstOrNull { now < it.end }
    views.setTextViewText(R.id.widget_glass_status, when {
        data.holidayName != null -> data.holidayName
        data.entries.isEmpty() -> "无课日"
        next == null -> "今日已结束"
        now >= next.start -> "上课中"
        else -> "下一节 ${next.start}"
    })
    views.setTextViewText(R.id.widget_glass_date, data.date.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)) +
        if (data.entries.isNotEmpty()) " · ${data.entries.size}节课" else "")
    data.preferences.textPrimaryColor?.let { views.setTextColor(R.id.widget_glass_title, it) }
    data.preferences.textTertiaryColor?.let { views.setTextColor(R.id.widget_glass_date, it) }
    views.setEmptyView(R.id.widget_glass_course_list, R.id.widget_glass_empty)
    views.setRemoteAdapter(R.id.widget_glass_course_list, Intent(context, GlassTodayWidgetService::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .setData(Uri.parse("qztc-glass-widget://today/$appWidgetId")))
    val presentation = widgetPresentation(context, appWidgetId, data.entries, now, true)
    views.configureGlassEmptyCards(presentation.visibleRows, data.preferences)
    views.setOnClickPendingIntent(R.id.widget_glass_empty,
        PendingIntent.getActivity(context, appWidgetId + 200_000, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    views.setImageViewBitmap(R.id.widget_glass_progress, widgetDayProgress(context, data.entries, now, presentation.widthDp - 32))
    if (data.entries.isNotEmpty()) views.setScrollPosition(R.id.widget_glass_course_list, 0)
    views.showWidgetFocusPreview(context, data, now, presentation, true,
        PendingIntent.getActivity(context, appWidgetId + 200_000, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    return views
}

/** Preserve empty slots without relying on API 31 layout-height setters. */
internal fun RemoteViews.configureGlassEmptyCards(visibleRows: Int, prefs: cn.qztc.timetable.AppPreferences) {
    val threeCards = visibleRows == 3
    setViewVisibility(R.id.widget_glass_empty_card_3, if (threeCards) android.view.View.VISIBLE else android.view.View.GONE)
    setViewVisibility(R.id.widget_glass_empty_gap_3, if (threeCards) android.view.View.VISIBLE else android.view.View.GONE)
    setTextViewText(R.id.widget_glass_empty_message_1, if (threeCards) "" else "今天没有课程")
    setTextViewText(R.id.widget_glass_empty_message_2, if (threeCards) "今天没有课程" else "")
    val ink = prefs.textPrimaryColor ?: 0xff63798e.toInt()
    setTextColor(R.id.widget_glass_empty_message_1, ink)
    setTextColor(R.id.widget_glass_empty_message_2, ink)
    val alpha = kotlin.math.round(prefs.glassWidgetBackgroundAlpha.coerceIn(0f, 1f) * 255).toInt()
    listOf(R.id.widget_glass_empty_background_1, R.id.widget_glass_empty_background_2, R.id.widget_glass_empty_background_3).forEach {
        setInt(it, "setImageAlpha", alpha)
    }
}

internal fun buildGlassCourseRow(
    context: Context,
    entry: TodayWidgetEntry,
    prefs: cn.qztc.timetable.AppPreferences,
    now: LocalTime,
    isNext: Boolean,
    compact: Boolean,
    rowHeight: Float = 64f,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_today_glass_course)
    views.setInt(R.id.widget_glass_course_background, "setImageAlpha", kotlin.math.round(prefs.glassWidgetBackgroundAlpha.coerceIn(0f, 1f) * 255).toInt())
    val ongoing = now >= entry.start && now < entry.end
    val status = when {
        ongoing -> "上课中"
        isNext -> "下一节"
        else -> ""
    }
    val reminder = if (!compact && entry.reminderMessage.isNotBlank()) {
        entry.reminderMessage
    } else ""
    views.setTextViewText(R.id.widget_glass_course_period, "第${entry.period}节")
    views.setTextViewText(R.id.widget_glass_course_start, entry.start.toString())
    views.setTextViewText(R.id.widget_glass_course_end, entry.end.toString())
    views.setTextViewText(R.id.widget_glass_course_name, entry.courseName)
    views.setTextViewText(R.id.widget_glass_course_room, entry.room.ifBlank { "教室待定" })
    views.setTextViewText(R.id.widget_glass_course_reminder, reminder)
    views.setViewVisibility(R.id.widget_glass_course_reminder,
        if (reminder.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE)
    views.setTextViewText(R.id.widget_glass_course_badge, status)
    views.setViewVisibility(R.id.widget_glass_course_badge, if (status.isBlank()) android.view.View.GONE else android.view.View.VISIBLE)
    views.setInt(R.id.widget_glass_course_name, "setMaxLines", widgetTextLines(context, rowHeight, 12f, true))
    views.setInt(R.id.widget_glass_course_reminder, "setMaxLines", widgetTextLines(context, rowHeight, 10f, true))
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        views.setViewLayoutHeight(R.id.widget_glass_course_row, rowHeight, android.util.TypedValue.COMPLEX_UNIT_DIP)
    }
    val ink = if (ongoing || isNext) entry.color else prefs.textPrimaryColor ?: 0xff20344b.toInt()
    views.setInt(R.id.widget_glass_course_divider, "setBackgroundColor", entry.color)
    views.setTextColor(R.id.widget_glass_course_period, ink)
    views.setTextColor(R.id.widget_glass_course_start, ink)
    views.setTextColor(R.id.widget_glass_course_end, if (ongoing || isNext) ink else prefs.textTertiaryColor ?: 0xff63798e.toInt())
    views.setTextColor(R.id.widget_glass_course_name, ink)
    views.setOnClickFillInIntent(R.id.widget_glass_course_row, Intent())
    return views
}

private fun glassWidgetFocusViews(context: Context, entries: List<TodayWidgetEntry>, now: LocalTime, widgetId: Int) =
    RemoteViews(context.packageName, R.layout.widget_today_glass).apply {
        val plan = widgetPresentation(context, widgetId, entries, now, true)
        setScrollPosition(R.id.widget_glass_course_list, (plan.firstIndex + plan.visibleRows - 1).coerceAtMost(entries.lastIndex).coerceAtLeast(0))
    }

class GlassTodayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = GlassTodayCoursesFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
}

private class GlassTodayCoursesFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private var data = TodayWidgetData()
    private var now = LocalTime.now()
    override fun onCreate() = Unit
    override fun onDestroy() = Unit
    override fun onDataSetChanged() {
        runBlocking(Dispatchers.IO) { runCatching { loadTodayWidgetData(context, widgetId) }.getOrNull()?.let { data = it } }
        now = widgetNow(context)
    }
    override fun getCount() = data.entries.size
    override fun getViewAt(position: Int): RemoteViews? = data.entries.getOrNull(position)?.let {
        val presentation = widgetPresentation(context, widgetId, data.entries, now, true)
        buildGlassCourseRow(context, it, data.preferences, now, data.entries.firstOrNull { item -> now < item.end } == it, presentation.compact, presentation.rowHeightsDp[position])
    }
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = false
}
