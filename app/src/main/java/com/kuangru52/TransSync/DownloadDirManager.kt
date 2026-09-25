package com.kuangru52.transsync

import android.content.Context
import androidx.core.content.edit

/**
 * 永久保存并管理按不同服务器分类隔离的下载路径历史记录与当前活动种子路径嗅探：
 * - 凡是应用嗅探到或用户输入过的任何目录，均按当前服务器 ID 独立隔离并永久存入 SharedPreferences 磁盘
 * - 即使后续这些目录中的所有种子任务被删除，该服务器的历史目录也绝对不会丢失
 */
object DownloadDirManager {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_HISTORY_DIRS_PREFIX = "history_dirs_server_"

    private fun getStorageKey(context: Context, serverId: String? = null): String {
        val effectiveServerId = serverId?.ifBlank { null }
            ?: ServerManager.getActiveServer(context)?.id
            ?: "default"
        return "$KEY_HISTORY_DIRS_PREFIX$effectiveServerId"
    }

    /**
     * 获取指定服务器所有已知并已永久保存的下载目录（包含该服务器的历史记录 + 当前活动种子目录）
     */
    fun getAllDirs(context: Context, currentTorrents: List<Torrent>?, serverId: String? = null): List<String> {
        val storageKey = getStorageKey(context, serverId)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyDirs = prefs.getStringSet(storageKey, emptySet())?.toMutableSet() ?: mutableSetOf()

        val activeDirs = currentTorrents?.asSequence()?.mapNotNull { it.downloadDir }
            ?.filter { it.isNotBlank() }
            ?.toSet() ?: emptySet()

        if (activeDirs.isNotEmpty()) {
            val originalSize = historyDirs.size
            historyDirs.addAll(activeDirs)
            if (historyDirs.size > originalSize) {
                prefs.edit(commit = true) {
                    putStringSet(storageKey, historyDirs)
                }
            }
        }

        return historyDirs.asSequence().filter { it.isNotBlank() }.distinct().sorted().toList()
    }

    /**
     * 保存单一新路径到指定服务器的永久历史记录
     */
    fun saveDirToHistory(context: Context, dir: String, serverId: String? = null) {
        if (dir.isBlank()) return
        val storageKey = getStorageKey(context, serverId)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyDirs = prefs.getStringSet(storageKey, emptySet())?.toMutableSet() ?: mutableSetOf()

        if (historyDirs.add(dir.trim())) {
            prefs.edit(commit = true) {
                putStringSet(storageKey, historyDirs)
            }
        }
    }

    /**
     * 批量保存路径到指定服务器的永久历史记录
     */
    @Suppress("unused")
    fun saveDirsToHistory(context: Context, dirs: Collection<String>, serverId: String? = null) {
        val validDirs = dirs.asSequence().filter { it.isNotBlank() }.map { it.trim() }.toList()
        if (validDirs.isEmpty()) return

        val storageKey = getStorageKey(context, serverId)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyDirs = prefs.getStringSet(storageKey, emptySet())?.toMutableSet() ?: mutableSetOf()

        if (historyDirs.addAll(validDirs)) {
            prefs.edit(commit = true) {
                putStringSet(storageKey, historyDirs)
            }
        }
    }
}
