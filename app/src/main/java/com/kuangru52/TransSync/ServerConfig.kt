package com.kuangru52.transsync

import java.util.UUID

/**
 * 下载器服务器配置实体数据类 (ServerConfig.kt)
 *
 * 【作用与功能】：
 * - 定义服务器连接配置的数据结构；
 * - 包含服务器 UUID (id)、别名 (alias)、客户端类型 (clientType: Transmission / qBittorrent)、RPC 地址 (rpcUrl)、用户名 (user)、密码 (pass)、活动标识 (isActive) 以及自定义头像路径 (avatarUri)；
 * - 常量定义：[CLIENT_TRANSMISSION] ("transmission") 与 [CLIENT_QBITTORRENT] ("qbittorrent")。
 */
data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val alias: String,
    val clientType: String = CLIENT_TRANSMISSION,
    val rpcUrl: String,
    val user: String = "",
    val pass: String = "",
    val isActive: Boolean = false,
    val avatarUri: String = "",
) {
    companion object {
        const val CLIENT_TRANSMISSION = "transmission"
        const val CLIENT_QBITTORRENT = "qbittorrent"
    }
}
