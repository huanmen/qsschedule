package cn.qztc.timetable

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.widget.configureGlassEmptyCards
import org.junit.Assert.*
import org.junit.Test

class GlassWidgetEmptyStateTest {
    @Test fun emptyGlassWidgetUsesOneContinuousSurfaceAndRestoresCourseLayout() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            val prefs = AppPreferences(glassWidgetBackgroundAlpha = .4f)
            val data = cn.qztc.timetable.widget.TodayWidgetData(preferences = prefs)
            val views = cn.qztc.timetable.widget.buildGlassTodayWidgetViews(context, 98765, data)
            assertEquals(R.layout.widget_today, views.layoutId)
            val root = views.apply(context, android.appwidget.AppWidgetHostView(context))
            assertEquals("今天没有课程", root.findViewById<TextView>(R.id.widget_empty).text.toString())
            assertEquals(102, root.findViewById<ImageView>(R.id.widget_background).imageAlpha)
            assertNull(root.findViewById<View>(R.id.widget_glass_empty_card_3))
            for (height in listOf(284, 190, 284)) {
                val density = context.resources.displayMetrics.density
                root.measure(View.MeasureSpec.makeMeasureSpec((280*density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((height*density).toInt(), View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                assertEquals(root.height, root.findViewById<View>(R.id.widget_background).height)
                assertTrue(root.findViewById<View>(R.id.widget_empty).height > 0)
            }
            val entry = cn.qztc.timetable.widget.TodayWidgetEntry("合成课程", java.time.LocalTime.of(8,20), java.time.LocalTime.of(10,0), "合成教室", 1)
            val populated = cn.qztc.timetable.widget.buildGlassTodayWidgetViews(context, 98765, data.copy(entries = listOf(entry)))
            assertEquals(R.layout.widget_today_glass, populated.layoutId)
        }
    }

    @Test fun emptyCardsSurviveInflationAndResizeFromThreeToTwoAndBack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            val prefs = AppPreferences(glassWidgetBackgroundAlpha = .4f)
            fun remote(rows: Int) = RemoteViews(context.packageName, R.layout.widget_today_glass).apply {
                configureGlassEmptyCards(rows, prefs)
                setViewVisibility(R.id.widget_glass_course_list, View.GONE)
                setViewVisibility(R.id.widget_glass_empty, View.VISIBLE)
            }
            val root = remote(3).apply(context, FrameLayout(context))
            val density = context.resources.displayMetrics.density
            for (rows in listOf(3, 2, 3)) {
                remote(rows).reapply(context, root)
                val height = if (rows == 3) 284 else 190
                root.measure(View.MeasureSpec.makeMeasureSpec((280 * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                val first = root.findViewById<TextView>(R.id.widget_glass_empty_message_1)
                val second = root.findViewById<TextView>(R.id.widget_glass_empty_message_2)
                val third = root.findViewById<View>(R.id.widget_glass_empty_card_3)
                assertEquals(if (rows == 3) "" else "今天没有课程", first.text.toString())
                assertEquals(if (rows == 3) "今天没有课程" else "", second.text.toString())
                assertEquals(if (rows == 3) View.VISIBLE else View.GONE, third.visibility)
                assertTrue(first.height > 0 && second.height > 0)
                assertTrue(kotlin.math.abs(first.height - second.height) <= 1)
                assertEquals(102, root.findViewById<ImageView>(R.id.widget_glass_empty_background_1).imageAlpha)
                val message = if (rows == 3) second else first
                assertTrue(message.layout.getLineBottom(message.lineCount - 1) <= message.height)
            }
        }
    }
}
