package com.kuangru52.transsync

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val releaseNotes: String,
    val releaseUrl: String,
)

object UpdateCheckUtils {

    const val GITHUB_RELEASES_URL = "https://github.com/kuangru52/TransSync/releases/latest"
    private const val API_URL = "https://api.github.com/repos/kuangru52/TransSync/releases/latest"

    var cachedUpdateInfo: UpdateInfo? = null
        private set

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkForUpdates(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        val currentVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "3.08"
        } catch (_: Exception) {
            "3.08"
        }

        try {
            val request = Request.Builder()
                .url(API_URL)
                .header("User-Agent", "TransSync-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: ""
                val jsonObj = JSONObject(jsonStr)
                val tagName = jsonObj.optString("tag_name", "").removePrefix("v").trim()
                val htmlUrl = jsonObj.optString("html_url", GITHUB_RELEASES_URL)
                val body = jsonObj.optString("body", "点击前往 GitHub 查看新版本更变内容。")

                val hasNewer = isNewerVersion(tagName, currentVersion)
                val info = UpdateInfo(
                    hasUpdate = hasNewer,
                    latestVersion = if (tagName.isNotEmpty()) "v$tagName" else "新版本",
                    releaseNotes = body,
                    releaseUrl = htmlUrl.ifEmpty { GITHUB_RELEASES_URL },
                )
                cachedUpdateInfo = info
                return@withContext info
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fallbackInfo = UpdateInfo(
            hasUpdate = false,
            latestVersion = "v$currentVersion",
            releaseNotes = "",
            releaseUrl = GITHUB_RELEASES_URL,
        )
        cachedUpdateInfo = fallbackInfo
        fallbackInfo
    }

    fun openReleasesPage(context: Context, url: String = GITHUB_RELEASES_URL) {
        try {
            val targetUrl = url.ifEmpty { GITHUB_RELEASES_URL }
            val intent = Intent(Intent.ACTION_VIEW, targetUrl.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
        if (latestTag.isBlank() || currentVersion.isBlank()) return false
        val cleanLatest = latestTag.removePrefix("v").trim()
        val cleanCurrent = currentVersion.removePrefix("v").trim()

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
