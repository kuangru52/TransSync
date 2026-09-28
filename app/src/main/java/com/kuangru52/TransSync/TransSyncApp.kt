package com.kuangru52.transsync

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 应用程序 Application 基类 (TransSyncApp.kt)
 *
 * 【作用与功能】：
 * - 应用程序入口 Application 类；
 * - 启动初始化：在 onCreate 时同步加载并应用保存的主题模式与多语言配置；
 * - 启动后台 Worker：注册 15 分钟周期性轮询 WorkManager 任务 [TorrentCheckWorker]。
 */
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
