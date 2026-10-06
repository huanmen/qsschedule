package cn.qztc.timetable.widget

import android.app.PendingIntent
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import cn.qztc.timetable.R
import java.time.LocalTime

/** Render the final viewport immediately while the launcher binds/positions its
 * collection. This preserves chronological scrolling without exposing the
 * platform's animated position-scroller and its fading-edge overshoot. */
internal fun RemoteViews.showWidgetFocusPreview(
    context: Context, data: TodayWidgetData, now: LocalTime,
    plan: WidgetPresentation, glass: Boolean, click: PendingIntent,
) {
    val listId = if (glass) R.id.widget_glass_course_list else R.id.widget_course_list
    removeAllViews(R.id.widget_focus_preview)
    if (data.entries.isEmpty()) {
        setViewVisibility(R.id.widget_focus_preview, View.GONE)
        return
    }
    val next = data.entries.firstOrNull { now < it.end }
    for (index in plan.firstIndex until minOf(data.entries.size, plan.firstIndex + plan.visibleRows)) {
        if (glass && index > plan.firstIndex) addView(R.id.widget_focus_preview, RemoteViews(context.packageName, R.layout.widget_focus_gap))
        val entry = data.entries[index]
        val row = if (glass) buildGlassCourseRow(context, entry, data.preferences, now, entry == next, plan.compact, plan.rowHeightsDp[index])
            else buildWidgetCourseRow(context, entry, data.preferences, now, entry == next, data.entries.getOrNull(index + 1)?.start, plan.rowHeightsDp[index], plan.compact)
        addView(R.id.widget_focus_preview, row)
    }
    setOnClickPendingIntent(R.id.widget_focus_preview, click)
    setViewVisibility(listId, View.INVISIBLE)
    setViewVisibility(R.id.widget_focus_preview, View.VISIBLE)
}

internal fun revealWidgetCollection(context: Context, glass: Boolean) =
    RemoteViews(context.packageName, if (glass) R.layout.widget_today_glass else R.layout.widget_today).apply {
        setViewVisibility(if (glass) R.id.widget_glass_course_list else R.id.widget_course_list, View.VISIBLE)
        setViewVisibility(R.id.widget_focus_preview, View.GONE)
        removeAllViews(R.id.widget_focus_preview)
    }
