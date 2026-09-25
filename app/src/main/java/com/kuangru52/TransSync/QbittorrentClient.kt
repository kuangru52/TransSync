package com.kuangru52.transsync

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.lang.reflect.Type
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ToStringConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        if (type == String::class.java) {
            return Converter<ResponseBody, String> { value -> value.string() }
        }
        return null
    }
}

interface QBittorrentApiService {

    @FormUrlEncoded
    @POST("api/v2/auth/login")
    fun login(
        @Field("username") user: String,
        @Field("password") pass: String,
    ): Call<String>

    @GET("api/v2/torrents/info")
    fun getTorrentsInfo(
        @Query("filter") filter: String? = "all",
    ): Call<List<QbitTorrentInfo>>

    @GET("api/v2/transfer/info")
    fun getTransferInfo(): Call<QbitTransferInfo>

    @GET("api/v2/transfer/speedLimitsMode")
    fun getSpeedLimitsMode(): Call<String>

    @POST("api/v2/transfer/toggleSpeedLimitsMode")
    fun toggleSpeedLimitsMode(): Call<String>

    @FormUrlEncoded
    @POST("api/v2/torrents/pause")
    fun pauseTorrents(
        @Field("hashes") hashes: String
    ): Call<String>

    @FormUrlEncoded
    @POST("api/v2/torrents/stop")
    fun stopTorrents(
        @Field("hashes") hashes: String,
    ): Call<String>

    @FormUrlEncoded
    @POST("api/v2/torrents/resume")
    fun resumeTorrents(
        @Field("hashes") hashes: String
    ): Call<String>

    @FormUrlEncoded
    @POST("api/v2/torrents/start")
    fun startTorrents(
        @Field("hashes") hashes: String
    ): Call<String>

    @FormUrlEncoded
    @POST("api/v2/torrents/delete")
    fun deleteTorrents(
        @Field("hashes") hashes: String,
        @Field("deleteFiles") deleteFiles: Boolean
    ): Call<String>

    @FormUrlEncoded
    @POST("api/v2/torrents/setLocation")
    fun setLocation(
        @Field("hashes") hashes: String,
        @Field("location") location: String
    ): Call<String>

    @FormUrlEncoded
    @POST("api/v2/torrents/rename")
    fun renameTorrent(
        @Field("hash") hash: String,
        @Field("name") name: String
    ): Call<String>

    @POST("api/v2/torrents/add")
    fun addTorrent(
        @Body body: MultipartBody
    ): Call<String>

    @FormUrlEncoded
    @POST("api/v2/torrents/addTags")
    fun addTags(
        @Field("hashes") hashes: String,
        @Field("tags") tags: String
    ): Call<String>

    @Suppress("unused")
    @FormUrlEncoded
    @POST("api/v2/torrents/removeTags")
    fun removeTags(
        @Field("hashes") hashes: String,
        @Field("tags") tags: String,
    ): Call<String>

    @FormUrlEncoded
    @POST("api/v2/torrents/addTrackers")
    fun addTrackers(
        @Field("hash") hash: String,
        @Field("urls") urls: String,
    ): Call<String>

    @Suppress("unused")
    @FormUrlEncoded
    @POST("api/v2/torrents/editTracker")
    fun editTracker(
        @Field("hash") hash: String,
        @Field("origUrl") origUrl: String,
        @Field("newUrl") newUrl: String,
    ): Call<String>

    @GET("api/v2/torrents/trackers")
    fun getTorrentTrackers(
        @Query("hash") hash: String
    ): Call<List<QbitTrackerItem>>

    @GET("api/v2/sync/torrentPeers")
    fun getTorrentPeers(
        @Query("hash") hash: String
    ): Call<QbitPeersResponse>

    @GET("api/v2/torrents/files")
    fun getTorrentFiles(
        @Query("hash") hash: String
    ): Call<List<QbitFileInfo>>

    @FormUrlEncoded
    @POST("api/v2/torrents/recheck")
    fun recheckTorrents(
        @Field("hashes") hashes: String
    ): Call<String>

    @FormUrlEncoded
    @POST("api/v2/torrents/reannounce")
    fun reannounceTorrents(
        @Field("hashes") hashes: String
    ): Call<String>
}

object QBittorrentClient {

    private val clients = ConcurrentHashMap<String, QBittorrentApiService>()

    fun clearCache() {
        clients.clear()
    }

    // 存保存 Session Cookie 的 CookieJar
    private class InMemoryCookieJar : CookieJar {
        private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    @android.annotation.SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    fun getService(baseUrl: String): QBittorrentApiService {
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return clients.getOrPut(cleanUrl) {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            val okHttpClient = OkHttpClient.Builder()
                .cookieJar(InMemoryCookieJar())
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            Retrofit.Builder()
                .baseUrl(cleanUrl)
                .client(okHttpClient)
                .addConverterFactory(ToStringConverterFactory())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(QBittorrentApiService::class.java)
        }
    }
}
