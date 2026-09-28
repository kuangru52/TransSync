package com.kuangru52.transsync

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

data class IpApiResponse(
    val countryCode: String?,
)

interface IpApi {
    @GET("{ip}?fields=countryCode")
    fun getCountry(@Path("ip") ip: String): Call<IpApiResponse>
}

/**
 * IP 地理位置与国旗 Emoji 转换服务 (GeoIpService.kt)
 *
 * 【作用与功能】：
 * - 查询节点 Peer IP 的地理位置国旗 Emoji 图标；
 * - 使用 Retrofit 异步请求 ip-api.com 接口解析 ISO 国家代码，并转为 Unicode 国旗 Emoji 字符；
 * - 内置内存 Map 缓存，避免重复网络查询。
 */
object GeoIpService {
    private val cache = mutableMapOf<String, String>()
    private val api = Retrofit.Builder()
        .baseUrl("http://ip-api.com/json/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(IpApi::class.java)

    fun getCountryEmoji(ip: String, callback: (String?) -> Unit) {
        val cleanIp = ip.substringBefore(":").substringBefore("%")
        
        if (cache.containsKey(cleanIp)) {
            callback(cache[cleanIp])
            return
        }

        api.getCountry(cleanIp).enqueue(
            object : Callback<IpApiResponse> {
                override fun onResponse(call: Call<IpApiResponse>, response: Response<IpApiResponse>) {
                    val countryCode = response.body()?.countryCode
                    val emoji = countryCode?.let { codeToEmoji(it) }
                    emoji?.let {
                        cache[cleanIp] = it
                    }
                    callback(emoji)
                }

                override fun onFailure(call: Call<IpApiResponse>, t: Throwable) {
                    callback(null)
                }
            },
        )
    }

    private fun codeToEmoji(countryCode: String): String {
        val firstLetter = (Character.codePointAt(countryCode, 0) - 0x41) + 0x1F1E6
        val secondLetter = (Character.codePointAt(countryCode, 1) - 0x41) + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }
}

