package cn.qztc.timetable.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import cn.qztc.timetable.BuildConfig
import java.time.LocalTime
import kotlin.math.roundToInt

internal fun widgetNow(context: Context): LocalTime {
    if (BuildConfig.DEBUG) {
        val preview = context.getSharedPreferences("widget_preview", Context.MODE_PRIVATE)
        if (preview.getBoolean("test_mode", false)) {
            preview.getString("time", null)?.let { value -> runCatching { LocalTime.parse(value) }.getOrNull()?.let { return it } }
        }
    }
    return LocalTime.now()
}

internal data class WidgetPresentation(
    val widthDp: Float,
    val heightDp: Float,
    val visibleRows: Int,
    val firstIndex: Int,
    val rowHeightsDp: List<Float>,
    val compact: Boolean,
)

/** Keep the visible window exact in physical pixels, including the transparent dividers. */
internal fun widgetPresentation(context: Context, id: Int, entries: List<TodayWidgetEntry>, now: LocalTime, glass: Boolean): WidgetPresentation {
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
    val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280)
    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 284)
    // Modern launchers report fractional dp. Rounding those to integers leaves
    // a few pixels of the fourth row visible at the bottom.
    val size = if (android.os.Build.VERSION.SDK_INT >= 31) {
        options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            ?.minByOrNull { kotlin.math.abs(it.width - minWidth) + kotlin.math.abs(it.height - minHeight) }
    } else null
    val width = size?.width ?: minWidth.toFloat()
    val height = size?.height ?: minHeight.toFloat()
    val density = context.resources.displayMetrics.density
    val fontScale = context.resources.configuration.fontScale
    val compact = width < 220
    // The glass list now starts directly below the header; only the list's
    // inter-card divider remains part of the vertical budget.
    val headerAndPaddingPx = if (glass) (6 * density).roundToInt() * 2 + (56 * density).roundToInt()
        else (10 * density).roundToInt() * 2 + (44 * density).roundToInt()
    val gapPx = if (glass) (5 * density).roundToInt() else 0
    val availablePx = ((height * density).roundToInt() - headerAndPaddingPx).coerceAtLeast(1)
    val contentWidth = (width - if (glass) 108 else 93).coerceAtLeast(40f)
    fun textHeight(text: String, size: Float, dpWidth: Float, maxLines: Int): Int {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * density * fontScale
            if (size > 10f) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, (dpWidth * density).roundToInt().coerceAtLeast(1))
            .setIncludePad(false).setMaxLines(maxLines).setAlignment(Layout.Alignment.ALIGN_NORMAL).build().height
    }
    val minimums = entries.map { entry ->
        val reminder = !compact && entry.reminderMessage.isNotBlank()
        val nameWidth = if (reminder) (contentWidth - 6) * .58f else contentWidth.toFloat()
        val noteWidth = (contentWidth - 6) * .42f
        val name = textHeight(entry.courseName, if (glass) 12f else 13f, nameWidth, 6)
        val note = if (reminder) textHeight(entry.reminderMessage, 10f, noteWidth, 6) else 0
        val footer = textHeight("教室", 10f, contentWidth.toFloat(), 1)
        val timeColumn = listOf(9f, 12f, 9f).sumOf { textHeight("08:00", it, 48f, 1) } + (density).roundToInt()
        maxOf(maxOf(name, note) + footer + ((if (glass) 11 else 7) * density).roundToInt(),
            timeColumn + ((if (glass) 8 else 0) * density).roundToInt())
    }
    var count = if (height >= 210) 3 else 2
    val proposedFirst = widgetFocusIndex(entries, now, count)
    if (count == 3 && (proposedFirst until proposedFirst + count).any { (minimums.getOrNull(it) ?: 0) > (availablePx - gapPx * 2) / 3 }) count = 2
    val first = widgetFocusIndex(entries, now, count)
    val rowBudget = (availablePx - gapPx * (count - 1)).coerceAtLeast(count)
    val base = rowBudget / count
    val slots = MutableList(count) { base + if (it < rowBudget % count) 1 else 0 }
    // If the two priority courses have unequal text heights, borrow unused space
    // from the shorter one while preserving the total viewport height.
    if (count == 2) {
        val a = minimums.getOrNull(first) ?: 0
        val b = minimums.getOrNull(first + 1) ?: 0
        if (a + b <= rowBudget) {
            slots[0] = slots[0].coerceIn(a, rowBudget - b)
            slots[1] = rowBudget - slots[0]
        }
    }
    return WidgetPresentation(width, height, count, first, entries.indices.map { index ->
        val slot = Math.floorMod(index - first, count)
        slots[slot] / density
    }, compact)
}

internal fun widgetTextLines(context: Context, rowHeight: Float, sizeSp: Float, glass: Boolean): Int {
    val metrics = context.resources.displayMetrics
    val paint = TextPaint().apply { textSize = sizeSp * metrics.scaledDensity }
    val footer = TextPaint().apply { textSize = 10f * metrics.scaledDensity }.fontSpacing
    val available = (rowHeight - if (glass) 11 else 7) * metrics.density - footer
    return (available / paint.fontSpacing).toInt().coerceIn(1, 6)
}

/** Same 07:50–21:10 scale and course-colour segments as the Today page. */
internal fun widgetDayProgress(context: Context, entries: List<TodayWidgetEntry>, now: LocalTime, widthDp: Float): Bitmap {
    val density = context.resources.displayMetrics.density
    val bitmap = Bitmap.createBitmap((widthDp * density).roundToInt().coerceAtLeast(32), (14 * density).roundToInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 5 * density; strokeCap = Paint.Cap.ROUND }
    val padding = 6 * density
    fun x(seconds: Int) = padding + ((seconds - 28200) / 48000f).coerceIn(0f, 1f) * (bitmap.width - padding * 2)
    val y = bitmap.height / 2f
    paint.color = 0xffc1cbd4.toInt()
    canvas.drawLine(padding, y, bitmap.width - padding, y, paint)
    entries.forEach { entry ->
        paint.color = entry.color
        paint.alpha = 217
        canvas.drawLine(x(entry.start.toSecondOfDay()), y, x(entry.end.toSecondOfDay()), y, paint)
    }
    paint.color = 0xff365f8a.toInt()
    canvas.drawCircle(x(now.toSecondOfDay()), y, 5.5f * density, paint)
    paint.color = Color.WHITE
    canvas.drawCircle(x(now.toSecondOfDay()), y, 2 * density, paint)
    return bitmap
}
