package com.kuangru52.transsync

/**
 * Tracker 域名解析与简称映射工具 (TrackerUtils.kt)
 *
 * 【作用与功能】：
 * - getTrackerNameFromUrl：提取 Tracker 宣告 URL 字符串中的核心域名；
 * - 优先比对匹配用户自定义的 domain=label 映射关系表（如 www.google.com -> Google）；
 * - 未匹配时自动提取 Host 主机名并剥离 "www." 兜底呈现。
 */
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
