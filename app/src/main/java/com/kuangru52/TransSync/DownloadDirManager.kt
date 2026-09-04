package com.kuangru52.transsync

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.content.edit

/**
 * 统一管理下载目录的获取、更新和 Adapter 绑定
 */
object DownloadDirManager {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_HISTORY_DIRS = "history_dirs"

    /**
     * 获取所有已知的下载目录（历史记录 + 当前正在运行的种子目录）
     */
    fun getAllDirs(context: Context, currentTorrents: List<Torrent>?): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyDirs = prefs.getStringSet(KEY_HISTORY_DIRS, emptySet())?.toMutableSet() ?: mutableSetOf()
        
        val activeDirs = currentTorrents?.mapNotNull { it.downloadDir }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        
        // 如果有活跃目录，自动同步到历史记录中，实现“嗅探”功能
        if (activeDirs.isNotEmpty()) {
            val originalSize = historyDirs.size
            historyDirs.addAll(activeDirs)
            if (historyDirs.size > originalSize) {
                prefs.edit { putStringSet(KEY_HISTORY_DIRS, historyDirs) }
            }
        }
        
        return historyDirs.filter { it.isNotBlank() }.distinct().sorted()
    }

    /**
     * 将一个新的目录保存到历史记录中
     */
    fun saveDirToHistory(context: Context, dir: String) {
        if (dir.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyDirs = prefs.getStringSet(KEY_HISTORY_DIRS, emptySet())?.toMutableSet() ?: mutableSetOf()
        
        if (historyDirs.add(dir)) {
            prefs.edit { putStringSet(KEY_HISTORY_DIRS, historyDirs) }
        }
    }

    /**
     * 为 AutoCompleteTextView 绑定统一的 Adapter
     */
    fun setupAdapter(etDir: AutoCompleteTextView, allDirs: List<String>) {
        val adapter = ArrayAdapter(etDir.context, R.layout.item_dropdown_compact, allDirs)
        etDir.setAdapter(adapter)
        etDir.threshold = 0
        
        // 确保点击触发下拉
        etDir.setOnClickListener {
            etDir.showDropDown()
        }
    }
}
