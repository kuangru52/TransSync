package com.kuangru52.transsync

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme

/**
 * 种子详情页宿主 Activity (TorrentDetailActivity.kt)
 *
 * 【作用与功能】：
 * - 承载 [TorrentDetailScreen] 种子详情界面的 AppCompatActivity 容器；
 * - 负责接收 Intent 传入的种子 ID (torrent_id)、RPC 地址及用户认证凭据；
 * - 支持侧滑手势返回与状态栏沉浸式浸入。
 */
class TorrentDetailActivity : AppCompatActivity() {

    private var torrentId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setupStatusBar()

        torrentId = intent.getIntExtra("torrent_id", -1)

        val rpcUrl = intent.getStringExtra("rpcUrl") ?: ""
        val user = intent.getStringExtra("user") ?: ""
        val pass = intent.getStringExtra("pass") ?: ""

        setContent {
            MaterialTheme {
                TorrentDetailScreen(
                    torrentId = torrentId,
                    rpcUrl = rpcUrl,
                    user = user,
                    pass = pass,
                    onBackClick = { finish() },
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun setupStatusBar() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }
}
