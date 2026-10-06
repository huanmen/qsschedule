package cn.qztc.timetable

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.view.WindowManager

/** Empty host for isolated, synthetic feature acceptance tests. */
class FeatureAcceptanceActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
    }
}
