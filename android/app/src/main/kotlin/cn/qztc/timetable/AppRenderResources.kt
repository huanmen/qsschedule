package cn.qztc.timetable

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import android.graphics.Bitmap
import cn.qztc.timetable.background.BackgroundBitmapCache
import cn.qztc.timetable.core.ui.background.BackgroundMotion
import cn.qztc.timetable.core.ui.background.BackgroundSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** Process-owned appearance survives stopped collectors and Activity recreation. */
data class PreparedAppAppearance(val preferences: AppPreferences, val background: Bitmap?)

class AppRenderResources(dataStore: DataStore<Preferences>, scope: CoroutineScope) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val appearance = AppPreferencesStore(dataStore).preferences
        .distinctUntilChanged()
        .mapLatest { preferences ->
            val background = if (preferences.backgroundSource == BackgroundSource.IMAGE &&
                preferences.backgroundMotion != BackgroundMotion.OFF) {
                preferences.backgroundImagePath?.let { BackgroundBitmapCache.load(it) }
            } else null
            // Keep the selected image alive even if the carousel evicts its cache entry.
            PreparedAppAppearance(preferences, background)
        }
        .stateIn(scope, SharingStarted.Eagerly, null)
}
