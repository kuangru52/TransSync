package com.kuangru52.transsync

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme

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
