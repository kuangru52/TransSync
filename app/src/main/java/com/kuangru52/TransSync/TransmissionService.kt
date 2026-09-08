package com.kuangru52.transsync

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface TransmissionService {
    @POST
    fun rpc(
        @Url url: String,
        @Header("X-Transmission-Session-Id") sessionId: String?,
        @Body request: RpcRequest
    ): Call<RpcResponse<Map<String, Any>>>

    @POST
    fun getTorrents(
        @Url url: String,
        @Header("X-Transmission-Session-Id") sessionId: String?,
        @Body request: RpcRequest
    ): Call<RpcResponse<TorrentListArguments>>
}

