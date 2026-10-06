package cn.qztc.timetable

import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cn.qztc.timetable.qnu.captureFullWebPage
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FullPageCaptureTest {
    @get:Rule val compose=createAndroidComposeRule<FeatureAcceptanceActivity>()
    @Test fun screenshotContainsBottomBeyondVisibleViewportAndRestoresSize() = verifyCapture(false)
    @Test fun screenshotExpandsNestedTimetableFrame() = verifyCapture(true)
    @Test fun screenshotHasNoAddedRightMargin() = verifyCapture(false)
    private fun verifyCapture(nested: Boolean) = runBlocking {
        val loaded=CountDownLatch(1)
        lateinit var web: WebView
        withContext(Dispatchers.Main) {
            web=WebView(compose.activity).apply {
                settings.javaScriptEnabled=true
                webViewClient=object:WebViewClient(){override fun onPageFinished(view:WebView,url:String){loaded.countDown()}}
            }
            compose.activity.setContentView(web,ViewGroup.LayoutParams(-1,-1))
            val content="""<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;width:700px"><div style="height:3000px;background:rgb(200,230,250)">synthetic timetable</div><div style="height:300px;width:100%;background:rgb(230,30,70)">END OF TABLE</div></body></html>"""
            val html=if(nested) """<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;width:700px"><iframe style="width:700px;height:450px;border:0" srcdoc="${content.replace("\"","&quot;")}"></iframe></body></html>""" else content
            web.loadDataWithBaseURL("https://fixture.invalid/",html,"text/html","UTF-8",null)
        }
        assertTrue(loaded.await(15,TimeUnit.SECONDS))
        val height=withContext(Dispatchers.Main){web.height}
        val width=withContext(Dispatchers.Main){web.width}
        val bitmap=withTimeout(30_000){withContext(Dispatchers.Main){captureFullWebPage(web)}}
        try {
            assertTrue("Full capture is not taller than screen",bitmap.height>height)
            assertTrue("Full capture omitted horizontal overflow",bitmap.width>width)
            val scale=withContext(Dispatchers.Main){ @Suppress("DEPRECATION") web.scale }
            assertTrue("Unexpected right padding: ${bitmap.width}",bitmap.width<=kotlin.math.ceil(700*scale).toInt()+3)
            val red=(0 until bitmap.height).count { y ->
                val pixel=bitmap.getPixel(bitmap.width/2,y)
                android.graphics.Color.red(pixel)>190&&android.graphics.Color.green(pixel)<70&&android.graphics.Color.blue(pixel)<110
            }
            val samples=(0 until bitmap.height step 300).map { y -> "$y:${Integer.toHexString(bitmap.getPixel(bitmap.width/2,y))}" }
            assertTrue("Bottom strip was omitted: $red size=${bitmap.width}x${bitmap.height} samples=$samples",red>100)
            val right=(0 until bitmap.height).count { y -> android.graphics.Color.red(bitmap.getPixel(bitmap.width-100,y))>190 && android.graphics.Color.green(bitmap.getPixel(bitmap.width-100,y))<70 }
            assertTrue("Rightmost table columns omitted",right>100)
            assertEquals(height,withContext(Dispatchers.Main){web.height})
        } finally {
            bitmap.recycle()
            withContext(Dispatchers.Main) {
                (web.parent as? ViewGroup)?.removeView(web)
                compose.activity.setContentView(FrameLayout(compose.activity))
                web.destroy()
            }
        }
    }
}
