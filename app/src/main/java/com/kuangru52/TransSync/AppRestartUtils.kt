package com.kuangru52.transsync

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

object AppRestartUtils {
    /**
     * 无缝重启本应用进程：
     * - 清空全量 Activity 任务栈并从 Launcher 主入口重新拉起应用，随后彻底杀死旧进程，保证跨客户端类型 (Transmission <-> qBittorrent) 100% 内存干净生效
     */
    fun restartApp(context: Context) {
        try {
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            if (intent != null) {
                val componentName = intent.component
                val mainIntent = Intent.makeRestartActivityTask(componentName)
                context.startActivity(mainIntent)
                exitProcess(0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
