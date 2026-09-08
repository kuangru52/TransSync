package com.kuangru52.transsync

import android.util.Log
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.util.concurrent.ConcurrentHashMap

object TransmissionClient {
    private const val TAG = "TransmissionClient"
    private val serviceCache = ConcurrentHashMap<String, TransmissionService>()
    private var sessionId: String? = null

    /**
     * 获取或创建缓存的 RPC 服务实例
     */
    fun getService(rpcUrl: String, user: String, pass: String): TransmissionService {
        val baseUrl = rpcUrl.substringBefore("/transmission/rpc") + "/"
        val cacheKey = "$baseUrl|$user|$pass"
        return serviceCache.getOrPut(cacheKey) {
            createService(baseUrl, user, pass)
        }
    }

    private fun createService(baseUrl: String, user: String, pass: String): TransmissionService {
        val client = getOkHttpClient(user, pass)
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        .create(TransmissionService::class.java)
    }

    private fun getOkHttpClient(user: String, pass: String): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        val logging = HttpLoggingInterceptor { message ->
            Log.d(TAG, "OkHttp: $message")
        }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor { chain ->
                val original = chain.request()
                val auth = Credentials.basic(user, pass, Charsets.UTF_8)
                
                val requestBuilder = original.newBuilder()
                    .header("Authorization", auth)
                    .header("Accept", "application/json")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("User-Agent", "TransSync/1.0")
                
                sessionId?.let { requestBuilder.header("X-Transmission-Session-Id", it) }
                
                var response = chain.proceed(requestBuilder.build())
                
                if (response.code == 409) {
                    val newSessionId = response.header("X-Transmission-Session-Id")
                    if (newSessionId != null) {
                        sessionId = newSessionId
                        response.close()
                        val retryRequest = original.newBuilder()
                            .header("Authorization", auth)
                            .header("X-Transmission-Session-Id", newSessionId)
                            .build()
                        response = chain.proceed(retryRequest)
                    }
                }
                response
            }
            .addInterceptor(logging)
            .build()
    }
}
