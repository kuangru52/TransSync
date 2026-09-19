package com.kuangru52.transsync

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FormatUtils {
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceAtMost(units.size - 1)
        return String.format(
            Locale.US,
            "%.1f %s",
            bytes / 1024.0.pow(digitGroups.toDouble()),
            units[digitGroups],
        )
    }

    fun formatSpeed(rateBytesPerSec: Double): String {
        if (rateBytesPerSec <= 0) return "0\u00A0KB/s"
        val kbs = rateBytesPerSec / 1024.0
        return if (kbs < 1024) {
            String.format(Locale.US, "%.1f\u00A0KB/s", kbs)
        } else {
            String.format(Locale.US, "%.1f\u00A0MB/s", kbs / 1024.0)
        }
    }

    fun formatDate(epochSeconds: Long): String {
        if (epochSeconds <= 0) return "--"
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(java.util.Date(epochSeconds * 1000L))
    }

    /**
     * 格式化种子标题，增强排版可读性：
     * 1. 遇到点号 '.' 允许自然折行 (在 '.' 后面插入 \u200B 零宽空格作为自然断点)
     * 2. 保护 x265, x264, WEB-DL, BluRay, H.265 等专业词汇不被拆断显示
     */
    fun formatTorrentTitle(title: String): String {
        if (title.isEmpty()) return title
        val formatted = title
            .replace("WEB-DL", "WEB\u2011DL", ignoreCase = true)
            .replace("WEB-RIP", "WEB\u2011RIP", ignoreCase = true)
            .replace("HQ-WEB", "HQ\u2011WEB", ignoreCase = true)
            .replace("H.265", "H\u2024265", ignoreCase = true)
            .replace("H.264", "H\u2024264", ignoreCase = true)

        return formatted.replace(".", ".\u200B")
    }
}
