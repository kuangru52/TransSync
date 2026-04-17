package com.kuangru52.TransSync

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

data class IpApiResponse(
    val countryCode: String?
)

interface IpApi {
    @GET("{ip}?fields=countryCode")
    fun getCountry(@Path("ip") ip: String): Call<IpApiResponse>
}

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

        api.getCountry(cleanIp).enqueue(object : Callback<IpApiResponse> {
            override fun onResponse(call: Call<IpApiResponse>, response: Response<IpApiResponse>) {
                val countryCode = response.body()?.countryCode
                val emoji = countryCode?.let { codeToEmoji(it) }
                if (emoji != null) {
                    cache[cleanIp] = emoji
                }
                callback(emoji)
            }

            override fun onFailure(call: Call<IpApiResponse>, t: Throwable) {
                callback(null)
            }
        })
    }

    private fun codeToEmoji(countryCode: String): String {
        val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }
}

