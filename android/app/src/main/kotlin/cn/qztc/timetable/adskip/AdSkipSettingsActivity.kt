package cn.qztc.timetable.adskip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.qztc.timetable.core.ui.theme.QztcTheme

/** Notification destination, without opening the user's personal timetable. */
class AdSkipSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QztcTheme { AdSkipScreen { finish() } } }
    }
}
