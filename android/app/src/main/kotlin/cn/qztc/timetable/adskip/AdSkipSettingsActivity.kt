package cn.qztc.timetable.adskip

import android.os.Bundle
import androidx.activity.ComponentActivity

/** Notification destination, without opening the user's personal timetable. */
class AdSkipSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Forward existing notifications created before the destination was unified.
        startActivity(adSkipManagementIntent(this))
        finish()
    }
}
