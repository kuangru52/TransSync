package com.kuangru52.transsync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * 多 Transmission 服务器配置持久化管理器：
 * - 允许添加/修改/删除多个服务器并设置备注名 (如 "家中 NAS", "云端 VPS")
 * - 一键快速切换活动服务器
 * - 自动迁移旧版单服务器配置
 */
object ServerManager {

    private const val PREFS_NAME = "servers_prefs"
    private const val KEY_SERVERS_JSON = "servers_json_list"
    private const val KEY_ACTIVE_SERVER_ID = "active_server_id"

    var serversVersion by mutableIntStateOf(0)

    /**
     * 获取所有已配置的服务器列表
     */
    fun getServers(context: Context): List<ServerConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SERVERS_JSON, null)
        val activeId = prefs.getString(KEY_ACTIVE_SERVER_ID, "") ?: ""

        val list = mutableListOf<ServerConfig>()

        if (!jsonStr.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id")
                    val alias = obj.optString("alias", "Transmission")
                    val clientType = obj.optString("clientType", ServerConfig.CLIENT_TRANSMISSION)
                    val rpcUrl = obj.optString("rpcUrl")
                    val user = obj.optString("user", "")
                    val pass = obj.optString("pass", "")
                    val avatarUri = obj.optString("avatarUri", "")

                    if (rpcUrl.isNotBlank()) {
                        list.add(
                            ServerConfig(
                                id = id,
                                alias = alias,
                                clientType = clientType,
                                rpcUrl = rpcUrl,
                                user = user,
                                pass = pass,
                                isActive = (id == activeId),
                                avatarUri = avatarUri,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 若无多服务器配置，自动平滑迁移旧版 auth SharedPreferences 凭据
        if (list.isEmpty()) {
            val legacyServer = migrateLegacyServer(context)
            if (legacyServer != null) {
                list.add(legacyServer.copy(isActive = true))
                saveServersToPrefs(context, list, legacyServer.id)
            }
        } else {
            // 确保必定有一个活动服务器
            if (list.none { it.isActive }) {
                val first = list.first()
                val updatedList = list.mapIndexed { index, config ->
                    if (index == 0) config.copy(isActive = true) else config
                }
                list.clear()
                list.addAll(updatedList)
                saveServersToPrefs(context, list, first.id)
            }
        }

        return list
    }

    /**
     * 获取当前活动的 Transmission 服务器配置
     */
    fun getActiveServer(context: Context): ServerConfig? {
        val servers = getServers(context)
        return servers.find { it.isActive } ?: servers.firstOrNull()
    }

    /**
     * 切换当前选中的活动服务器
     */
    fun setActiveServer(context: Context, serverId: String) {
        val servers = getServers(context)
        val updated = servers.map { it.copy(isActive = (it.id == serverId)) }
        saveServersToPrefs(context, updated, serverId)
    }

    /**
     * 保存或更新服务器配置 (根据 ID 匹配)
     */
    fun saveServer(context: Context, config: ServerConfig) {
        val servers = getServers(context).toMutableList()
        val index = servers.indexOfFirst { it.id == config.id }

        if (index >= 0) {
            servers[index] = config
        } else {
            servers.add(config)
        }

        val activeId = if (config.isActive || servers.none { it.isActive }) config.id else getActiveServer(context)?.id ?: config.id
        val updated = servers.map { it.copy(isActive = (it.id == activeId)) }
        saveServersToPrefs(context, updated, activeId)
    }

    /**
     * 删除指定 ID 的服务器配置
     */
    fun deleteServer(context: Context, serverId: String) {
        val servers = getServers(context).toMutableList()
        servers.removeAll { it.id == serverId }

        if (servers.isNotEmpty()) {
            val newActiveId = if (servers.none { it.isActive }) servers.first().id else getActiveServer(context)?.id ?: servers.first().id
            val updated = servers.map { it.copy(isActive = (it.id == newActiveId)) }
            saveServersToPrefs(context, updated, newActiveId)
        } else {
            saveServersToPrefs(context, emptyList(), "")
        }
    }

    private fun saveServersToPrefs(context: Context, list: List<ServerConfig>, activeId: String) {
        val jsonArray = JSONArray()
        for (server in list) {
            val obj = JSONObject().apply {
                put("id", server.id)
                put("alias", server.alias)
                put("clientType", server.clientType)
                put("rpcUrl", server.rpcUrl)
                put("user", server.user)
                put("pass", server.pass)
                put("avatarUri", server.avatarUri)
            }
            jsonArray.put(obj)
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            putString(KEY_SERVERS_JSON, jsonArray.toString())
            putString(KEY_ACTIVE_SERVER_ID, activeId)
        }

        TransmissionClient.clearCache()
        QBittorrentClient.clearCache()
        serversVersion += 1

        // 同步更新旧版 auth SharedPreferences 兼容旧逻辑
        val activeServer = list.find { it.id == activeId } ?: list.firstOrNull()
        if (activeServer != null) {
            context.getSharedPreferences("auth", Context.MODE_PRIVATE).edit(commit = true) {
            putString("rpcUrl", activeServer.rpcUrl)
            putString("user", activeServer.user)
            putString("pass", activeServer.pass)
        }
        }
    }

    private fun migrateLegacyServer(context: Context): ServerConfig? {
        val authPrefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
        val url = authPrefs.getString("rpcUrl", "") ?: ""
        val user = authPrefs.getString("user", "") ?: ""
        val pass = authPrefs.getString("pass", "") ?: ""

        if (url.isNotBlank()) {
            return ServerConfig(
                alias = "主服务器",
                rpcUrl = url,
                user = user,
                pass = pass,
                isActive = true
            )
        }
        return null
    }
}
