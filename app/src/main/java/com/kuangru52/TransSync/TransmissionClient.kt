package com.kuangru52.TransSync

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

import okhttp3.logging.HttpLoggingInterceptor
import android.util.Log

object TransmissionClient {
    private const val TAG = "TransmissionClient"
    private var retrofit: Retrofit? = null
    private var sessionId: String? = null

    fun getService(baseUrl: String, user: String, pass: String): TransmissionService {
        val client = getOkHttpClient(user, pass)
        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit!!.create(TransmissionService::class.java)
    }

    private fun getOkHttpClient(user: String, pass: String): OkHttpClient {
        try {
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
                    // Use UTF-8 for credentials to handle special characters
                    val auth = Credentials.basic(user, pass, Charsets.UTF_8)
                    
                    val requestBuilder = original.newBuilder()
                        .header("Authorization", auth)
                        .header("Accept", "application/json")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("User-Agent", "TransSync/1.0")
                    
                    sessionId?.let {
                        requestBuilder.header("X-Transmission-Session-Id", it)
                    }
                    
                    val request = requestBuilder.build()
                    var response = chain.proceed(request)
                    
                    if (response.code == 409) {
                        val newSessionId = response.header("X-Transmission-Session-Id")
                        if (newSessionId != null) {
                            sessionId = newSessionId
                            response.close()
                            
                            Log.d(TAG, "Updating Session ID and retrying...")
                            val newRequest = original.newBuilder()
                                .header("Authorization", auth)
                                .header("Accept", "application/json")
                                .header("X-Requested-With", "XMLHttpRequest")
                                .header("User-Agent", "TransSync/1.0")
                                .header("X-Transmission-Session-Id", newSessionId)
                                .build()
                            response = chain.proceed(newRequest)
                        }
                    } else if (response.code == 401) {
                        Log.e(TAG, "Unauthorized! Verify credentials and server settings.")
                    }

                    response
                }
                .addInterceptor(logging)
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}

