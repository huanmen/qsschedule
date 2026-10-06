package cn.qztc.timetable

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHostView
import android.content.ComponentName
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import cn.qztc.timetable.widget.*
import java.time.LocalTime

/** Debug-only preview: synthetic rows replace rendered views, never the user's database. */
class WidgetPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("verifyHolidayHeader", false)) {
            runCatching {
                val data = TodayWidgetData(holidayName = "国庆节")
                val host = FrameLayout(this)
                val ordinary = buildTodayWidgetViews(this, 0, data).apply(this, host)
                val glass = buildGlassTodayWidgetViews(this, 0, data).apply(this, host)
                check(ordinary.findViewById<TextView>(R.id.widget_today_status).text == "国庆节")
                check(glass.findViewById<TextView>(R.id.widget_glass_status).text == "国庆节")
                android.util.Log.i("WidgetPreviewQA", "PASS both holiday headers; no preview preferences changed")
            }.onFailure { android.util.Log.e("WidgetPreviewQA", "FAIL holiday headers", it) }
            finish()
            return
        }
        if (intent.getBooleanExtra("checkUpdate", false)) {
            Thread {
                runCatching {
                    kotlinx.coroutines.runBlocking {
                        val manifest = cn.qztc.timetable.update.AppUpdateClient.check(2).getOrThrow()
                        check(manifest != null) { "Expected a newer release" }
                        val apk = cn.qztc.timetable.update.AppUpdateClient.download(this@WidgetPreviewActivity, manifest).getOrThrow()
                        android.util.Log.i("WidgetPreviewQA", "PASS update download version=${manifest.latestVersionCode} bytes=${apk.length()}")
                        check(cn.qztc.timetable.update.AppUpdateClient.check(manifest.latestVersionCode).getOrThrow() == null)
                        android.util.Log.i("WidgetPreviewQA", "PASS latest-version check")
                        if (intent.getBooleanExtra("installUpdate", false)) runOnUiThread { cn.qztc.timetable.update.AppUpdateClient.install(this@WidgetPreviewActivity, apk) }
                    }
                }.onFailure { android.util.Log.e("WidgetPreviewQA", "UPDATE FAILED: ${it.message}") }
                runOnUiThread { finish() }
            }.start()
            return
        }
        val now = LocalTime.parse(intent.getStringExtra("time") ?: "12:20")
        val preview = getSharedPreferences("widget_preview", MODE_PRIVATE)
        preview.edit().clear()
            .putBoolean("test_mode", !intent.getBooleanExtra("restore", false))
            .putString("time", now.toString()).apply()
        val manager = AppWidgetManager.getInstance(this)
        runCatching { verifyBackgroundOpacity() }
            .onSuccess { android.util.Log.i("WidgetPreviewQA", "PASS background opacity 0/50/100 and independent text") }
            .onFailure { android.util.Log.e("WidgetPreviewQA", "FAIL background opacity", it) }
        for ((provider, glass) in listOf(TodayWidgetProvider::class.java to false, GlassTodayWidgetProvider::class.java to true)) {
            manager.getAppWidgetIds(ComponentName(this, provider)).forEach { id ->
                val options = manager.getAppWidgetOptions(id)
                val plan = widgetPresentation(this, id, debugWidgetEntries(), now, glass)
                android.util.Log.i("WidgetPreviewQA", "id=$id glass=$glass options=" +
                    options.keySet().joinToString { key -> "$key=${options.get(key)}" } + " plan=$plan")
                runCatching { verifyCardLayout(id, glass, now) }
                    .onSuccess { android.util.Log.i("WidgetPreviewQA", "PASS measured layout id=$id") }
                    .onFailure { android.util.Log.e("WidgetPreviewQA", "FAIL measured layout id=$id", it) }
            }
        }
        TodayWidgetProvider.requestUpdate(this)
        finish()
    }

    private fun verifyCardLayout(id: Int, glass: Boolean, now: LocalTime) {
        val entries = debugWidgetEntries()
        val plan = widgetPresentation(this, id, entries, now, glass)
        val density = resources.displayMetrics.density
        fun px(dp: Float) = kotlin.math.round(dp * density).toInt()
        val host = AppWidgetHostView(this).apply { setAppWidget(id, AppWidgetManager.getInstance(this@WidgetPreviewActivity).getAppWidgetInfo(id)) }
        val remote = if (glass) buildGlassTodayWidgetViews(this, id, TodayWidgetData(entries), now)
            else buildTodayWidgetViews(this, id, TodayWidgetData(entries), now)
        val root = remote.apply(this, host)
        fun measure(view: android.view.View, width: Int, height: Int) {
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
        }
        val list = root.findViewById<ListView>(if (glass) R.id.widget_glass_course_list else R.id.widget_course_list)
        // The service binds asynchronously in the launcher; measure its populated state here.
        list.visibility = android.view.View.VISIBLE
        root.findViewById<android.view.View>(if (glass) R.id.widget_glass_empty else R.id.widget_empty).visibility = android.view.View.GONE
        measure(root, px(plan.widthDp), px(plan.heightDp))
        var occupied = 0
        for (index in plan.firstIndex until minOf(entries.size, plan.firstIndex + plan.visibleRows)) {
            val entry = entries[index]
            val next = entries.firstOrNull { now < it.end }
            val row = (if (glass) buildGlassCourseRow(this, entry, AppPreferences(), now, entry == next, plan.compact, plan.rowHeightsDp[index])
                else buildWidgetCourseRow(this, entry, AppPreferences(), now, entry == next, entries.getOrNull(index + 1)?.start, plan.rowHeightsDp[index], plan.compact))
                .apply(this, FrameLayout(this)) as android.view.ViewGroup
            measure(row, list.width, row.layoutParams.height)
            occupied += row.height
            val name = row.findViewById<TextView>(if (glass) R.id.widget_glass_course_name else R.id.widget_course_name)
            val note = row.findViewById<TextView>(if (glass) R.id.widget_glass_course_reminder else R.id.widget_course_reminder)
            if (note.visibility == android.view.View.VISIBLE) check(name.right <= note.left) { "Columns overlap" }
            fun inspect(view: android.view.View) {
                if (view.visibility != android.view.View.VISIBLE) return
                if (view is TextView && view.text.isNotEmpty()) {
                    val rect = android.graphics.Rect()
                    view.getDrawingRect(rect)
                    row.offsetDescendantRectToMyCoords(view, rect)
                    check(rect.top >= 0 && rect.bottom <= row.height) { "Clipped text id=${view.id} bounds=$rect row=${row.height}" }
                }
                if (view is android.view.ViewGroup) for (child in 0 until view.childCount) inspect(view.getChildAt(child))
            }
            inspect(row)
        }
        occupied += list.dividerHeight * (plan.visibleRows - 1)
        check(occupied == list.height) { "Rows occupy $occupied px, viewport ${list.height} px" }
    }

    private fun verifyBackgroundOpacity() {
        val entry = debugWidgetEntries()[2]
        for (alpha in listOf(0f, .5f, 1f)) {
            val prefs = AppPreferences(widgetBackgroundAlpha = alpha, glassWidgetBackgroundAlpha = alpha)
            val data = TodayWidgetData(listOf(entry), prefs)
            val ordinary = buildTodayWidgetViews(this, 0, data).apply(this, AppWidgetHostView(this))
            val glass = buildGlassTodayWidgetViews(this, 0, data).apply(this, AppWidgetHostView(this))
            val card = buildGlassCourseRow(this, entry, prefs, LocalTime.NOON, true, false)
                .apply(this, FrameLayout(this))
            val expected = kotlin.math.round(alpha * 255).toInt()
            check(ordinary.findViewById<android.widget.ImageView>(R.id.widget_background).imageAlpha == expected)
            check(glass.findViewById<android.widget.ImageView>(R.id.widget_glass_header_background).imageAlpha == expected)
            check(card.findViewById<android.widget.ImageView>(R.id.widget_glass_course_background).imageAlpha == expected)
            check(card.findViewById<TextView>(R.id.widget_glass_course_name).alpha == 1f)
            check(card.findViewById<TextView>(R.id.widget_glass_course_period).text == "第3节")
            check(card.findViewById<TextView>(R.id.widget_glass_course_start).text == "12:00")
            check(card.findViewById<TextView>(R.id.widget_glass_course_end).text == "13:40")
            val divider = card.findViewById<android.view.View>(R.id.widget_glass_course_divider)
            check((divider.background as android.graphics.drawable.ColorDrawable).color == entry.color)
            val row = buildWidgetCourseRow(this, entry, prefs, LocalTime.NOON, true).apply(this, FrameLayout(this))
            check(row.findViewById<TextView>(R.id.widget_course_period).text == "第3节")
        }
    }

    private fun showSyntheticPreview(entries: List<TodayWidgetEntry>, now: LocalTime) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            background = GradientDrawable().apply {
                setColor(Color.rgb(245, 247, 250))
                cornerRadius = 20f
            }
        }
        root.addView(TextView(this).apply {
            text = "今日课表 · 测试数据"
            textSize = 18f
            setTextColor(Color.rgb(32, 52, 75))
        })
        root.addView(TextView(this).apply {
            text = "12月20日 · ${entries.size}节课"
            textSize = 12f
            setTextColor(Color.rgb(99, 121, 142))
            setPadding(0, 6, 0, 12)
        })
        entries.forEachIndexed { index, entry ->
            val row = buildWidgetCourseRow(this, entry, AppPreferences(), now, index == 2,
                entries.getOrNull(index + 1)?.start, 64f).apply(this, root)
            root.addView(row, LinearLayout.LayoutParams(-1, 64).apply {
                if (index > 0) topMargin = 8
            })
        }
        setContentView(root)
    }
}
