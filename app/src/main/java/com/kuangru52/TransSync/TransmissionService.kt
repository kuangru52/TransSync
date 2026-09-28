package com.kuangru52.transsync

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Transmission Retrofit RPC 接口定义 (TransmissionService.kt)
 *
 * 【作用与功能】：
 * - 定义连接 Transmission 的 Retrofit HTTP @POST 接口方法；
 * - rpc：发送通用 JSON-RPC 动作指令 (如 session-get, torrent-start, torrent-stop, torrent-set, torrent-remove)；
 * - getTorrents：查询获取种子列表数据。
 */
interface TransmissionService {
    @POST
    fun rpc(
        @Url url: String,
        @Header("X-Transmission-Session-Id") sessionId: String?,
        @Body request: RpcRequest,
    ): Call<RpcResponse<Map<String, Any>>>

    @POST
    fun getTorrents(
        @Url url: String,
        @Header("X-Transmission-Session-Id") sessionId: String?,
        @Body request: RpcRequest,
    ): Call<RpcResponse<TorrentListArguments>>
}

