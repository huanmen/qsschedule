package cn.qztc.timetable

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.qztc.timetable.core.domain.model.HomePreference
import cn.qztc.timetable.core.domain.model.HomeView
import cn.qztc.timetable.core.domain.model.WeekendDisplayMode
import cn.qztc.timetable.core.domain.model.ReminderDisplayMode
import cn.qztc.timetable.core.ui.theme.FontSizeMode
import cn.qztc.timetable.core.ui.theme.QztcThemeMode
import cn.qztc.timetable.core.ui.theme.QztcPalette
import cn.qztc.timetable.core.ui.background.BackgroundMotion
import cn.qztc.timetable.core.ui.background.BackgroundSource
import cn.qztc.timetable.core.ui.glass.GlassQuality
import cn.qztc.timetable.core.ui.glass.GlassSettings
import cn.qztc.timetable.core.ui.theme.CoursePalette
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val DEFAULT_LAST_DESTINATION = "today"

val Context.qztcDataStore: DataStore<Preferences> by preferencesDataStore(name = "qztc_preferences")

data class AppPreferences(
    val homePreference: HomePreference = HomePreference.TODAY,
    val lastDestination: HomeView = HomeView.TODAY,
    val themeMode: QztcThemeMode = QztcThemeMode.SYSTEM,
    val palette: QztcPalette = QztcPalette.ORIGINAL,
    val syncEnabled: Boolean = false,
    val syncIntervalHours: Int = 2,
    val remindersEnabled: Boolean = false,
    val reminderMinutes: Int = 15,
    val reminderDisplayMode: ReminderDisplayMode = ReminderDisplayMode.SCREEN_POPUP,
    val remindOnHolidays: Boolean = false,
    /** Per-course override in minutes; zero means this course does not notify. */
    val courseReminderOverrides: Map<String, Int> = emptyMap(),
    val courseReminderMessages: Map<String, String> = emptyMap(),
    val dailyAgenda: cn.qztc.timetable.core.domain.model.DailyAgendaSettings = cn.qztc.timetable.core.domain.model.DailyAgendaSettings(),
    val backgroundSource: BackgroundSource = BackgroundSource.SCENE,
    val backgroundMotion: BackgroundMotion = BackgroundMotion.DYNAMIC,
    val backgroundImagePath: String? = null,
    val backgroundCarouselEnabled: Boolean = true,
    val backgroundCarouselIntervalSeconds: Int = 8,
    val backgroundPositionX: Float = .5f,
    val backgroundPositionY: Float = .5f,
    val glassQuality: GlassQuality = GlassQuality.FULL_LENS,
    val functionalAlpha: Float = .45f,
    val functionalBlur: Float = 13f,
    val contentAlpha: Float = .50f,
    val contentBlur: Float = 14f,
    val widgetBackgroundAlpha: Float = .96f,
    val glassWidgetBackgroundAlpha: Float = .85f,
    val weekendDisplayMode: WeekendDisplayMode = WeekendDisplayMode.HIDE_EMPTY,
    /** Built-in palette indexes (0..8) or custom opaque ARGB colors. */
    val coursePresetColors: List<Int> = CoursePalette.indices.toList(),
    val textPrimaryColor: Int? = null,
    val textSecondaryColor: Int? = null,
    val textTertiaryColor: Int? = null,
    val fontSizeMode: FontSizeMode = FontSizeMode.DEFAULT,
    val fontScale: Float = 1f,
) {
    fun startDestination(): TopDestination = when (homePreference) {
        HomePreference.TODAY -> TopDestination.Today
        HomePreference.WEEK -> TopDestination.Week
        HomePreference.CALENDAR -> TopDestination.Calendar
        HomePreference.LAST -> when (lastDestination) {
            HomeView.WEEK -> TopDestination.Week
            HomeView.CALENDAR -> TopDestination.Calendar
            else -> TopDestination.Today
        }
    }

    fun glassSettings(apiLevel: Int, lowMemory: Boolean): GlassSettings = GlassSettings(
        quality = glassQuality,
        functionalAlpha = functionalAlpha.coerceIn(.15f, .80f),
        functionalBlur = functionalBlur.coerceIn(0f, 30f).dp,
        contentAlpha = contentAlpha.coerceIn(.20f, .95f),
        contentBlur = contentBlur.coerceIn(0f, 30f).dp,
        apiLevel = apiLevel,
        lowMemory = lowMemory,
    )
}

class AppPreferencesStore(private val dataStore: DataStore<Preferences>) {
    private val agendaJson = Json { ignoreUnknownKeys = true }
    private object Keys {
        val homePreference = stringPreferencesKey("home_preference")
        val lastDestination = stringPreferencesKey("last_destination")
        val themeMode = stringPreferencesKey("theme_mode")
        val palette = stringPreferencesKey("theme_palette")
        val syncEnabled = booleanPreferencesKey("sync_enabled")
        val syncIntervalHours = intPreferencesKey("sync_interval_hours")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val reminderMinutes = intPreferencesKey("reminder_minutes")
        val reminderDisplayMode = stringPreferencesKey("reminder_display_mode")
        val remindOnHolidays = booleanPreferencesKey("remind_on_holidays")
        val courseReminderOverrides = stringPreferencesKey("course_reminder_overrides")
        val courseReminderMessages = stringPreferencesKey("course_reminder_messages")
        val dailyAgenda = stringPreferencesKey("daily_agenda_settings")
        val backgroundSource = stringPreferencesKey("background_source")
        val backgroundMotion = stringPreferencesKey("background_motion")
        val backgroundImagePath = stringPreferencesKey("background_image_path")
        val backgroundCarouselEnabled = booleanPreferencesKey("background_carousel_enabled")
        val backgroundCarouselIntervalSeconds = intPreferencesKey("background_carousel_interval_seconds")
        val backgroundPositionX = floatPreferencesKey("background_position_x")
        val backgroundPositionY = floatPreferencesKey("background_position_y")
        val glassQuality = stringPreferencesKey("glass_quality")
        val functionalAlpha = floatPreferencesKey("glass_functional_alpha")
        val functionalBlur = floatPreferencesKey("glass_functional_blur")
        val contentAlpha = floatPreferencesKey("glass_content_alpha")
        val contentBlur = floatPreferencesKey("glass_content_blur")
        val widgetBackgroundAlpha = floatPreferencesKey("widget_background_alpha")
        val glassWidgetBackgroundAlpha = floatPreferencesKey("glass_widget_background_alpha")
        val weekendDisplayMode = stringPreferencesKey("weekend_display_mode")
        val coursePresetColors = stringPreferencesKey("course_preset_colors")
        val textPrimaryColor = intPreferencesKey("text_primary_color")
        val textSecondaryColor = intPreferencesKey("text_secondary_color")
        val textTertiaryColor = intPreferencesKey("text_tertiary_color")
        val fontSizeMode = stringPreferencesKey("font_size_mode")
        val fontScale = floatPreferencesKey("font_scale")
    }

    val preferences: Flow<AppPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { values ->
            AppPreferences(
                homePreference = values[Keys.homePreference].toHomePreference(),
                lastDestination = values[Keys.lastDestination].toHomeView(),
                themeMode = values[Keys.themeMode].toThemeMode(),
                palette = runCatching { QztcPalette.valueOf(values[Keys.palette] ?: "ORIGINAL") }.getOrDefault(QztcPalette.ORIGINAL),
                syncEnabled = values[Keys.syncEnabled] ?: false,
                syncIntervalHours = (values[Keys.syncIntervalHours] ?: 2).coerceIn(1, 24),
                remindersEnabled = values[Keys.remindersEnabled] ?: false,
                reminderMinutes = (values[Keys.reminderMinutes] ?: 15).coerceIn(0, 60),
                reminderDisplayMode = values[Keys.reminderDisplayMode].toReminderDisplayMode(),
                remindOnHolidays = values[Keys.remindOnHolidays] ?: false,
                courseReminderOverrides = values[Keys.courseReminderOverrides].toCourseReminderOverrides(),
                courseReminderMessages = values[Keys.courseReminderMessages].toCourseReminderMessages(),
                dailyAgenda = runCatching {
                    agendaJson.decodeFromString<cn.qztc.timetable.core.domain.model.DailyAgendaSettings>(values[Keys.dailyAgenda] ?: "{}").normalized()
                }.getOrDefault(cn.qztc.timetable.core.domain.model.DailyAgendaSettings()),
                backgroundSource = values[Keys.backgroundSource].toBackgroundSource(),
                backgroundMotion = values[Keys.backgroundMotion].toBackgroundMotion(),
                backgroundImagePath = values[Keys.backgroundImagePath],
                backgroundCarouselEnabled = values[Keys.backgroundCarouselEnabled] ?: true,
                backgroundCarouselIntervalSeconds = (values[Keys.backgroundCarouselIntervalSeconds] ?: 8).coerceIn(1, 86_400),
                backgroundPositionX = values[Keys.backgroundPositionX]?.coerceIn(0f, 1f) ?: .5f,
                backgroundPositionY = values[Keys.backgroundPositionY]?.coerceIn(0f, 1f) ?: .5f,
                glassQuality = values[Keys.glassQuality].toGlassQuality(),
                functionalAlpha = values[Keys.functionalAlpha]?.coerceIn(.15f, .80f) ?: .45f,
                functionalBlur = values[Keys.functionalBlur]?.coerceIn(0f, 30f) ?: 13f,
                contentAlpha = values[Keys.contentAlpha]?.coerceIn(.20f, .95f) ?: .50f,
                contentBlur = values[Keys.contentBlur]?.coerceIn(0f, 30f) ?: 14f,
                widgetBackgroundAlpha = values[Keys.widgetBackgroundAlpha]?.coerceIn(0f, 1f) ?: .96f,
                glassWidgetBackgroundAlpha = values[Keys.glassWidgetBackgroundAlpha]?.coerceIn(0f, 1f) ?: .85f,
                weekendDisplayMode = values[Keys.weekendDisplayMode].toWeekendDisplayMode(),
                coursePresetColors = values[Keys.coursePresetColors].toCoursePresetColors(),
                textPrimaryColor = values[Keys.textPrimaryColor],
                textSecondaryColor = values[Keys.textSecondaryColor],
                textTertiaryColor = values[Keys.textTertiaryColor],
                fontSizeMode = values[Keys.fontSizeMode].toFontSizeMode(),
                fontScale = values[Keys.fontScale]?.coerceIn(.75f, 1.6f) ?: 1f,
            )
        }

    suspend fun setHomePreference(value: HomePreference) {
        dataStore.edit { it[Keys.homePreference] = value.name.lowercase() }
    }

    suspend fun setLastDestination(value: HomeView) {
        if (value == HomeView.MINE) return
        dataStore.edit { it[Keys.lastDestination] = value.name.lowercase() }
    }

    suspend fun setThemeMode(value: QztcThemeMode) {
        dataStore.edit { it[Keys.themeMode] = value.name.lowercase() }
    }

    suspend fun setPalette(value: QztcPalette) {
        dataStore.edit { it[Keys.palette] = value.name }
    }

    suspend fun setSyncEnabled(value: Boolean) {
        dataStore.edit { it[Keys.syncEnabled] = value }
    }

    suspend fun setSyncIntervalHours(value: Int) {
        dataStore.edit { it[Keys.syncIntervalHours] = value.coerceIn(1, 24) }
    }

    suspend fun setRemindersEnabled(value: Boolean) {
        dataStore.edit { it[Keys.remindersEnabled] = value }
    }

    suspend fun setDailyAgenda(value: cn.qztc.timetable.core.domain.model.DailyAgendaSettings) {
        dataStore.edit { it[Keys.dailyAgenda] = Json.encodeToString(value.normalized()) }
    }

    suspend fun setReminderMinutes(value: Int) {
        dataStore.edit { it[Keys.reminderMinutes] = value.coerceIn(0, 60) }
    }

    suspend fun setWidgetBackgroundAlpha(value: Float, glass: Boolean) {
        dataStore.edit { it[if (glass) Keys.glassWidgetBackgroundAlpha else Keys.widgetBackgroundAlpha] = value.coerceIn(0f, 1f) }
    }

    suspend fun setReminderDisplayMode(value: ReminderDisplayMode) {
        dataStore.edit { it[Keys.reminderDisplayMode] = value.name.lowercase() }
    }

    suspend fun setRemindOnHolidays(value: Boolean) {
        dataStore.edit { it[Keys.remindOnHolidays] = value }
    }

    suspend fun setCourseReminderOverride(courseId: String, minutes: Int?) {
        if (courseId.isBlank()) return
        dataStore.edit { values ->
            val overrides = values[Keys.courseReminderOverrides].toCourseReminderOverrides().toMutableMap()
            if (minutes == null) overrides.remove(courseId) else overrides[courseId] = minutes.coerceIn(0, 60)
            values[Keys.courseReminderOverrides] = overrides.toCourseReminderOverrideText()
        }
    }

    suspend fun setCourseReminderMessage(courseId: String, message: String?) {
        if (courseId.isBlank()) return
        dataStore.edit { values ->
            val messages = values[Keys.courseReminderMessages].toCourseReminderMessages().toMutableMap()
            val clean = message?.trim().orEmpty()
            if (clean.isBlank()) messages.remove(courseId) else messages[courseId] = clean.take(240)
            values[Keys.courseReminderMessages] = messages.toCourseReminderMessageText()
        }
    }

    suspend fun setCourseReminder(courseId: String, minutes: Int?, message: String?) {
        if (courseId.isBlank()) return
        dataStore.edit { values ->
            val overrides = values[Keys.courseReminderOverrides].toCourseReminderOverrides().toMutableMap()
            if (minutes == null) overrides.remove(courseId) else overrides[courseId] = minutes.coerceIn(0, 60)
            val messages = values[Keys.courseReminderMessages].toCourseReminderMessages().toMutableMap()
            val clean = message?.trim().orEmpty().take(240)
            if (clean.isEmpty()) messages.remove(courseId) else messages[courseId] = clean
            values[Keys.courseReminderOverrides] = overrides.toCourseReminderOverrideText()
            values[Keys.courseReminderMessages] = messages.toCourseReminderMessageText()
        }
    }

    suspend fun setBackgroundSource(value: BackgroundSource) {
        dataStore.edit { it[Keys.backgroundSource] = value.name.lowercase() }
    }

    suspend fun setBackgroundMotion(value: BackgroundMotion) {
        dataStore.edit { it[Keys.backgroundMotion] = value.name.lowercase() }
    }

    suspend fun setBackgroundCarouselEnabled(value: Boolean) {
        dataStore.edit { it[Keys.backgroundCarouselEnabled] = value }
    }

    suspend fun setBackgroundCarouselIntervalSeconds(value: Int) {
        dataStore.edit { it[Keys.backgroundCarouselIntervalSeconds] = value.coerceIn(1, 86_400) }
    }

    suspend fun setBackgroundImage(path: String, positionX: Float, positionY: Float) {
        dataStore.edit {
            it[Keys.backgroundImagePath] = path
            it[Keys.backgroundPositionX] = positionX.coerceIn(0f, 1f)
            it[Keys.backgroundPositionY] = positionY.coerceIn(0f, 1f)
            it[Keys.backgroundSource] = BackgroundSource.IMAGE.name.lowercase()
            if (it[Keys.backgroundMotion] == BackgroundMotion.OFF.name.lowercase()) {
                it[Keys.backgroundMotion] = BackgroundMotion.STATIC.name.lowercase()
            }
        }
    }

    suspend fun clearBackgroundImage(path: String) {
        dataStore.edit {
            if (it[Keys.backgroundImagePath] == path) {
                it.remove(Keys.backgroundImagePath)
                it[Keys.backgroundSource] = BackgroundSource.SCENE.name.lowercase()
            }
        }
    }

    suspend fun setGlassQuality(value: GlassQuality) {
        dataStore.edit { it[Keys.glassQuality] = value.name.lowercase() }
    }

    suspend fun setFunctionalAlpha(value: Float) {
        dataStore.edit { it[Keys.functionalAlpha] = value.coerceIn(.15f, .80f) }
    }

    suspend fun setFunctionalBlur(value: Float) {
        dataStore.edit { it[Keys.functionalBlur] = value.coerceIn(0f, 30f) }
    }

    suspend fun setContentAlpha(value: Float) {
        dataStore.edit { it[Keys.contentAlpha] = value.coerceIn(.20f, .95f) }
    }

    suspend fun setContentBlur(value: Float) {
        dataStore.edit { it[Keys.contentBlur] = value.coerceIn(0f, 30f) }
    }

    suspend fun setWeekendDisplayMode(value: WeekendDisplayMode) {
        dataStore.edit { it[Keys.weekendDisplayMode] = value.name.lowercase() }
    }

    suspend fun setCoursePresetColors(value: List<Int>) {
        dataStore.edit { it[Keys.coursePresetColors] = value.distinct().joinToString(",") }
    }

    suspend fun setTextPrimaryColor(value: Int?) = setOptionalTextColor(Keys.textPrimaryColor, value)

    suspend fun setTextSecondaryColor(value: Int?) = setOptionalTextColor(Keys.textSecondaryColor, value)

    suspend fun setTextTertiaryColor(value: Int?) = setOptionalTextColor(Keys.textTertiaryColor, value)

    suspend fun setFontSizeMode(value: FontSizeMode) {
        dataStore.edit { it[Keys.fontSizeMode] = value.name.lowercase() }
    }

    suspend fun setFontScale(value: Float) {
        dataStore.edit { it[Keys.fontScale] = value.coerceIn(.75f, 1.6f) }
    }

    private suspend fun setOptionalTextColor(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Int?) {
        dataStore.edit { values ->
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    suspend fun resetGlassParameters() {
        resetFunctionalGlassParameters()
        resetContentGlassParameters()
    }

    suspend fun resetFunctionalGlassParameters() {
        dataStore.edit {
            it[Keys.functionalAlpha] = .45f
            it[Keys.functionalBlur] = 13f
        }
    }

    suspend fun resetContentGlassParameters() {
        dataStore.edit {
            it[Keys.contentAlpha] = .50f
            it[Keys.contentBlur] = 14f
        }
    }
}

private fun String?.toHomePreference(): HomePreference = when (this) {
    "calendar" -> HomePreference.CALENDAR
    "week" -> HomePreference.WEEK
    "last" -> HomePreference.LAST
    else -> HomePreference.TODAY
}

private fun String?.toHomeView(): HomeView = when (this ?: DEFAULT_LAST_DESTINATION) {
    "calendar" -> HomeView.CALENDAR
    "week" -> HomeView.WEEK
    else -> HomeView.TODAY
}

private fun String?.toThemeMode(): QztcThemeMode = when (this) {
    "light" -> QztcThemeMode.LIGHT
    "dark" -> QztcThemeMode.DARK
    else -> QztcThemeMode.SYSTEM
}

private fun String?.toFontSizeMode(): FontSizeMode = when (this) {
    "auto_fill" -> FontSizeMode.AUTO_FILL
    "custom" -> FontSizeMode.CUSTOM
    else -> FontSizeMode.DEFAULT
}

private fun String?.toBackgroundSource(): BackgroundSource = when (this) {
    "image" -> BackgroundSource.IMAGE
    else -> BackgroundSource.SCENE
}

private fun String?.toBackgroundMotion(): BackgroundMotion = when (this) {
    "static" -> BackgroundMotion.STATIC
    "off" -> BackgroundMotion.OFF
    else -> BackgroundMotion.DYNAMIC
}

private fun String?.toGlassQuality(): GlassQuality = when (this) {
    "blur_tint" -> GlassQuality.BLUR_TINT
    "solid_surface" -> GlassQuality.SOLID_SURFACE
    else -> GlassQuality.FULL_LENS
}

internal fun String?.toWeekendDisplayMode(): WeekendDisplayMode = when (this) {
    "show_all" -> WeekendDisplayMode.SHOW_ALL
    "fold_empty" -> WeekendDisplayMode.FOLD_EMPTY
    "hide_empty" -> WeekendDisplayMode.HIDE_EMPTY
    else -> WeekendDisplayMode.HIDE_EMPTY
}

private fun String?.toCoursePresetColors(): List<Int> =
    if (this == null) {
        CoursePalette.indices.toList()
    } else if (this.isBlank()) {
        emptyList()
    } else {
        this.split(',').mapNotNull { it.trim().toIntOrNull() }.distinct().let { values ->
            if(values==(0..7).toList())CoursePalette.indices.toList() else values
        }
    }

private fun String?.toCourseReminderOverrides(): Map<String, Int> =
    this.orEmpty()
        .lineSequence()
        .mapNotNull { line ->
            val divider = line.lastIndexOf('\t')
            if (divider <= 0) return@mapNotNull null
            val courseId = line.take(divider)
            val minutes = line.drop(divider + 1).toIntOrNull()?.takeIf { it in 0..60 } ?: return@mapNotNull null
            courseId to minutes
        }
        .toMap()

internal fun String?.toReminderDisplayMode(): ReminderDisplayMode = when (this) {
    "notification_bar" -> ReminderDisplayMode.NOTIFICATION_BAR
    else -> ReminderDisplayMode.SCREEN_POPUP
}

internal fun String?.toCourseReminderMessages(): Map<String, String> {
    val text = this.orEmpty()
    if (text.trimStart().startsWith("{")) {
        return runCatching { Json.decodeFromString<Map<String, String>>(text) }
            .getOrDefault(emptyMap())
            .filter { (id, message) -> id.isNotBlank() && message.isNotBlank() }
            .mapValues { (_, message) -> message.trim().take(240) }
    }
    // Preserve text saved by the earlier tab-separated preference format.
    return text.lineSequence().mapNotNull { line ->
        val divider = line.indexOf('\t')
        if (divider <= 0) return@mapNotNull null
        val id = line.take(divider)
        val message = line.drop(divider + 1).trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        id to message.take(240)
    }.toMap()
}

private fun Map<String, Int>.toCourseReminderOverrideText(): String =
    entries
        .filter { (courseId, minutes) -> courseId.isNotBlank() && minutes in 0..60 && '\t' !in courseId && '\n' !in courseId }
        .sortedBy { it.key }
        .joinToString("\n") { "${it.key}\t${it.value}" }

internal fun Map<String, String>.toCourseReminderMessageText(): String =
    Json.encodeToString<Map<String, String>>(filter { (id, message) -> id.isNotBlank() && message.isNotBlank() }
        .mapValues { (_, message) -> message.trim().take(240) }.toSortedMap())
