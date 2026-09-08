package com.kuangru52.transsync

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.content.edit

/**
 * Manages download directories, including history, auto-detection from active torrents,
 * and AutoCompleteTextView adapter setup.
 */
object DownloadDirManager {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_HISTORY_DIRS = "history_dirs"

    /**
     * Gets all known download directories (history + currently active torrent directories).
     * Also synchronizes active directories into history.
     */
    fun getAllDirs(context: Context, currentTorrents: List<Torrent>?): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyDirs = prefs.getStringSet(KEY_HISTORY_DIRS, emptySet())?.toMutableSet() ?: mutableSetOf()
        
        val activeDirs = currentTorrents?.mapNotNull { it.downloadDir }
            ?.filter { it.isNotBlank() }
            ?.toSet() ?: emptySet()
        
        // Sync active directories to history for "sniffing" functionality
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
     * Saves a new directory to history.
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
     * Binds a standardized adapter to an AutoCompleteTextView.
     */
    fun setupAdapter(etDir: AutoCompleteTextView, allDirs: List<String>) {
        val adapter = ArrayAdapter(etDir.context, R.layout.item_dropdown_compact, allDirs)
        etDir.setAdapter(adapter)
        etDir.threshold = 0
        
        // Ensure dropdown shows on click
        etDir.setOnClickListener {
            if (!etDir.isPopupShowing) {
                etDir.showDropDown()
            }
        }
    }
}
