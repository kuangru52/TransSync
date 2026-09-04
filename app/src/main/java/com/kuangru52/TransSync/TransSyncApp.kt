package com.kuangru52.transsync

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class TransSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 应用用户保存的主题模�?
        val themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val mode = themePrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)

        setupBackgroundWorker()
    }

    private fun setupBackgroundWorker() {
        val workRequest = PeriodicWorkRequestBuilder<TorrentCheckWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "TorrentCheckWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

