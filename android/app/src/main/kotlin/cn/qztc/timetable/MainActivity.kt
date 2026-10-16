package cn.qztc.timetable

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import android.view.ViewTreeObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private data class LaunchRequest(val id: Int = 0, val today: Boolean = false,
        val homework: Boolean = false, val calendar: String? = null, val adSkip: Boolean = false)
    private var launchRequest by mutableStateOf(LaunchRequest())
    private var readyToDraw = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep Android's starting window until the actual appearance and local entry page
        // are ready. This gate is released once, never reinstalled on a normal resume.
        val firstFrameGate = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!readyToDraw) return false
                window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        }
        window.decorView.viewTreeObserver.addOnPreDrawListener(firstFrameGate)
        if (Build.VERSION.SDK_INT >= 31) {
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        consumeLaunchRequest(intent)
        setContent {
            QztcApp(openTodayOnStart = launchRequest.today, openHomeworkOnStart = launchRequest.homework,
                openCalendarOnStart = launchRequest.calendar, openAdSkipOnStart = launchRequest.adSkip,
                launchRequestId = launchRequest.id, onReadyToDraw = { readyToDraw = true })
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchRequest(intent)
    }

    private fun consumeLaunchRequest(intent: android.content.Intent?) {
        if (intent == null) return
        val next = LaunchRequest(launchRequest.id + 1,
            intent.getBooleanExtra(EXTRA_OPEN_TODAY, false),
            intent.getBooleanExtra(EXTRA_OPEN_HOMEWORK, false),
            intent.getStringExtra(EXTRA_OPEN_CALENDAR),
            intent.getBooleanExtra(EXTRA_OPEN_AD_SKIP, false))
        if (next.today || next.homework || next.calendar != null || next.adSkip) launchRequest = next
        intent.removeExtra(EXTRA_OPEN_TODAY)
        intent.removeExtra(EXTRA_OPEN_HOMEWORK)
        intent.removeExtra(EXTRA_OPEN_CALENDAR)
        intent.removeExtra(EXTRA_OPEN_AD_SKIP)
    }

    companion object {
        const val EXTRA_OPEN_TODAY = "cn.qztc.timetable.OPEN_TODAY"
        const val EXTRA_OPEN_HOMEWORK = "cn.qztc.timetable.OPEN_HOMEWORK"
        const val EXTRA_OPEN_CALENDAR = "cn.qztc.timetable.OPEN_CALENDAR"
        const val EXTRA_OPEN_AD_SKIP = "cn.qztc.timetable.OPEN_AD_SKIP"
    }
}
