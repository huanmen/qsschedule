package cn.qztc.timetable

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import cn.qztc.timetable.background.BackgroundBitmapCache
import cn.qztc.timetable.core.ui.theme.QztcPalette
import cn.qztc.timetable.core.ui.theme.QztcThemeMode
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class LaunchRenderingAcceptanceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun appearanceIsRetainedAndBackgroundIsReadyBeforePublication(): Unit = runBlocking {
        val folder = File(context.cacheDir, "render-test-${System.nanoTime()}").apply { mkdirs() }
        val image = File(folder, "wallpaper.png")
        Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xff123456.toInt())
            image.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder, "appearance.preferences_pb") })
        try {
            store.edit {
                it[stringPreferencesKey("background_source")] = "image"
                it[stringPreferencesKey("background_image_path")] = image.absolutePath
                it[stringPreferencesKey("theme_mode")] = "dark"
            }
            val resources = AppRenderResources(store, scope)
            val ready = withTimeout(10_000) { resources.appearance.filterNotNull().first() }
            assertEquals(QztcThemeMode.DARK, ready.preferences.themeMode)
            assertEquals(0xff123456.toInt(), BackgroundBitmapCache.peek(image.absolutePath)!!.getPixel(10, 10))
            // A new Activity/collector receives the current value, never a default/null theme.
            assertSame(ready, resources.appearance.value)
            assertSame(ready, resources.appearance.first())
            BackgroundBitmapCache.remove(image.absolutePath)
            assertEquals(0xff123456.toInt(), ready.background!!.getPixel(10,10))
            // Missing files must still publish a usable appearance, so first draw cannot deadlock.
            store.edit { it[stringPreferencesKey("background_image_path")] = File(folder,"missing.png").absolutePath }
            withTimeout(10_000) { resources.appearance.filterNotNull().first { it.preferences.backgroundImagePath != image.absolutePath } }
        } finally {
            scope.cancel()
            BackgroundBitmapCache.remove(image.absolutePath)
            folder.listFiles().orEmpty().forEach { it.delete() }; folder.delete()
        }
    }

    @Test fun launchColorsRespectExplicitAndSystemModesForAllPalettes() {
        if (android.os.Build.VERSION.SDK_INT < 31) return
        for (palette in QztcPalette.entries) for (mode in QztcThemeMode.entries) for (systemDark in listOf(false,true)) {
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if(systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themedContext = ContextThemeWrapper(context.createConfigurationContext(config), appLaunchTheme(mode,palette))
            val value = android.util.TypedValue()
            assertTrue(themedContext.theme.resolveAttribute(android.R.attr.windowSplashScreenBackground,value,true))
            val dark = mode == QztcThemeMode.DARK || mode == QztcThemeMode.SYSTEM && systemDark
            val expected = when(palette) {
                QztcPalette.ORIGINAL -> if(dark)0xff0b141f else 0xffe9f1ef
                QztcPalette.SODA -> if(dark)0xff0c1820 else 0xfff1f7f9
                QztcPalette.CITRUS -> if(dark)0xff11180f else 0xfff5f8ed
            }.toInt()
            assertEquals("$palette / $mode / systemDark=$systemDark",expected,value.data)
        }
    }
}
