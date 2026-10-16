package cn.qztc.timetable.adskip

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Spanned
import android.text.style.ImageSpan
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AdSkipFeedbackDeviceTest {
    @Test fun androidRegexAndIconSpansRenderDefaultAndReorderedTags() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = feedbackLabel(instrumentation.targetContext)
            view.renderFeedback(AdSkipFeedbackTemplate.DEFAULT, "菜鸟")
            val text = view.text as Spanned
            assertEquals("\uFFFC 菜鸟：跳过成功", text.toString())
            assertEquals(1, text.getSpans(0, text.length, ImageSpan::class.java).size)
            assertEquals(Color.WHITE, (view.background as GradientDrawable).color!!.defaultColor)
            view.renderFeedback("{应用名}已跳过 {图标}{图标}", "掌上公交")
            val reordered = view.text as Spanned
            assertEquals("掌上公交已跳过 \uFFFC\uFFFC", reordered.toString())
            assertEquals(2, reordered.getSpans(0, reordered.length, ImageSpan::class.java).size)
        }
    }
}
