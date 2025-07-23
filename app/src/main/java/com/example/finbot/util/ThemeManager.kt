package com.example.finbot.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    private const val THEME_PREF = "theme_preferences"
    private const val THEME_KEY = "selected_theme"

    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM = "system"

    fun applyTheme(context: Context) {
        val theme = getSelectedTheme(context)
        when (theme) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun setTheme(context: Context, theme: String) {
        val preferences = getPreferences(context)
        preferences.edit().putString(THEME_KEY, theme).apply()
        applyTheme(context)
    }

    fun getSelectedTheme(context: Context): String {
        val preferences = getPreferences(context)
        return preferences.getString(THEME_KEY, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(THEME_PREF, Context.MODE_PRIVATE)
    }

    fun getCurrentThemeMode(): Int {
        return AppCompatDelegate.getDefaultNightMode()
    }

    fun isDarkMode(context: Context): Boolean {
        val selectedTheme = getSelectedTheme(context)
        return when (selectedTheme) {
            THEME_DARK -> true
            THEME_LIGHT -> false
            THEME_SYSTEM -> {
                val currentMode = context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                currentMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            else -> false
        }
    }
}