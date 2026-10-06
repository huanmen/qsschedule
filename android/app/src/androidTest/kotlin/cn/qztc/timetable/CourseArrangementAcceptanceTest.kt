package cn.qztc.timetable

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import cn.qztc.timetable.core.domain.model.*
import cn.qztc.timetable.core.domain.imports.PersonalClassMerger
import cn.qztc.timetable.core.domain.schedule.TimetableState
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.feature.settings.SemesterScheduleSettingsScreen
import cn.qztc.timetable.qnu.ClassMergeReviewScreen
import cn.qztc.timetable.qnu.timetableImportFailureReason
import cn.qztc.timetable.storage.StorageSettingsScreen
import cn.qztc.timetable.integration.qnu.QnuTimetableImporter
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class CourseArrangementAcceptanceTest {
    @Test fun includedHolidayAppearsOnceInSeparateHolidaySection() {
        val semester=Semester("included-holiday","2026 秋",2026,Term.AUTUMN,LocalDate.of(2026,8,31),20)
        val holiday=(1..3).map { day ->
            AcademicDay(
                id="included-holiday-$day",
                semesterId=semester.id,
                date=LocalDate.of(2026,10,day),
                kind=AcademicDayKind.HOLIDAY,
                name="国庆节",
            )
        }
        compose.setContent { QztcTheme {
            SemesterScheduleSettingsScreen(
                semester=semester,
                periodScheme=PeriodSchemeFactory.forSemester(semester.id),
                academicDays=holiday,
                onSave={_,_,_->},
                onBack={},
            )
        } }

        compose.onNodeWithText("活动安排").assertIsDisplayed()
        compose.onNodeWithText("节假日安排").assertIsDisplayed()
        compose.onAllNodesWithText("国庆节", substring=true).assertCountEquals(1)
        compose.onAllNodesWithText("2026/10/01 - 10/03", substring=true)
            .onFirst()
            .assertIsDisplayed()
    }

    @Test fun arrangementOpensSharedMultiSessionActivityEditor() {
        val semester=Semester("activity-editor","2026 秋",2026,Term.AUTUMN,LocalDate.of(2026,8,31),20)
        compose.setContent { QztcTheme {
            SemesterScheduleSettingsScreen(semester,PeriodSchemeFactory.forSemester(semester.id),
                onSave={_,_,_->},onBack={})
        } }
        compose.waitUntil(15000){compose.onAllNodesWithText("查看校历").fetchSemanticsNodes().isNotEmpty()}
        val calendar=compose.onNodeWithText("查看校历").fetchSemanticsNode().boundsInRoot
        val activities=compose.onNodeWithText("活动安排").fetchSemanticsNode().boundsInRoot
        assertTrue(calendar.bottom <= activities.top)
        compose.onNodeWithText("添加").performClick()
        compose.onNodeWithText("活动名称").assertIsDisplayed()
        compose.onNodeWithText("活动备注").assertIsDisplayed()
        compose.onNodeWithText("添加场次").assertIsDisplayed()
    }

    @Test fun timelineEventCanSaveWithoutLeavingCalendar() {
        val current=Semester("timeline-edit","2026 秋",2026,Term.AUTUMN,LocalDate.of(2026,8,31),20)
        val changed=AtomicReference<List<cn.qztc.timetable.feature.settings.SchoolCalendarEvent>>()
        compose.setContent { QztcTheme { SemesterScheduleSettingsScreen(current,PeriodSchemeFactory.forSemester(current.id),
            onSave={_,_,_->},onBack={},onCalendarEventsChange={changed.set(it)}) } }
        compose.onNodeWithText("保存课程安排").assertDoesNotExist()
        compose.waitUntil(15000){compose.onAllNodesWithText("查看校历").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("查看校历").performClick()
        compose.onNodeWithTag("calendar-preview-list").performScrollToNode(hasText("学生报到"))
        compose.onAllNodesWithText("学生报到").onFirst().performClick()
        compose.onNodeWithText("开始日期").assertIsDisplayed()
        compose.onNodeWithText("结束日期").assertIsDisplayed()
        compose.onNodeWithText("保存并同步").assertDoesNotExist()
        compose.onNodeWithContentDescription("收起编辑").performClick()
        compose.waitForIdle()
        assertNull(changed.get())
        compose.onNodeWithText("活动安排").assertExists()
    }
    @get:Rule val compose=createAndroidComposeRule<FeatureAcceptanceActivity>()
    @Test fun headerSelectsSemesterWithBottomSheet() {
        val current=Semester("header-current","2026 秋",2026,Term.AUTUMN,LocalDate.of(2026,8,31),20)
        val previous=current.copy(id="header-old",name="2026 春",term=Term.SPRING,startDate=LocalDate.of(2026,2,23))
        val selected=AtomicReference<Semester>()
        compose.setContent { QztcTheme { SemesterScheduleSettingsScreen(current,PeriodSchemeFactory.forSemester(current.id),
            onSave={_,_,_->},onBack={},semesterOptions=listOf(current,previous),onSelectSemester={selected.set(it)}) } }
        compose.onNodeWithText("课程安排").assertIsDisplayed()
        compose.onNodeWithText("开学日期").assertIsDisplayed()
        compose.onNodeWithContentDescription("编辑校历设置").assertDoesNotExist()
        compose.onNodeWithText("2026 秋").performClick()
        compose.onNodeWithText("选择学期").assertIsDisplayed()
        compose.onNodeWithText("2026 春").performClick()
        assertEquals(previous,selected.get())
    }
    @Test fun overlapNoticeScrollsToClassEntry() {
        val course=Course("p","s",SourceType.QNU_EDU,"p",name="个人课程")
        val personal=CourseMeeting("p","p",1,1,2,WeekRule.of(listOf(1)),"甲","A",SourceType.QNU_EDU)
        val extras=(1..6).map { i ->
            val c=course.copy(id="c$i",name="班级课程$i")
            PersonalClassMerger.ExtraSlot(c,personal.copy(id="m$i",courseId=c.id))
        }
        val conflict=PersonalClassMerger.OverlapConflict("个人课程","班级课程6",1,1,2,1,2,listOf(1),"p","m6")
        val result=PersonalClassMerger.Result(TimetableState(listOf(course),listOf(personal)),extras,emptyList(),listOf(conflict))
        compose.setContent { QztcTheme { ClassMergeReviewScreen(result,{}, {}) } }
        compose.onNodeWithText("班级课表：班级课程6").performClick()
        compose.onNodeWithTag("merge-extra-m6").assertIsDisplayed()
    }
    @Test fun storageTotalsAndGroupsAreReadable() {
        compose.setContent { QztcTheme { StorageSettingsScreen(null,{}) } }
        compose.waitUntil(15000) { compose.onAllNodesWithText("总占用",substring=true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("程序",useUnmergedTree=true).assertExists()
        compose.onNodeWithText("数据",useUnmergedTree=true).assertExists()
        compose.onNodeWithText("缓存",useUnmergedTree=true).assertExists()
    }

    @Test fun storageEntryCanBePreviewedBeforeCleanup() {
        val suffix = "qa-preview-${UUID.randomUUID()}"
        val file = java.io.File(compose.activity.cacheDir, "school-calendar-$suffix.pdf")
        file.writeBytes(ByteArray(2_048))
        try {
            compose.setContent { QztcTheme { StorageSettingsScreen(null,{}) } }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("展开校历与下载").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("展开校历与下载").performClick()
            val previewTag = "storage-preview:${file.canonicalPath}"
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag(previewTag).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag(previewTag).performClick()
            compose.onNode(hasText("$suffix 学年", substring = true) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            compose.onNode(hasText("占用空间：2.0 KB", substring = true) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            compose.onNodeWithText("关闭").assertIsDisplayed()
            compose.onNodeWithText("关闭").performClick()
        } finally {
            file.delete()
        }
    }

    @Test fun emptyQnuTimetableUsesClearReasonWithoutRealCourseData() {
        val html = """
            <html><body>
            <div id="title">2026 秋 学生课表</div>
            <table id="timetable"><tr><th></th><th>周一</th></tr></table>
            </body></html>
        """.trimIndent()
        val imported = QnuTimetableImporter.importHtml(html, "synthetic-semester")

        assertTrue(imported.diagnostics.any { it.code == "EMPTY_GRID" })
        assertEquals(
            "个人课表为空，所选学期没有课程；现有课表不会被覆盖",
            timetableImportFailureReason("个人课表", imported),
        )
    }

    @Test fun calendarOriginalOpensInsideAppWithoutExternalDownloadPrompt() {
        val context=compose.activity
        val root=org.json.JSONObject(context.assets.open("school-calendar-2026-2027.json").bufferedReader().use { it.readText() })
        val url=root.getString("pdfUrl")
        val file=java.io.File(context.cacheDir,"school-calendar-${url.hashCode()}.pdf")
        val existed=file.isFile
        if(!existed) {
            val pdf=android.graphics.pdf.PdfDocument()
            try {
            val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(600,800,1).create())
            page.canvas.drawColor(android.graphics.Color.WHITE)
            page.canvas.drawText("Synthetic calendar PDF",30f,80f,android.graphics.Paint().apply{textSize=20f})
            pdf.finishPage(page);file.outputStream().use { pdf.writeTo(it) }
            } finally { pdf.close() }
        }
        try {
            val semester=Semester("pdf-test","2026 秋",2026,Term.AUTUMN,LocalDate.of(2026,8,31),20)
            compose.setContent { QztcTheme { SemesterScheduleSettingsScreen(semester,PeriodSchemeFactory.forSemester(semester.id),onSave={_,_,_->},onBack={}) } }
            compose.waitUntil(15000){compose.onAllNodesWithText("查看校历").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("查看校历").performClick()
            compose.onNodeWithContentDescription("查看原文件").performClick()
            compose.waitUntil(15000){compose.onAllNodesWithContentDescription("校历 PDF 页面").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("校历原文件").assertIsDisplayed()
            compose.onNodeWithContentDescription("下载原文件").assertIsDisplayed()
        } finally { if(!existed) file.delete() }
    }
}
