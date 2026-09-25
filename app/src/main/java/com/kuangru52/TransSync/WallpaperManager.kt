package com.kuangru52.transsync

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 全应用壁纸管理器 (Bing 每日壁纸拉取、本地多图按小时轮播、实时高斯模糊)
 */
object WallpaperManager {

    private const val BING_API = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN"
    private const val BING_HOST = "https://www.bing.com"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 异步获取/缓存每日 Bing 壁纸图片文件
     */
    suspend fun getBingWallpaperFile(context: Context): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "wallpapers").apply { if (!exists()) mkdirs() }
            val dateStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
            val targetFile = File(cacheDir, "bing_$dateStr.jpg")

            if (targetFile.exists() && targetFile.length() > 0L) {
                return@withContext targetFile
            }

            val request = Request.Builder().url(BING_API).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: return@withContext null
                val jsonObj = JSONObject(jsonStr)
                val images = jsonObj.optJSONArray("images")
                if (images != null && images.length() > 0) {
                    val urlPath = images.getJSONObject(0).optString("url")
                    if (urlPath.isNotBlank()) {
                        val fullUrl = if (urlPath.startsWith("http")) urlPath else "$BING_HOST$urlPath"
                        val imgRequest = Request.Builder().url(fullUrl).build()
                        val imgResponse = client.newCall(imgRequest).execute()
                        if (imgResponse.isSuccessful) {
                            val bytes = imgResponse.body?.bytes() ?: return@withContext null
                            targetFile.writeBytes(bytes)
                            return@withContext targetFile
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * 根据当前壁纸模式（Bing 或 本地轮播）获取当前应该呈现的 ImageBitmap
     */
    suspend fun loadCurrentWallpaperBitmap(context: Context, mode: String, localUris: List<String>): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            if (mode == "bing") {
                val bingFile = getBingWallpaperFile(context)
                if (bingFile != null && bingFile.exists()) {
                    return@withContext BitmapFactory.decodeFile(bingFile.absolutePath)?.asImageBitmap()
                }
            } else if (mode == "local" && localUris.isNotEmpty()) {
                // 每 1 小时自动轮播 (按当前小时时间戳 % 图片列表数量)
                val currentHourIndex = (System.currentTimeMillis() / (3600 * 1000L)).toInt()
                val selectedIndex = (currentHourIndex % localUris.size + localUris.size) % localUris.size
                val uriStr = localUris[selectedIndex]

                val uri = uriStr.toUri()
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    return@withContext bitmap?.asImageBitmap()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}

/**
 * 全应用通用的全屏壁纸渲染背景组件
 */
@Composable
fun WallpaperBackground(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val mode = SettingsManager.getWallpaperMode(context)
    val localUris = SettingsManager.getLocalWallpaperUris(context)
    val blurRadius = SettingsManager.getWallpaperBlur(context)

    val baseColor = if (isDark) Color(0xFF161F29) else Color(0xFFF0F2F5)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor),
    ) {
        var wallpaperBitmap by remember(mode, localUris, SettingsManager.wallpaperStateVersion) {
            mutableStateOf<ImageBitmap?>(null)
        }

        LaunchedEffect(mode, localUris, SettingsManager.wallpaperStateVersion) {
            if (mode != "none") {
                val loaded = WallpaperManager.loadCurrentWallpaperBitmap(context, mode, localUris)
                if (loaded != null) {
                    wallpaperBitmap = loaded
                }
            } else {
                wallpaperBitmap = null
            }
        }

        if (mode != "none" && wallpaperBitmap != null) {
            Image(
                bitmap = wallpaperBitmap!!,
                contentDescription = "壁纸背景",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurRadius > 0f) Modifier.blur(blurRadius.dp) else Modifier),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDark) Color(0x55000000) else Color(0x22FFFFFF)),
            )
        }
    }
}
