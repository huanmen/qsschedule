package cn.qztc.timetable.feature.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebView
import android.webkit.WebViewClient
import cn.qztc.timetable.core.domain.calendar.AcademicDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONObject
import org.json.JSONTokener
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reads the official school calendar through the same WebView mechanism as timetable import. */
object SchoolCalendarWebFetcher {
    private const val LIST_URL = "https://www.qztc.edu.cn/jwc/1044/list.htm"

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun load(context: Context, schoolYear: String, semesterId: String): SchoolCalendarResult =
        withContext(Dispatchers.Main.immediate) {
            val web = WebView(context)
            val host = context.findActivity()
            try {
                web.settings.javaScriptEnabled = true
                web.settings.domStorageEnabled = true
                web.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                web.settings.allowFileAccess = false
                web.settings.allowContentAccess = false
                web.settings.blockNetworkLoads = false
                host?.addContentView(web, FrameLayout.LayoutParams(1, 1).apply { gravity = Gravity.TOP or Gravity.START })
                web.alpha = 0f
                web.visibility = View.VISIBLE
                awaitPage(web, LIST_URL)
                val articleUrl = evaluateString(web, findArticleScript(schoolYear))
                    ?: error("官网校历列表中没有找到 ${schoolYear} 校历")
                awaitPage(web, articleUrl)
                val viewerUrl = evaluateString(web, FIND_PDF_VIEWER_SCRIPT)
                    ?: error("校历文章中没有找到 PDF 阅读器")
                awaitPage(web, viewerUrl)
                val text = readPdfText(web)
                parseCalendarText(text, articleUrl, originalPdfUrl(viewerUrl), semesterId, schoolYear).also {
                    require(it.schoolYear == schoolYear) { "官网返回的校历学年与当前学年不一致" }
                }
            } finally {
                web.stopLoading()
                (web.parent as? ViewGroup)?.removeView(web)
                web.destroy()
            }
        }

    private suspend fun awaitPage(web: WebView, url: String): String = withTimeout(25_000) {
        suspendCancellableCoroutine { continuation ->
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    if (continuation.isActive) continuation.resume(finishedUrl)
                }
                override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                    if (request.isForMainFrame && continuation.isActive) continuation.resumeWithException(
                        IllegalStateException("官网校历页面加载失败"),
                    )
                }
            }
            web.loadUrl(url)
            continuation.invokeOnCancellation { web.stopLoading() }
        }
    }

    private suspend fun evaluateString(web: WebView, script: String): String? = withTimeout(15_000) {
        suspendCancellableCoroutine { continuation ->
            web.evaluateJavascript(script) { value ->
                if (!continuation.isActive) return@evaluateJavascript
                runCatching {
                    val parsed = JSONTokener(value ?: "null").nextValue()
                    (parsed as? String)?.takeIf { it.isNotBlank() }
                }.onSuccess(continuation::resume).onFailure(continuation::resumeWithException)
            }
        }
    }

    private suspend fun readPdfText(web: WebView): String {
        try {
            return withTimeout(30_000) {
                var text: String? = null
                while (text == null) {
                    val state = JSONObject(evaluateString(web, PDF_TEXT_SCRIPT) ?: error("校历读取脚本未能运行"))
                    when (state.optString("state")) {
                        "ready" -> text = state.optString("text").takeIf(String::isNotBlank)
                        "failed" -> error("校历 PDF 文字读取失败，请重试或查看官网原文")
                        else -> delay(350)
                    }
                }
                text!!
            }
        } catch (timeout: TimeoutCancellationException) {
            val state = runCatching { evaluateString(web, PDF_TEXT_SCRIPT) }.getOrNull().orEmpty()
            throw IllegalStateException("校历 PDF 读取超时（$state）", timeout)
        }
    }

    private fun findArticleScript(schoolYear: String): String = """
        (() => {
          const target = ${quote(schoolYear)};
          const links = [...document.querySelectorAll('a[href]')]
            .map(a => ({ href: new URL(a.getAttribute('href'), location.href).href, title: (a.getAttribute('title') || a.textContent || '').replace(/\s+/g, '') }))
            .filter(a => a.href.includes('/jwc/') && /校历/.test(a.title) && a.title.includes(target + '学年'));
          return links[0]?.href || '';
        })()
    """.trimIndent()

    private fun quote(value: String): String = org.json.JSONObject.quote(value)

    private fun originalPdfUrl(viewerUrl: String): String {
        val viewer = Uri.parse(viewerUrl)
        val file = viewer.getQueryParameter("file")?.let(Uri::parse) ?: return viewerUrl
        return when {
            file.scheme == "https" && file.host == "www.qztc.edu.cn" -> file.toString()
            file.scheme.isNullOrBlank() && file.path?.startsWith("/") == true -> "https://www.qztc.edu.cn${file.path}"
            else -> viewerUrl
        }
    }

    internal fun parseCalendarText(text: String, articleUrl: String, viewerUrl: String, semesterId: String, requestedSchoolYear: String): SchoolCalendarResult {
        val schoolYear = Regex("20\\d{2}[-—]20\\d{2}").find(text)?.value?.replace('—', '-') ?: requestedSchoolYear
        val normalizedText = text.replace(Regex("\\s+"), "")
        val ranges = Regex("(第一学期|第二学期)[^\\d]{0,30}(20\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})[^\\d]{0,24}[—–至-][^\\d]{0,24}(20\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})")
            .findAll(normalizedText)
            .associate { match ->
                val values = match.groupValues
                val start = LocalDate.of(values[2].toInt(), values[3].toInt(), values[4].toInt())
                val end = LocalDate.of(values[5].toInt(), values[6].toInt(), values[7].toInt())
                (if (values[1] == "第一学期") "first" else "second") to Pair(start, end)
            }
        if (ranges.isEmpty()) error("无法从官网校历 PDF 识别学期日期")
        val first = ranges["first"]
        val second = ranges["second"]
        val events = parseEvents(
            text = text,
            schoolYear = schoolYear,
            first = first,
            second = second,
        )
        return SchoolCalendarResult(
            // The PDF describes semesters and campus events, but it is not the
            // authoritative source for statutory holiday/makeup dates. Those
            // come from OfficialHolidayApi; inferring them from flattened PDF
            // text can attach a holiday name to an unrelated semester date.
            days = emptyList(),
            firstSemesterStart = first?.first,
            secondSemesterStart = second?.first,
            firstSemesterEnd = first?.second,
            secondSemesterEnd = second?.second,
            sourceUrl = articleUrl,
            pdfUrl = viewerUrl,
            schoolYear = schoolYear,
            events = events,
        )
    }

    private fun parseEvents(
        text: String,
        schoolYear: String,
        first: Pair<LocalDate, LocalDate>?,
        second: Pair<LocalDate, LocalDate>?,
    ): List<SchoolCalendarEvent> {
        val compact = text.replace(Regex("\\s+"), "")
        val firstIndex = compact.indexOf("第一学期")
        val secondIndex = compact.indexOf("第二学期")
        val sections = listOfNotNull(
            first?.let { "第一学期" to (firstIndex to if (secondIndex >= 0) secondIndex else compact.length) },
            second?.let { "第二学期" to (secondIndex to compact.length) },
        )
        return sections.flatMap { (label, bounds) ->
            if (bounds.first < 0 || bounds.second <= bounds.first) emptyList()
            else parseSectionEvents(compact.substring(bounds.first, bounds.second), schoolYear, label, if (label == "第一学期") first!! else second!!)
        }.distinctBy { it.id }.sortedWith(compareBy<SchoolCalendarEvent> { it.startDate }.thenBy { it.name })
    }

    private fun parseSectionEvents(
        section: String,
        schoolYear: String,
        semesterLabel: String,
        semester: Pair<LocalDate, LocalDate>,
    ): List<SchoolCalendarEvent> {
        val events = mutableListOf<SchoolCalendarEvent>()

        fun add(
            type: SchoolCalendarEventType,
            name: String,
            start: LocalDate,
            end: LocalDate? = null,
            detail: String = "",
        ) {
            events += SchoolCalendarEvent(
                id = "school-event:$schoolYear:$type:$start:$name",
                schoolYear = schoolYear,
                semesterLabel = semesterLabel,
                type = type,
                name = name,
                startDate = start,
                endDate = end,
                detail = detail,
            )
        }

        val reportPattern = Regex("(?:(教职工|学生|新生)[：:]?)?(?:(20\\d{2})年)?(\\d{1,2})月(\\d{1,2}(?:、\\d{1,2})*)日([^。；]{0,16})报到")
        reportPattern.findAll(section).forEach { match ->
            val year = match.groupValues[2].toIntOrNull()
            val month = match.groupValues[3].toInt()
            val dates = match.groupValues[4].split('、').map { day -> resolveDate(year, month, day.toInt(), semester) }
            val subject = match.groupValues[1].ifBlank {
                Regex("教职工|学生|新生").find(match.groupValues[5])?.value.orEmpty()
            }
            val name = when {
                subject == "教职工" -> "教职工报到"
                subject == "新生" -> "新生报到"
                else -> "学生报到"
            }
            add(SchoolCalendarEventType.REPORTING, name, dates.min(), dates.max(), "官网校历 $semesterLabel")
        }

        val teachingPattern = Regex("(?:(20\\d{2})年)?(\\d{1,2})月(\\d{1,2})日正式上课")
        teachingPattern.findAll(section).forEach { match ->
            val subject = section.substring(0, match.range.first).substringAfterLast('。').substringAfterLast('；')
            add(
                SchoolCalendarEventType.TEACHING_START,
                if (subject.contains("新生")) "新生正式上课" else "正式上课",
                resolveDate(match.groupValues[1].toIntOrNull(), match.groupValues[2].toInt(), match.groupValues[3].toInt(), semester),
                detail = "官网校历 $semesterLabel",
            )
        }

        val breakPattern = Regex("(寒假|暑假)[：:]?((?:(?:20\\d{2})年)?\\d{1,2}月\\d{1,2}日)[^\\d]{0,60}(?:至|到|-)[^\\d]{0,12}((?:(?:20\\d{2})年)?\\d{1,2}月\\d{1,2}日)")
        breakPattern.findAll(section).forEach { match ->
            val start = parseChineseDate(match.groupValues[2], semester)
            val end = parseChineseDate(match.groupValues[3], semester)
            add(SchoolCalendarEventType.BREAK, match.groupValues[1], start, end, "官网校历 $semesterLabel")
        }

        val sportsPattern = Regex("校运动会(?:暂定)?(?:(20\\d{2})年)?(\\d{1,2})月(\\d{1,2})(?:日)?(?:-|至)(\\d{1,2})日")
        sportsPattern.findAll(section).forEach { match ->
            val start = resolveDate(match.groupValues[1].toIntOrNull(), match.groupValues[2].toInt(), match.groupValues[3].toInt(), semester)
            val end = resolveDate(match.groupValues[1].toIntOrNull(), match.groupValues[2].toInt(), match.groupValues[4].toInt(), semester)
            add(SchoolCalendarEventType.SPORTS_MEETING, "校运动会", start, end, "官网校历标注为暂定时间")
        }

        val examPattern = Regex("((?:(?:20\\d{2})年)?\\d{1,2}月\\d{1,2}日前)完成")
        examPattern.findAll(section).forEach { match ->
            val prefix = section.substring(0, match.range.first).takeLast(24)
            if ("补考" in prefix) {
                val date = parseChineseDate(match.groupValues[1].removeSuffix("前"), semester)
                add(SchoolCalendarEventType.EXAM, "公共课、专业课补考截止", date, detail = "此前完成 · 官网校历 $semesterLabel")
            }
        }
        return events
    }

    private fun parseChineseDate(value: String, semester: Pair<LocalDate, LocalDate>): LocalDate {
        val match = Regex("(?:(20\\d{2})年)?(\\d{1,2})月(\\d{1,2})日").find(value)
            ?: error("无法识别校历日期：$value")
        return resolveDate(match.groupValues[1].toIntOrNull(), match.groupValues[2].toInt(), match.groupValues[3].toInt(), semester)
    }

    private fun resolveDate(year: Int?, month: Int, day: Int, semester: Pair<LocalDate, LocalDate>): LocalDate {
        if (year != null) return LocalDate.of(year, month, day)
        return (semester.first.year - 1..semester.second.year + 1)
            .map { candidateYear -> LocalDate.of(candidateYear, month, day) }
            .minBy { candidate ->
                when {
                    candidate.isBefore(semester.first) -> java.time.temporal.ChronoUnit.DAYS.between(candidate, semester.first)
                    candidate.isAfter(semester.second) -> java.time.temporal.ChronoUnit.DAYS.between(semester.second, candidate)
                    else -> 0L
                }
            }
    }

    private const val FIND_PDF_VIEWER_SCRIPT = """
        (() => {
          const iframe = document.querySelector('iframe.wp_pdf_player, iframe[src*="pdfjs"], iframe[src*="viewer.html"]');
          return iframe ? new URL(iframe.getAttribute('src'), location.href).href : '';
        })()
    """

    private const val PDF_TEXT_SCRIPT = """
        (() => {
          let capture = window.__qztcCalendarCapture;
          if (!capture) {
            const pdf = window.PDFViewerApplication?.pdfDocument;
            if (!pdf || !pdf.numPages) return JSON.stringify({state:'waiting'});
            capture = window.__qztcCalendarCapture = {state:'reading'};
            (async () => {
              if (pdf.numPages > 12) throw Error('page limit');
              const pages = [];
              for (let i = 1; i <= pdf.numPages; i++) {
                const content = await pdf.getPage(i).then(page => page.getTextContent());
                pages.push(content.items.map(item => item.str || '').join(' '));
              }
              const text = pages.join('\\n\\f\\n');
              if (!text.trim() || text.length > 300000) throw Error('text limit');
              capture.text = text;
              capture.state = 'ready';
            })().catch(() => { capture.state = 'failed'; });
          }
          return JSON.stringify(capture);
        })()
    """

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }
}
