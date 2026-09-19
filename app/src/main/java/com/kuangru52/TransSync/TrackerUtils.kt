package com.kuangru52.transsync

object TrackerUtils {
    fun getTrackerNameFromUrl(url: String, customMappings: Map<String, String> = emptyMap()): String? {
        if (url.isEmpty()) return null

        // 匹配用户自定义的 Tracker 映射
        for ((key, value) in customMappings) {
            if (url.contains(key, ignoreCase = true)) return value
        }

        // 默认兜底逻辑：提取 Host 主机域名
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: url
            host.removePrefix("www.").substringBefore(":")
        } catch (_: Exception) {
            url.substringBefore("/")
        }
    }
}
