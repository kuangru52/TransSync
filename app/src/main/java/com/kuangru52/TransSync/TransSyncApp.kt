package com.kuangru52.TransSync

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class TransSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 搴旂敤鐢ㄦ埛淇濆瓨鐨勪富棰樻ā锟?
        val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val mode = themePrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}

