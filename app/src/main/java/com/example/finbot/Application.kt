package com.example.finbot

import android.app.Application
import com.example.finbot.util.ThemeManager

class FinBotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply theme at app startup
        ThemeManager.applyTheme(this)
    }
}