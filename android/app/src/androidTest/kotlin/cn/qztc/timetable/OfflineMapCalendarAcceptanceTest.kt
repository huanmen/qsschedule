package cn.qztc.timetable

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.graphics.Bitmap
import android.view.PixelCopy
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import cn.qztc.timetable.core.domain.calendar.AcademicDayKind
import cn.qztc.timetable.core.domain.model.PeriodSchemeFactory
import cn.qztc.timetable.core.domain.model.Semester
import cn.qztc.timetable.core.domain.model.Term
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.feature.settings.SemesterScheduleSettingsScreen
import cn.qztc.timetable.feature.settings.SchoolCalendarWebFetcher
import cn.qztc.timetable.feature.timetable.CampusRouteScreen
import java.time.LocalDate
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OfflineMapCalendarAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
        else -> null
    }

    private fun evaluate(script: String): String? {
        val result = AtomicReference<String?>()
        val done = CountDownLatch(1)
        compose.activity.runOnUiThread {
            val web = findWebView(compose.activity.window.decorView)
            if (web == null) done.countDown() else web.evaluateJavascript(script) { result.set(it); done.countDown() }
        }
        assertTrue("WebView callback timeout", done.await(30, TimeUnit.SECONDS))
        return result.get()
    }

    private fun tapDom(selector: String) {
        val encoded = org.json.JSONObject.quote(selector)
        val data = org.json.JSONObject(org.json.JSONTokener(evaluate("JSON.stringify((()=>{const r=document.querySelector($encoded).getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,width:innerWidth};})())")).nextValue() as String)
        val point = IntArray(2)
        compose.runOnIdle {
            val web=findWebView(compose.activity.window.decorView)!!
            web.getLocationOnScreen(point)
            val scale=web.width/data.getDouble("width")
            point[0]+=(data.getDouble("x")*scale).toInt()
            point[1]+=(data.getDouble("y")*scale).toInt()
        }
        shell("input tap ${point[0]} ${point[1]}")
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
        }
    }

    private fun waitForKeyboard(visible: Boolean) {
        compose.waitUntil(10_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==visible
        }
    }

    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("screenshots") != "true") return
        val image = AtomicReference<Bitmap>()
        val copied = AtomicReference<Int>()
        val done = CountDownLatch(1)
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            image.set(bitmap)
            PixelCopy.request(compose.activity.window, bitmap, { copied.set(it); done.countDown() }, Handler(Looper.getMainLooper()))
        }
        if (!done.await(20, TimeUnit.SECONDS) || copied.get() != PixelCopy.SUCCESS) return
        val bitmap = image.get()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = compose.activity.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/QztcAcceptance")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } else {
            val file = File(compose.activity.getExternalFilesDir(null), name)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        bitmap.recycle()
    }

    private fun loadPackagedMap() {
        compose.runOnUiThread {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(compose.activity.window,false)
            compose.activity.startActivity(
                Intent(compose.activity, FeatureAcceptanceActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            )
        }
        compose.setContent { QztcTheme { CampusRouteScreen("俊秀图书馆", {}, initialMode = "edit") } }
        // A fresh emulator has not made the SDK privacy choice. Its modal owns
        // window focus until dismissed; keyboard tests use system location.
        if (compose.onAllNodesWithText("使用系统定位").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("使用系统定位").performClick()
        }
        compose.waitUntil(timeoutMillis = 15_000) { compose.activity.hasWindowFocus() }
        compose.waitUntil(timeoutMillis = 45_000) {
            evaluate("Boolean(window.CampusApp && window.__mapEditor && document.querySelectorAll('.athletics-svg').length===2)") == "true"
        }
        assertEquals("true", evaluate("Boolean([...document.querySelectorAll('.athletics-svg')].every(i=>i.complete&&i.naturalWidth===661))"))
        compose.waitUntil(timeoutMillis = 30_000) {
            evaluate("Boolean(document.querySelectorAll('img.leaflet-tile').length && [...document.querySelectorAll('img.leaflet-tile')].every(i=>i.complete&&i.naturalWidth===256))") == "true"
        }
        assertEquals("true", evaluate("Boolean(__mapEditor.store.list().some(p=>p.name==='一操') && __mapEditor.store.list().some(p=>p.name==='二操'))"))
        assertEquals("Search must not focus when opening a destination", "false",
            evaluate("document.activeElement===document.querySelector('#campus-search')"))
        evaluate("CampusApp.setDestination('俊秀图书馆'); __mapEditor.setRoutePoint('start','map-science'); true")
        compose.waitUntil(timeoutMillis = 30_000) { evaluate("Boolean(__mapEditor.route && __mapEditor.route.points.length>10)") == "true" }
        assertEquals("true", evaluate("(() => { document.querySelector('#settings-open').click(); return document.querySelector('#settings').open; })()"))
        assertEquals("true", evaluate("(() => { const r=document.querySelector('#settings').getBoundingClientRect(); return r.top>=0&&r.bottom<=innerHeight; })()"))
        val layout=evaluate("JSON.stringify({map:document.querySelector('#map').getBoundingClientRect().toJSON(),dock:document.querySelector('#map-details').getBoundingClientRect().toJSON(),height:innerHeight})")
        assertEquals(layout,"true", evaluate("(() => { const m=document.querySelector('#map').getBoundingClientRect(),d=document.querySelector('#map-details').getBoundingClientRect();return m.height>300&&m.bottom<=d.top+1&&d.bottom<=innerHeight+1; })()"))
        assertEquals("true", evaluate("(() => { CampusApp.setLocation({lat:24.868021,lng:118.67256,accuracy:10,timestamp:Date.now()}); return true; })()"))
        assertEquals("true", evaluate("(() => { document.querySelector('#settings').close(); campusMap.setView(CampusLayout.crop.center,16,{animate:false}); return true; })()"))
        screenshot("offline-map-acceptance.png")
        compose.runOnIdle {
            val web = findWebView(compose.activity.window.decorView)
            assertNotNull(web)
            assertTrue(web!!.settings.blockNetworkLoads)
            assertTrue(!web.settings.allowFileAccess && !web.settings.allowContentAccess)
        }
    }

    @Test fun packagedMapLoadsSearchesAndRoutesWithoutNetwork() {
        loadPackagedMap()
        evaluate("(() => { const places=__mapEditor.store.list();const from=places.find(p=>p.name==='教育科学学院');const to=places.find(p=>p.name==='中山广场');__mapEditor.setRoutePoint('start',from.id);__mapEditor.setRoutePoint('end',to.id);return true; })()")
        compose.waitUntil(30_000) { evaluate("Boolean(__mapEditor.route?.edgeKeys?.includes('repair:existing-building-corridor'))") == "true" }
        assertEquals("true",evaluate("Boolean(__mapEditor.route.meters<400)"))
    }

    @Test fun weekSwitchIsAlwaysVisibleAndHolidayHasThreeCompleteBlocks() {
        foregroundTestActivity()
        val semester = Semester("holiday-render-test", "合成测试学期", 2026, Term.AUTUMN, LocalDate.of(2026,8,31))
        val state = cn.qztc.timetable.feature.timetable.DemoScheduleState().apply {
            importedSchedule = cn.qztc.timetable.feature.timetable.ImportedSchedule(
                semester, PeriodSchemeFactory.forSemester(semester.id).periods,
                cn.qztc.timetable.core.domain.schedule.TimetableState.EMPTY,
                academicDays = listOf(AcademicDay("test-midautumn", semester.id, LocalDate.of(2026,9,25), AcademicDayKind.HOLIDAY,"中秋节")),
            )
        }
        val week = androidx.compose.runtime.mutableIntStateOf(1)
        compose.setContent {
            QztcTheme { androidx.compose.runtime.key(week.intValue) {
                cn.qztc.timetable.feature.timetable.WeekScreen(state, { _, _ -> }, initialWeek = week.intValue)
            } }
        }
        compose.onNodeWithTag("week-book-open").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithTag("week-book-closed").assertIsDisplayed()
        compose.runOnIdle { week.intValue = 4 }
        compose.onNodeWithTag("week-book-open").performTouchInput { click() }
        compose.onAllNodesWithText("中秋快乐").assertCountEquals(3)
    }

    @Test fun mapLongPressAndHeaderKeepPresentingFrames() {
        foregroundTestActivity()
        compose.setContent { QztcTheme { CampusRouteScreen("", {}) } }
        if (compose.onAllNodesWithText("使用系统定位").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("使用系统定位").performClick()
        }
        compose.waitUntil(45_000) { evaluate("Boolean(window.CampusApp && document.querySelector('[data-place-id]'))") == "true" }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val before = automation.takeScreenshot()
        tapDom("#route-expand")
        compose.waitUntil(5_000) { evaluate("document.querySelector('#route-expand').getAttribute('aria-expanded')==='true'") == "true" }
        val origin = IntArray(2)
        compose.runOnIdle { findWebView(compose.activity.window.decorView)!!.getLocationOnScreen(origin) }
        // Check pixels from the end-row area, not just the DOM: the original
        // failure changed DOM/coordinates while keeping the old screen image.
        compose.waitUntil(5_000) {
            val after = automation.takeScreenshot()
            var changed = 0
            val top = origin[1] + (60 * compose.activity.resources.displayMetrics.density).toInt()
            for (y in top until minOf(top + 100, after.height) step 4) {
                for (x in 50 until after.width - 50 step 8) {
                    if (before.getPixel(x,y) != after.getPixel(x,y)) changed++
                }
            }
            after.recycle()
            changed > 100
        }
        before.recycle()
        evaluate("window.__testPlace=[...document.querySelectorAll('[data-place-id]')].find(e=>e.title==='中山广场').dataset.placeId;const p=__mapEditor.store.list().find(p=>p.id===__testPlace);campusMap.setView(p.coordinate,17,{animate:false});true")
        val point = org.json.JSONObject(org.json.JSONTokener(evaluate("JSON.stringify((()=>{const r=document.querySelector('[data-place-id=\"'+__testPlace+'\"]').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,width:innerWidth}})())")).nextValue() as String)
        val screen = IntArray(2)
        compose.runOnIdle {
            val web = findWebView(compose.activity.window.decorView)!!
            web.getLocationOnScreen(screen)
            val scale = web.width / point.getDouble("width")
            screen[0] += (point.getDouble("x") * scale).toInt()
            screen[1] += (point.getDouble("y") * scale).toInt()
        }
        shell("input swipe ${screen[0]} ${screen[1]} ${screen[0]} ${screen[1]} 850")
        compose.waitUntil(5_000) { evaluate("!document.querySelector('#selected-panel').hidden") == "true" }
        assertEquals("\"中山广场\"", evaluate("document.querySelector('#selected-name').textContent"))
        val center = evaluate("JSON.stringify(campusMap.getCenter())")
        shell("input swipe ${screen[0]} ${origin[1]+850} ${screen[0]+220} ${origin[1]+950} 600")
        compose.waitUntil(5_000) { evaluate("JSON.stringify(campusMap.getCenter())") != center }
        tapDom("#route-expand")
        assertEquals("\"false\"", evaluate("document.querySelector('#route-expand').getAttribute('aria-expanded')"))
    }

    private fun foregroundTestActivity() {
        compose.runOnUiThread {
            compose.activity.startActivity(Intent(compose.activity, FeatureAcceptanceActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ))
        }
        compose.waitUntil(15_000) { compose.activity.hasWindowFocus() }
    }

    @Test fun mapSearchKeyboardCanTypeAndReopen() {
        loadPackagedMap()
        repeat(3) { attempt ->
            evaluate("document.querySelector('#campus-search').blur();document.querySelector('#campus-search').value='';true")
            tapDom("#campus-search")
            waitForKeyboard(true)
            compose.waitUntil(10_000) { evaluate("document.activeElement?.id") == "\"campus-search\"" }
            shell("input text library")
            try {
                compose.waitUntil(5_000) { evaluate("document.querySelector('#campus-search').value") == "\"library\"" }
            } catch (error: Throwable) {
                val native = AtomicReference<String>()
                compose.runOnIdle {
                    val web = findWebView(compose.activity.window.decorView)!!
                    val imm = compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    native.set("window=${web.hasWindowFocus()}, webFocus=${web.hasFocus()}, served=${imm.isActive(web)}, root=${compose.activity.currentFocus?.javaClass?.simpleName}")
                }
                throw AssertionError(
                    "Search input attempt ${attempt + 1}: ${evaluate("JSON.stringify({focus:document.activeElement?.id,value:document.querySelector('#campus-search').value,selection:[document.querySelector('#campus-search').selectionStart,document.querySelector('#campus-search').selectionEnd]})")}; $native",
                    error,
                )
            }
            shell("input keyevent KEYCODE_BACK")
            waitForKeyboard(false)
            tapDom("#campus-search")
            waitForKeyboard(true)
            compose.waitUntil(5_000) { evaluate("document.querySelector('#campus-search').selectionStart===document.querySelector('#campus-search').selectionEnd") == "true" }
            shell("input text x")
            compose.waitUntil(5_000) { evaluate("document.querySelector('#campus-search').value") == "\"libraryx\"" }
            shell("input keyevent KEYCODE_BACK")
            waitForKeyboard(false)
        }
    }

    @Test fun mapEditorKeyboardAvoidsObscuringSaveButton() {
        loadPackagedMap()
        val originalHeight=AtomicReference<Int>()
        compose.runOnIdle {originalHeight.set(findWebView(compose.activity.window.decorView)!!.height)}
        evaluate("document.addEventListener('pointerdown',e=>window.__qaPointer=e.target.id,true);true")
        evaluate("document.querySelector('#settings-open').click();document.querySelector('#start-edit').click();document.querySelector('#position-confirm').click();document.querySelector('#edit-name').focus();true")
        val tap = org.json.JSONObject(org.json.JSONTokener(evaluate("JSON.stringify((()=>{const r=document.querySelector('#edit-name').getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2};})())")).nextValue() as String)
        val screenTap=IntArray(2)
        compose.runOnIdle {
            val web=findWebView(compose.activity.window.decorView)!!
            web.requestFocusFromTouch()
            web.getLocationOnScreen(screenTap)
            val density=web.resources.displayMetrics.density
            screenTap[0]+=(tap.getDouble("x")*density).toInt()
            screenTap[1]+=(tap.getDouble("y")*density).toInt()
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input tap ${screenTap[0]} ${screenTap[1]}").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
        }
        try {
            compose.waitUntil(10_000) { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true }
        } catch (error: Throwable) {
            val native = AtomicReference<String>()
            compose.runOnIdle {
                val web=findWebView(compose.activity.window.decorView)!!
                val imm=compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                native.set("window=${web.hasWindowFocus()}, webFocus=${web.hasFocus()}, focusable=${web.isFocusableInTouchMode}, served=${imm.isActive(web)}, root=${compose.activity.currentFocus?.javaClass?.simpleName}, size=${web.width}x${web.height}")
            }
            throw AssertionError("IME diagnostics: ${evaluate("JSON.stringify({pointer:window.__qaPointer,focus:document.activeElement.id,input:document.querySelector('#edit-name').getBoundingClientRect().toJSON(),editor:document.querySelector('#editor').open,status:document.querySelector('#app-status').textContent,ratio:devicePixelRatio,height:innerHeight})")}; tap=${screenTap.toList()}; $native",error)
        }
        compose.waitForIdle()
        assertEquals("true",evaluate("(() => { const e=document.querySelector('#editor');e.scrollTop=e.scrollHeight;const r=document.querySelector('#save-place').getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight; })()"))
        compose.runOnIdle {
            val decor=compose.activity.window.decorView
            val web=findWebView(decor)!!
            val position=IntArray(2);web.getLocationOnScreen(position)
            val visible=android.graphics.Rect();decor.getWindowVisibleDisplayFrame(visible)
            assertEquals("Keyboard must overlay without resizing map",originalHeight.get(),web.height)
            (compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(web.windowToken,0)
        }
    }

    @Test fun officialCalendarRefreshKeepsDatesPendingWithoutOverwritingManualChoice() {
        val semester = Semester("acceptance", "合成测试学期", 2026, Term.AUTUMN, LocalDate.of(2026, 8, 31), 20)
        val manual = AcademicDay("academic:acceptance:2026-10-01", semester.id, LocalDate.of(2026, 10, 1), AcademicDayKind.HOLIDAY, "学校自定安排")
        val saved = AtomicReference<List<AcademicDay>?>(null)
        compose.setContent {
            QztcTheme {
                SemesterScheduleSettingsScreen(
                    semester,
                    PeriodSchemeFactory.forSemester(semester.id),
                    listOf(manual),
                    onSave = { _, _, _ -> },
                    onBack = {},
                    onAcademicDaysChange = { _, days -> saved.set(days) },
                )
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("重新获取").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("获取节假日安排").fetchSemanticsNodes().isNotEmpty()
        }
        val refresh = if (compose.onAllNodesWithText("重新获取").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("重新获取")
        } else {
            compose.onNodeWithText("获取节假日安排")
        }
        refresh.performScrollTo().performClick()
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText("正在获取最新校历…").fetchSemanticsNodes().isEmpty()
        }
        screenshot("calendar-import-acceptance.png")
        compose.runOnIdle {
            assertEquals(null, saved.get())
            compose.activity.finish()
        }
    }

    @Test fun websiteCalendarIsReadThroughTheSameWebViewStyleAsTimetableImport() {
        val semester = Semester(
            "website-calendar",
            "2026-2027 秋季学期",
            2026,
            Term.AUTUMN,
            LocalDate.of(2026, 8, 31),
            20,
        )
        val saved = AtomicReference<Semester>()
        compose.setContent {
            QztcTheme {
                SemesterScheduleSettingsScreen(
                    semester = semester,
                    periodScheme = PeriodSchemeFactory.forSemester(semester.id),
                    onSave = { updated, _, _ -> saved.set(updated) },
                    onBack = {},
                )
            }
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("查看校历", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(LocalDate.of(2026, 8, 31), saved.get()?.startDate ?: LocalDate.of(2026, 8, 31))
        compose.onNodeWithTag("calendar-settings-list").performScrollToNode(hasText("查看校历"))
        compose.onNodeWithText("查看校历").performClick()
        compose.onNodeWithText("活动安排").assertIsDisplayed()
        screenshot("calendar-timeline-all.png")
        compose.onNodeWithTag("calendar-preview-list").performScrollToNode(hasText("2026/08/29 - 08/30", substring = true))
        compose.onNodeWithText("2026/08/29 - 08/30", substring = true).assertExists()
        compose.onNodeWithTag("calendar-preview-list").performScrollToNode(hasText("春季学期", substring = true))
        compose.onNodeWithText("春季学期", substring = true).assertExists()
        compose.onNodeWithTag("calendar-preview-list").performScrollToNode(hasText("放假与调休"))
        compose.onNodeWithText("放假与调休").performClick()
        screenshot("calendar-timeline-holidays.png")
        compose.onNodeWithTag("calendar-preview-list").performScrollToNode(hasText("寒假"))
        compose.onNodeWithText("寒假").assertExists()
        compose.onNodeWithTag("calendar-preview-list").performScrollToNode(hasText("暑假"))
        compose.onNodeWithText("暑假").assertExists()
    }

    @Test fun websiteCalendarFetcherReturnsOfficialArticleAndPdf() = kotlinx.coroutines.runBlocking {
        val result = SchoolCalendarWebFetcher.load(
            compose.activity,
            "2026-2027",
            "website-calendar-direct",
        )
        assertTrue(result.sourceUrl?.contains("/jwc/2026/") == true)
        assertTrue(result.pdfUrl?.contains("/_upload/article/files/") == true)
        assertEquals(LocalDate.of(2026, 8, 31), result.firstSemesterStart)
        assertEquals(LocalDate.of(2027, 2, 22), result.secondSemesterStart)
        assertTrue(result.events.any { it.name == "寒假" && it.startDate == LocalDate.of(2027, 1, 16) && it.endDate == LocalDate.of(2027, 2, 20) })
        assertTrue(result.events.any { it.name == "暑假" && it.startDate == LocalDate.of(2027, 7, 3) && it.endDate == LocalDate.of(2027, 8, 27) })
        assertTrue(result.events.any { it.name == "校运动会" && it.startDate == LocalDate.of(2026, 11, 4) && it.endDate == LocalDate.of(2026, 11, 6) })
        assertTrue(result.events.any { it.name == "正式上课" && it.semesterLabel == "第二学期" && it.startDate == LocalDate.of(2027, 2, 22) })
    }

    @Test fun holidaySwipesFollowFingerAndDatesCanDisableMakeup() {
        val semester = Semester("gesture-calendar", "手势验收学期", 2026, Term.AUTUMN, LocalDate.of(2026,8,31),20)
        val holiday = AcademicDay("holiday",semester.id,LocalDate.of(2026,10,1),AcademicDayKind.HOLIDAY,"国庆节")
        val makeup = AcademicDay("makeup",semester.id,LocalDate.of(2026,10,10),AcademicDayKind.MAKEUP,"国庆调休")
        val candidates=org.json.JSONArray()
        listOf(holiday,makeup).forEach { day -> candidates.put(org.json.JSONObject()
            .put("id",day.id).put("date",day.date.toString()).put("kind",day.kind.name).put("name",day.name)
            .put("week",day.sourceWeek).put("weekday",day.sourceWeekday)
            .put("currentWeek",day.useCurrentWeek).put("currentWeekday",day.useCurrentWeekday)) }
        compose.activity.getSharedPreferences("calendar-candidates", Context.MODE_PRIVATE).edit()
            .putString(semester.id,candidates.toString()).commit()
        compose.activity.getSharedPreferences("calendar-inclusion", Context.MODE_PRIVATE).edit().clear().commit()
        val changes = java.util.concurrent.CopyOnWriteArrayList<Pair<List<AcademicDay>,List<AcademicDay>>>()
        compose.setContent { QztcTheme { SemesterScheduleSettingsScreen(semester,PeriodSchemeFactory.forSemester(semester.id),
            onSave={_,_,_->},onBack={},onTimelineDaysChange={old,next->changes.add(old to next)}) } }
        val tag="calendar-swipe-pending-holiday:2026-10-01:国庆节"
        fun row()=compose.onNodeWithTag(tag)
        row().performScrollTo().performTouchInput { swipeRight() }
        assertTrue(changes.isEmpty())
        row().performTouchInput { swipeLeft() }
        compose.waitUntil(10_000) { changes.size==1 }
        assertEquals(2,changes.single().second.size)
        row().performClick()
        compose.onNodeWithText("不调休").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("收起编辑").performClick()
        compose.waitUntil(10_000) { changes.size==2 }
        assertEquals(listOf(AcademicDayKind.HOLIDAY),changes.last().second.map { it.kind })
        row().performScrollTo().performTouchInput { swipeLeft() }
        compose.waitUntil(10_000) { changes.size==3 }
        assertTrue(changes.last().second.isEmpty())
        row().assertExists()
        compose.onNodeWithText("官网识别的校历事项").assertDoesNotExist()
        compose.onNodeWithText("正在从官网获取最新校历安排").assertDoesNotExist()
    }
}
