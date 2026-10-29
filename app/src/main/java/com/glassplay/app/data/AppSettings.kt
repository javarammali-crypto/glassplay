package com.glassplay.app.data

import android.content.SharedPreferences
import com.glassplay.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How subtitles look in the player. */
data class SubtitleStyle(
    val textColor: SubtitleColor = SubtitleColor.WHITE,
    val background: SubtitleBackground = SubtitleBackground.SHADOW,
    val size: SubtitleSize = SubtitleSize.MEDIUM,
)

enum class SubtitleColor(val label: String, val argb: Int) {
    WHITE("White", 0xFFFFFFFF.toInt()),
    YELLOW("Yellow", 0xFFFFE14D.toInt()),
    CYAN("Cyan", 0xFF5CE1FF.toInt()),
    GREEN("Green", 0xFF7CFF8A.toInt()),
    PINK("Pink", 0xFFFF8AD8.toInt()),
}

enum class SubtitleBackground(val label: String, val argb: Int) {
    /** No box; a soft shadow keeps the text readable. */
    SHADOW("None", 0x00000000),
    LIGHT("Light", 0x66000000),
    DARK("Dark", 0xB3000000.toInt()),
    SOLID("Solid", 0xFF000000.toInt()),
}

enum class SubtitleSize(val label: String, val scale: Float) {
    SMALL("Small", 0.8f),
    MEDIUM("Medium", 1f),
    LARGE("Large", 1.3f),
    HUGE("Extra large", 1.65f),
}

/** App preferences, saved on the phone. */
class AppSettings(private val prefs: SharedPreferences) {
    private val _theme = MutableStateFlow(enumOr(prefs.getString(KEY_THEME, null), ThemeMode.SYSTEM))
    val theme: StateFlow<ThemeMode> = _theme

    private val _subtitles = MutableStateFlow(
        SubtitleStyle(
            textColor = enumOr(prefs.getString(KEY_SUB_COLOR, null), SubtitleColor.WHITE),
            background = enumOr(prefs.getString(KEY_SUB_BG, null), SubtitleBackground.SHADOW),
            size = enumOr(prefs.getString(KEY_SUB_SIZE, null), SubtitleSize.MEDIUM),
        )
    )
    val subtitles: StateFlow<SubtitleStyle> = _subtitles

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setSubtitles(style: SubtitleStyle) {
        _subtitles.value = style
        prefs.edit()
            .putString(KEY_SUB_COLOR, style.textColor.name)
            .putString(KEY_SUB_BG, style.background.name)
            .putString(KEY_SUB_SIZE, style.size.name)
            .apply()
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_SUB_COLOR = "subtitle_color"
        const val KEY_SUB_BG = "subtitle_background"
        const val KEY_SUB_SIZE = "subtitle_size"

        inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
            name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
    }
}
