package com.kuangru52.transsync

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FormatUtils {
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        return String.format(
            Locale.US,
            "%.1f %s",
            bytes / 1024.0.pow(digitGroups.toDouble()),
            units[digitGroups]
        )
    }

    fun formatSpeed(rateBytesPerSec: Double): String {
        if (rateBytesPerSec <= 0) return "0 KB/s"
        val kbs = rateBytesPerSec / 1024.0
        return if (kbs < 1024) {
            String.format(Locale.US, "%.1f KB/s", kbs)
        } else {
            String.format(Locale.US, "%.1f MB/s", kbs / 1024.0)
        }
    }
}
