package com.kuangru52.transsync

import java.util.UUID

/**
 * 下载器服务器配置实体数据类 (支持 Transmission 与 qBittorrent)
 * @param id 唯一标识符 UUID
 * @param alias 服务器备注/别名 (例如: "家中 NAS", "云端 VPS")
 * @param clientType 客户端类型: "transmission" 或 "qbittorrent"，默认为 "transmission"
 * @param rpcUrl 服务器 Web/RPC 地址 (例如: "http://192.168.1.100:9091/transmission/rpc" 或 "http://192.168.1.100:8080")
 * @param user 认证用户名
 * @param pass 认证密码
 * @param isActive 是否为当前选中的活动服务器
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
