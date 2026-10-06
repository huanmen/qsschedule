package cn.qztc.timetable.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SchoolCalendarCacheTest {
    @Test fun freshInstallHasNoPackagedSchoolCalendar() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("school-calendar", Context.MODE_PRIVATE).edit().clear().commit()
        assertNull(SchoolCalendarApi.cached(context, "2026-2027", "test"))
        assertFalse(context.assets.list("").orEmpty().contains("school-calendar-2026-2027.json"))
    }
    @Test fun cacheIsScopedToItsActualAcademicYear() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("school-calendar", Context.MODE_PRIVATE).edit()
            .putString("year:2027-2028", """{"schoolYear":"2026-2027","semester":{}}""").commit()
        assertNull(SchoolCalendarApi.cached(context, "2027-2028", "test"))
    }
}
