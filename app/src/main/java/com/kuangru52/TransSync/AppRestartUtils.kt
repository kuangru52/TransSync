package com.kuangru52.transsync

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/**
 * 应用重启工具类 (AppRestartUtils)
 *
 * 【作用与功能】：
 * - 提供无缝重启应用进程的全局静态方法；
 * - 当切换服务器客户端类型 (Transmission <-> qBittorrent) 或切换语言/主题时，清空全量 Activity 任务栈并重新拉起 Launcher 主入口；
 * - 随后彻底杀死旧进程，确保客户端单例、内存缓存及网络连接 100% 干净重启生效。
 */
object AppRestartUtils {
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
