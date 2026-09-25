package com.kuangru52.transsync

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class TransSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 初始化应用主题模式 (深色/浅色/跟随系统) 与语言设置
        SettingsManager.applySettingsOnAppStart(this)

        setupBackgroundWorker()
    }

    private fun setupBackgroundWorker() {
        val workRequest = PeriodicWorkRequestBuilder<TorrentCheckWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "TorrentCheckWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }
}
