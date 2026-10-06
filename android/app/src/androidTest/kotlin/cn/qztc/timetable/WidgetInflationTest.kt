package cn.qztc.timetable

import android.widget.FrameLayout
import android.appwidget.AppWidgetHostView
import android.widget.ListView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.widget.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime
import android.os.Build

class WidgetInflationTest {
    @Test fun glassCardKeepsRoomAndReminderVisibleWithLongCourseName() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            val entry = TodayWidgetEntry("测试课程 · 这是一门用于检查长名称自适应高度的课程",
                LocalTime.of(8, 0), LocalTime.of(9, 40), "测试教室 101", 1,
                reminderMessage = "带好教材和实验记录")
            val card = buildGlassCourseRow(context, entry, AppPreferences(), LocalTime.of(8, 20), true, false, 112f)
                .apply(context, FrameLayout(context))
            val density = context.resources.displayMetrics.density
            card.measure(android.view.View.MeasureSpec.makeMeasureSpec((240 * density).toInt(), android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec((112 * density).toInt(), android.view.View.MeasureSpec.EXACTLY))
            card.layout(0, 0, card.measuredWidth, card.measuredHeight)
            val name = card.findViewById<TextView>(R.id.widget_glass_course_name)
            val room = card.findViewById<TextView>(R.id.widget_glass_course_room)
            val reminder = card.findViewById<TextView>(R.id.widget_glass_course_reminder)
            assertEquals("测试教室 101", room.text.toString())
            assertEquals("带好教材和实验记录", reminder.text.toString())
            assertTrue(name.right <= reminder.left)
            for (text in listOf(name, room, reminder)) {
                val bounds = android.graphics.Rect()
                text.getDrawingRect(bounds)
                (card as android.view.ViewGroup).offsetDescendantRectToMyCoords(text, bounds)
                assertTrue("Text outside card: ${text.id}", bounds.top >= 0 && bounds.bottom <= card.height - card.paddingBottom)
                assertTrue("Clipped text: ${text.id}", text.layout.getLineBottom(text.lineCount - 1) <= text.height)
            }
            val compact = buildGlassCourseRow(context, entry, AppPreferences(), LocalTime.of(8, 20), true, true)
                .apply(context, FrameLayout(context))
            assertEquals(android.view.View.GONE, compact.findViewById<TextView>(R.id.widget_glass_course_reminder).visibility)
            val noNote = buildGlassCourseRow(context, entry.copy(reminderMessage = ""), AppPreferences(), LocalTime.of(8, 20), true, false, 112f)
                .apply(context, FrameLayout(context))
            noNote.measure(android.view.View.MeasureSpec.makeMeasureSpec((240 * density).toInt(), android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec((112 * density).toInt(), android.view.View.MeasureSpec.EXACTLY))
            noNote.layout(0, 0, noNote.measuredWidth, noNote.measuredHeight)
            assertTrue(noNote.findViewById<TextView>(R.id.widget_glass_course_name).width > name.width)
        }
    }

    @Test fun widgetIncludesEveryCourseAndUpdatesCurrentState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val entries = (0..5).map { TodayWidgetEntry("测试课程$it", LocalTime.of(8 + it * 2, 0),
            LocalTime.of(9 + it * 2, 40), "测试教室", it) }
        instrumentation.runOnMainSync {
            val now = LocalTime.of(10, 20)
            val data = TodayWidgetData(entries)
            val views = buildTodayWidgetViews(context, 12345, data, now)
            val root = views.apply(context, AppWidgetHostView(context))
            if (Build.VERSION.SDK_INT >= 31) {
                assertEquals(6, root.findViewById<ListView>(R.id.widget_course_list).adapter.count)
                val empty = buildTodayWidgetViews(context, 12345, TodayWidgetData(), now)
                val emptyRoot = empty.apply(context, AppWidgetHostView(context))
                assertEquals(0, emptyRoot.findViewById<ListView>(R.id.widget_course_list).adapter.count)
            }
            val active = buildWidgetCourseRow(context, entries[1], AppPreferences(), now, true)
                .apply(context, FrameLayout(context))
            assertEquals("● 上课中", active.findViewById<TextView>(R.id.widget_course_status).text.toString())
        }
    }
}
