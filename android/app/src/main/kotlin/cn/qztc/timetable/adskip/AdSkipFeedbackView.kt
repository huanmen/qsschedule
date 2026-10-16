package cn.qztc.timetable.adskip

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ImageSpan
import android.view.Gravity
import android.widget.TextView

internal fun feedbackLabel(context: Context): TextView {
    val density = context.resources.displayMetrics.density
    return TextView(context).apply {
        setTextColor(0xFF202124.toInt())
        textSize = 14f
        gravity = Gravity.CENTER
        maxWidth = (context.resources.displayMetrics.widthPixels * .88).toInt()
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        setPadding((18 * density).toInt(), (10 * density).toInt(), (18 * density).toInt(), (10 * density).toInt())
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 999 * density }
    }
}

internal fun TextView.renderFeedback(template: String, app: String) {
    val size = (24 * resources.displayMetrics.density).toInt()
    text = SpannableStringBuilder().apply {
        for (part in AdSkipFeedbackTemplate.parts(template, app)) {
            if (!part.icon) { append(part.text); continue }
            val start = length
            append('\uFFFC')
            val icon = context.applicationInfo.loadIcon(context.packageManager).mutate()
            icon.setBounds(0, 0, size, size)
            setSpan(ImageSpan(icon, ImageSpan.ALIGN_CENTER), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    contentDescription = AdSkipFeedbackTemplate.plain(template, app)
}
