package cn.qztc.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import cn.qztc.timetable.adskip.AdSkipScreen

/** Debug-only entry avoids opening personal timetable data during ad-skip QA. */
class AdSkipQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AdSkipScreen { finish() } } }
    }
}
