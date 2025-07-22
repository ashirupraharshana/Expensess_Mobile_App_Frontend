package com.example.finbot

import android.app.Application
import com.example.finbot.util.ThemeManager

class FinBotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply saved theme on app start
        ThemeManager.applyTheme(this)
    }
}