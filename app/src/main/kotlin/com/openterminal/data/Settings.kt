package com.openterminal.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppTheme(val label: String) {
    Aurora("Aurora"),
    Midnight("Midnight"),
    Amoled("AMOLED"),
    System("System"),
}

enum class TerminalPalette(val label: String, val fg: Int, val bg: Int, val cursor: Int) {
    Default("Default", 0xFFE6EDF3.toInt(), 0xFF0D1117.toInt(), 0xFF7DD3FC.toInt()),
    Solarized("Solarized", 0xFF93A1A1.toInt(), 0xFF002B36.toInt(), 0xFFB58900.toInt()),
    Dracula("Dracula", 0xFFF8F8F2.toInt(), 0xFF282A36.toInt(), 0xFFFF79C6.toInt()),
    Green("Phosphor", 0xFF33FF66.toInt(), 0xFF000000.toInt(), 0xFF33FF66.toInt()),
}

/** User preferences, persisted in SharedPreferences and observable from Compose. */
object Settings {
    private lateinit var prefs: SharedPreferences

    var theme by mutableStateOf(AppTheme.Aurora)
    var palette by mutableStateOf(TerminalPalette.Default)
    var blurRadius by mutableFloatStateOf(8f)
    var lensStrength by mutableFloatStateOf(1f)
    var glassOpacity by mutableFloatStateOf(0.35f)
    var fontSize by mutableIntStateOf(14)
    var scrollbackLines by mutableIntStateOf(5000)
    var showKeyBar by mutableStateOf(true)
    var showSuggestions by mutableStateOf(true)
    var haptics by mutableStateOf(true)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        theme = runCatching { AppTheme.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(AppTheme.Aurora)
        palette = runCatching { TerminalPalette.valueOf(prefs.getString("palette", null) ?: "") }.getOrDefault(TerminalPalette.Default)
        blurRadius = prefs.getFloat("blur", 8f)
        lensStrength = prefs.getFloat("lens", 1f)
        glassOpacity = prefs.getFloat("opacity", 0.35f)
        fontSize = prefs.getInt("font", 14)
        scrollbackLines = prefs.getInt("scrollback", 5000)
        showKeyBar = prefs.getBoolean("keybar", true)
        showSuggestions = prefs.getBoolean("suggest", true)
        haptics = prefs.getBoolean("haptics", true)
    }

    fun save() {
        prefs.edit()
            .putString("theme", theme.name)
            .putString("palette", palette.name)
            .putFloat("blur", blurRadius)
            .putFloat("lens", lensStrength)
            .putFloat("opacity", glassOpacity)
            .putInt("font", fontSize)
            .putInt("scrollback", scrollbackLines)
            .putBoolean("keybar", showKeyBar)
            .putBoolean("suggest", showSuggestions)
            .putBoolean("haptics", haptics)
            .apply()
    }
}
