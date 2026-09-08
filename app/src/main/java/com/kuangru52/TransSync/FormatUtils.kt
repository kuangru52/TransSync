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
            units[digitGroups]
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
}
