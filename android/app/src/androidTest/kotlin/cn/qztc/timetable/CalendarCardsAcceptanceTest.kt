package cn.qztc.timetable

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.qztc.timetable.core.domain.calendar.*
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.feature.settings.ActivityManagementScreen
import androidx.compose.ui.graphics.Color
import cn.qztc.timetable.feature.timetable.activityColorCss
import cn.qztc.timetable.plugin.AcademicRecordsPlugin
import cn.qztc.timetable.plugin.PluginPins
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** No activity launch is needed to exercise the real host downloader. */
class OfficialPluginDownloadAcceptanceTest {
    @Test fun officialPackageDownloadsAndInstallsWithoutChangingUserPlugin() = runBlocking {
        val real = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val original = AcademicRecordsPlugin.dataFile(real).takeIf { it.exists() }?.readBytes()
        val version = AcademicRecordsPlugin.installedVersion(real)
        val pinned = PluginPins.contains(real, AcademicRecordsPlugin.ID)
        val root = File(real.cacheDir, "calendar-cards-plugin-qa").apply { mkdirs() }
        val isolated = object : ContextWrapper(real) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = root
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("calendar-cards-qa-$name", mode)
        }
        try {
            val result = AcademicRecordsPlugin.install(isolated)
            assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
            val downloaded = AcademicRecordsPlugin.dataFile(isolated).readBytes()
            assertEquals("b96d6c35fc0466e0cfa46a8e9ed1f8db7642649fbcf0f954c17c0da83c8c4553",
                MessageDigest.getInstance("SHA-256").digest(downloaded).joinToString("") { "%02x".format(it) })
            assertEquals(1, AcademicRecordsPlugin.installedVersion(isolated))
            assertTrue(AcademicRecordsPlugin.script(isolated)!!.size > 1_000_000)
            assertTrue(PluginPins.contains(isolated, AcademicRecordsPlugin.ID))
            assertEquals(version, AcademicRecordsPlugin.installedVersion(real))
            assertEquals(pinned, PluginPins.contains(real, AcademicRecordsPlugin.ID))
            assertArrayEquals(original, AcademicRecordsPlugin.dataFile(real).takeIf { it.exists() }?.readBytes())
        } finally {
            root.deleteRecursively()
            listOf(AcademicRecordsPlugin.ID, "personal_plugin_pins").forEach { isolated.getSharedPreferences(it, 0).edit().clear().commit() }
        }
    }

}

class ActivityManagementAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()
    @Test fun activityFiltersHideSchoolNodesAndOnlyExplicitEditNavigates() {
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        // A Wednesday start still includes the entire first teaching week.
        val term = Semester("qa", "验收学期", today.year, Term.AUTUMN, monday.plusDays(2), 20)
        fun memo(id: String, date: LocalDate?, end: LocalDate? = null) = ScheduleActivity(id, "calendar", id,
            kind = ActivityKind.DAY, memoTime = ActivityMemoTime(date, endDate = end), calendarTimetable = false)
        val activities = listOf(memo("当日安排", today), memo("连续安排", today.minusDays(1), today.plusDays(2)),
            memo("未定安排", null), memo("下周安排", monday.plusWeeks(1)), memo("学期首日安排", monday))
        val isolated = object : ContextWrapper(compose.activity) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("calendar-cards-management-qa-$name", mode)
        }
        var edited: String? = null
        compose.setContent { QztcTheme { CompositionLocalProvider(LocalContext provides isolated) {
            ActivityManagementScreen(activities, term, listOf(term), listOf(
                AcademicDay("qa-holiday", "qa", today, AcademicDayKind.HOLIDAY, "验收假日")),
                onBack = {}, onEdit = { edited = it }, onAdd = {}, activityColor = { Color(android.graphics.Color.parseColor(activityColorCss(it))) })
        } } }
        compose.onNodeWithText("学期首日安排").assertIsDisplayed()
        compose.onNodeWithText("验收假日").assertDoesNotExist()
        compose.onNodeWithText("按日期").performClick()
        compose.onNodeWithText("当日安排").assertIsDisplayed()
        compose.onNodeWithText("连续安排").assertIsDisplayed()
        compose.onNodeWithText("未定安排").assertIsDisplayed()
        compose.onNodeWithText("下周安排").assertDoesNotExist()
        compose.onNodeWithText("当日安排").performClick()
        compose.runOnIdle { assertNull(edited) }
        compose.onNodeWithTag("management-edit-当日安排").performClick()
        compose.runOnIdle { assertEquals("当日安排", edited); edited = null }
        compose.onNodeWithContentDescription("显示校历节点").performClick()
        compose.onNodeWithText("验收假日").assertIsDisplayed()
        compose.onNodeWithText("验收假日").performClick()
        compose.runOnIdle { assertNull(edited) }
        compose.onNodeWithContentDescription("隐藏校历节点").performClick()
        compose.onNodeWithText("验收假日").assertDoesNotExist()
        compose.onNodeWithText("按周次").performClick()
        compose.onNodeWithText("当日安排").assertIsDisplayed()
        compose.onNodeWithText("第 1 周 ▾").performClick()
        compose.onNodeWithText("选择周次").assertIsDisplayed()
        compose.onNodeWithText("2").performClick()
        compose.onNodeWithText("下周安排").assertIsDisplayed()
        compose.onNodeWithText("当日安排").assertDoesNotExist()
    }
}
