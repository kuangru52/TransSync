package com.kuangru52.transsync

import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection

/**
 * SSL 证书类型智能探测工具：
 * - 区分 Let's Encrypt / DigiCert / Cloudflare 等公网系统受信任 CA 证书与 mkcert / OpenSSL / 局域网 IP 等自签名证书
 */
object SslCheckUtils {

    private val selfSignedCache = ConcurrentHashMap<String, Boolean>()

    /**
     * 检测指定的 HTTPS 地址是否属于自签名 / mkcert / 未通过原生系统 CA 校验的证书
     */
    fun isSelfSignedSsl(rpcUrl: String): Boolean {
        val cleanUrl = rpcUrl.trim()
        if (!cleanUrl.lowercase().startsWith("https://")) return false

        return selfSignedCache.getOrPut(cleanUrl) {
            try {
                val url = URL(cleanUrl)
                val host = url.host ?: ""

                // 1. 局域网/私有 IP 地址 (192.168.*, 10.*, 172.16-31.*, 127.0.0.1, localhost) 瞬间判定为自签名/私有 SSL
                if (isPrivateHost(host)) {
                    return@getOrPut true
                }

                // 2. 公网域名尝试通过原生 TrustManager 校验
                val conn = url.openConnection() as HttpsURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                conn.requestMethod = "HEAD"
                conn.connect()
                conn.disconnect()
                false // 系统默认 TrustManager 校验成功 -> 说明属于 Let's Encrypt 等权威公网 CA，非自签名！
            } catch (_: Exception) {
                true // 抛出 SSLHandshakeException -> 判定为 mkcert / 自签名证书
            }
        }
    }

    private fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase()
        return (h == "localhost" || h == "127.0.0.1" ||
                h.startsWith("192.168.") ||
                h.startsWith("10.") ||
                h.startsWith("172.16.") || h.startsWith("172.17.") || h.startsWith("172.18.") || h.startsWith("172.19.") ||
                h.startsWith("172.20.") || h.startsWith("172.21.") || h.startsWith("172.22.") || h.startsWith("172.23.") ||
                h.startsWith("172.24.") || h.startsWith("172.25.") || h.startsWith("172.26.") || h.startsWith("172.27.") ||
                h.startsWith("172.28.") || h.startsWith("172.29.") || h.startsWith("172.30.") || h.startsWith("172.31."))
    }
}
