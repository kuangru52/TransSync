package com.kuangru52.transsync

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import kotlin.math.abs

class TorrentDetailActivity : AppCompatActivity() {

    private var torrentId: Int = -1
    private var currentDetailTab: Int = 0

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isSwipingBack = false
    private var screenWidth = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        screenWidth = resources.displayMetrics.widthPixels.toFloat()
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
                ) { pageIndex ->
                    currentDetailTab = pageIndex
                }
            }
        }
    }

    /**
     * 手势拦截器：
     * - 仅在信息页面 (Tab 0) 从左往右滑动时，实时跟随手指右滑退出当前界面
     * - 在节点页面 (Tab 1) 从左往右滑动时，不拦截，交给 HorizontalPager 原生流畅滑动切换回信息页面 (Tab 0)
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val density = resources.displayMetrics.density

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = ev.x
                touchStartY = ev.y
                isSwipingBack = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = ev.x - touchStartX
                val deltaY = ev.y - touchStartY

                // 只有在Tab 0 (信息页面) 且从左向右滑动时，才触发 Activity 退出手势
                if ((currentDetailTab == 0) && (deltaX > 0)) {
                    val isSwipeTriggered = (!isSwipingBack) && (deltaX > (15f * density)) && (abs(deltaY) < (deltaX * 0.6f))
        if (isSwipeTriggered) {
                        isSwipingBack = true
                    }

                    if (isSwipingBack) {
                        val translation = deltaX.coerceAtLeast(0f)
                        window.decorView.translationX = translation
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSwipingBack) {
                    val deltaX = ev.x - touchStartX
                    isSwipingBack = false

                    if (deltaX > screenWidth * 0.3f) {
                        window.decorView.animate()
                            .translationX(screenWidth)
                            .setDuration(200)
                            .setInterpolator(DecelerateInterpolator())
                            .withEndAction {
                                finish()
                                @Suppress("DEPRECATION")
                                overridePendingTransition(0, 0)
                            }
                            .start()
                    } else {
                        window.decorView.animate()
                            .translationX(0f)
                            .setDuration(200)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
