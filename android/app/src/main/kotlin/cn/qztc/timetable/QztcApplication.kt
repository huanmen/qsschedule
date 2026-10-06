package cn.qztc.timetable

import android.app.Application
import android.webkit.WebView

class QztcApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        cn.qztc.timetable.widget.WidgetClock.register(this)
        WebView.enableSlowWholeDocumentDraw()
    }
}
