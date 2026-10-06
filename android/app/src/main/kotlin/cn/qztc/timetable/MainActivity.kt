package cn.qztc.timetable

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private var openTodayOnStart: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        openTodayOnStart = intent?.getBooleanExtra(EXTRA_OPEN_TODAY, false) == true
        setContent { QztcApp(openTodayOnStart = openTodayOnStart) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra(EXTRA_OPEN_TODAY, false) == true) {
            setIntent(intent)
            openTodayOnStart = true
            recreate()
        }
    }

    companion object {
        const val EXTRA_OPEN_TODAY = "cn.qztc.timetable.OPEN_TODAY"
    }
}
