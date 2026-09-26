package com.kuangru52.transsync

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 100% 纯 Compose 版本的 TorrentDetailScreen 种子详情外壳容器界面：
 * - 顶栏 TabRow 与包含 信息 / 节点 两页的 HorizontalPager
 * - 蓝色指示线条 (#1D88E3)，原生流畅左右滑动切换与返回导航
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TorrentDetailScreen(
    torrentId: Int,
    rpcUrl: String,
    user: String,
    pass: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPageSelected: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { 2 }

    LaunchedEffect(pagerState.currentPage) {
        onPageSelected(pagerState.currentPage)
    }

    val accentColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF)
    val topBarBgColor = if (isDark) Color(0xFF161F29) else Color(0xFF455A64)

    var torrentInfoState by remember { mutableStateOf<Torrent?>(null) }
    var peersState by remember { mutableStateOf<List<Peer>>(emptyList()) }
    var isPeersRefreshing by remember { mutableStateOf(value = false) }

    val isInspection = LocalInspectionMode.current
    val graphicsContext = LocalGraphicsContext.current
    val backdropLayer = remember(torrentId, pagerState.currentPage, isInspection) {
        if (!isInspection) {
            try {
                graphicsContext.createGraphicsLayer()
            } catch (_: Exception) { null }
        } else null
    }
    DisposableEffect(torrentId, pagerState.currentPage, isInspection) {
        onDispose {
            if (backdropLayer != null) {
                try {
                    graphicsContext.releaseGraphicsLayer(backdropLayer)
                } catch (_: Exception) {}
            }
        }
    }

    val detailView = LocalView.current
    var detailViewLocation by remember { mutableStateOf(Offset.Zero) }

    // 状态拉取函数
    val fetchDetailData = {
        if (torrentId != -1 && rpcUrl.isNotEmpty()) {
            val (effUrl, effUser, effPass) = DialogUtils.getEffectiveCredentials(context, rpcUrl, user, pass)
            val activeServer = ServerManager.getActiveServer(context)

            if (activeServer?.clientType == ServerConfig.CLIENT_QBITTORRENT) {
                val qbitService = QBittorrentClient.getService(effUrl)

                val doQbitFetch = {
                    qbitService.getTorrentsInfo("all").enqueue(object : retrofit2.Callback<List<QbitTorrentInfo>> {
                        override fun onResponse(call: retrofit2.Call<List<QbitTorrentInfo>>, response: retrofit2.Response<List<QbitTorrentInfo>>) {
                            if (response.isSuccessful) {
                                val list = response.body() ?: emptyList()
                                val targetQbit = list.find { it.hash.lowercase().hashCode() == torrentId }
                                if (targetQbit != null) {
                                    val baseTorrent = QbitMapper.mapToTorrent(targetQbit)
                                    val targetHash = targetQbit.hash

                                    // 开启拉取 qBittorrent 关联文件、Tracker 与 Peers 节点信息
                                    qbitService.getTorrentPeers(targetHash).enqueue(object : retrofit2.Callback<QbitPeersResponse> {
                                        override fun onResponse(c: retrofit2.Call<QbitPeersResponse>, r: retrofit2.Response<QbitPeersResponse>) {
                                            if (r.isSuccessful) {
                                                val peersMap = r.body()?.peers ?: emptyMap()
                                                val mappedPeers = peersMap.map { (key, info) -> QbitMapper.mapPeer(key, info) }
                                                peersState = mappedPeers
                                                torrentInfoState = (torrentInfoState ?: baseTorrent).copy(peers = mappedPeers)
                                            }
                                        }
                                        override fun onFailure(c: retrofit2.Call<QbitPeersResponse>, t: Throwable) {}
                                    })

                                    qbitService.getTorrentTrackers(targetHash).enqueue(object : retrofit2.Callback<List<QbitTrackerItem>> {
                                        override fun onResponse(c: retrofit2.Call<List<QbitTrackerItem>>, r: retrofit2.Response<List<QbitTrackerItem>>) {
                                            if (r.isSuccessful) {
                                                val trackersList = r.body() ?: emptyList()
                                                val mappedTrackers = trackersList.map { QbitMapper.mapTracker(it) }
                                                val mappedStats = trackersList.map { QbitMapper.mapTrackerStat(it) }
                                                torrentInfoState = (torrentInfoState ?: baseTorrent).copy(
                                                    trackers = mappedTrackers,
                                                    trackerStats = mappedStats
                                                )
                                            }
                                        }
                                        override fun onFailure(c: retrofit2.Call<List<QbitTrackerItem>>, t: Throwable) {}
                                    })

                                    qbitService.getTorrentFiles(targetHash).enqueue(object : retrofit2.Callback<List<QbitFileInfo>> {
                                        override fun onResponse(c: retrofit2.Call<List<QbitFileInfo>>, r: retrofit2.Response<List<QbitFileInfo>>) {
                                            if (r.isSuccessful) {
                                                val filesList = r.body() ?: emptyList()
                                                val mappedFiles = filesList.map { QbitMapper.mapFile(it) }
                                                torrentInfoState = (torrentInfoState ?: baseTorrent).copy(files = mappedFiles)
                                            }
                                        }
                                        override fun onFailure(c: retrofit2.Call<List<QbitFileInfo>>, t: Throwable) {}
                                    })

                                    torrentInfoState = baseTorrent
                                }
                            }
                        }
                        override fun onFailure(call: retrofit2.Call<List<QbitTorrentInfo>>, t: Throwable) {}
                    })
                }

                if (effUser.isNotEmpty() || effPass.isNotEmpty()) {
                    qbitService.login(effUser, effPass).enqueue(object : retrofit2.Callback<String> {
                        override fun onResponse(call: retrofit2.Call<String>, response: retrofit2.Response<String>) { doQbitFetch() }
                        override fun onFailure(call: retrofit2.Call<String>, t: Throwable) { doQbitFetch() }
                    })
                } else {
                    doQbitFetch()
                }
            } else {
                val service = TransmissionClient.getService(effUrl, effUser, effPass)

                val fields = listOf(
                    "id", "name", "totalSize", "percentDone", "rateDownload", "rateUpload",
                    "downloadedEver", "uploadedEver", "uploadRatio", "status", "downloadDir",
                    "addedDate", "doneDate", "activityDate", "secondsSeeding", "secondsDownloading",
                    "error", "errorString", "labels", "files", "fileStats", "trackers", "trackerStats", "peersGettingFromUs", "peersSendingToUs", "peers"
                )

                service.getTorrents(effUrl, null, RpcRequest("torrent-get", mapOf("fields" to fields, "ids" to listOf(torrentId))))
                    .enqueue(object : retrofit2.Callback<RpcResponse<TorrentListArguments>> {
                        override fun onResponse(call: retrofit2.Call<RpcResponse<TorrentListArguments>>, response: retrofit2.Response<RpcResponse<TorrentListArguments>>) {
                            if (response.isSuccessful) {
                                val torrent = response.body()?.arguments?.torrents?.firstOrNull()
                                if (torrent != null) {
                                    torrentInfoState = torrent
                                    peersState = torrent.peers ?: emptyList()
                                }
                            }
                        }
                        override fun onFailure(call: retrofit2.Call<RpcResponse<TorrentListArguments>>, t: Throwable) {}
                    })
            }
        }
    }

    // 重新汇报节点函数
    val reannouncePeers = {
        val (effUrl, effUser, effPass) = DialogUtils.getEffectiveCredentials(context, rpcUrl, user, pass)
        if (torrentId != -1 && effUrl.isNotEmpty()) {
            val activeServer = ServerManager.getActiveServer(context)

            if (activeServer?.clientType == ServerConfig.CLIENT_QBITTORRENT) {
                val qbitService = QBittorrentClient.getService(effUrl)
                val targetHash = torrentInfoState?.hash ?: ""

                val doQbitReannounce = {
                    isPeersRefreshing = true
                    qbitService.reannounceTorrents(targetHash.ifEmpty { "all" }).enqueue(object : retrofit2.Callback<String> {
                        override fun onResponse(call: retrofit2.Call<String>, response: retrofit2.Response<String>) {
                            isPeersRefreshing = false
                            if (response.isSuccessful || response.code() == 200) {
                                android.widget.Toast.makeText(context, R.string.msg_reannounce_success, android.widget.Toast.LENGTH_SHORT).show()
                                fetchDetailData()
                            } else {
                                android.widget.Toast.makeText(context, context.getString(R.string.msg_update_failed, response.code()), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onFailure(call: retrofit2.Call<String>, t: Throwable) {
                            isPeersRefreshing = false
                            android.widget.Toast.makeText(context, R.string.msg_network_error, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })
                }

                if (effUser.isNotEmpty() || effPass.isNotEmpty()) {
                    qbitService.login(effUser, effPass).enqueue(object : retrofit2.Callback<String> {
                        override fun onResponse(call: retrofit2.Call<String>, response: retrofit2.Response<String>) { doQbitReannounce() }
                        override fun onFailure(call: retrofit2.Call<String>, t: Throwable) { doQbitReannounce() }
                    })
                } else {
                    doQbitReannounce()
                }
            } else {
                val service = TransmissionClient.getService(effUrl, effUser, effPass)
                val request = RpcRequest("torrent-reannounce", mapOf("ids" to listOf(torrentId)))

                isPeersRefreshing = true
                service.rpc(effUrl, null, request).enqueue(object : retrofit2.Callback<RpcResponse<Map<String, Any>>> {
                    override fun onResponse(
                        call: retrofit2.Call<RpcResponse<Map<String, Any>>>,
                        response: retrofit2.Response<RpcResponse<Map<String, Any>>>
                    ) {
                        isPeersRefreshing = false
                        if (response.isSuccessful) {
                            android.widget.Toast.makeText(context, R.string.msg_reannounce_success, android.widget.Toast.LENGTH_SHORT).show()
                            fetchDetailData()
                        } else {
                            android.widget.Toast.makeText(context, context.getString(R.string.msg_update_failed, response.code()), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(
                        call: retrofit2.Call<RpcResponse<Map<String, Any>>>,
                        t: Throwable
                    ) {
                        isPeersRefreshing = false
                        android.widget.Toast.makeText(context, R.string.msg_network_error, android.widget.Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }
    }

    LaunchedEffect(torrentId) {
        fetchDetailData()
    }

    var recordTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            recordTick++
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    val pageOffsetAnim = remember { Animatable(0f) }
    val currentPage = if (pageOffsetAnim.value > screenWidthPx * 0.5f) 1 else 0

    // 返回键监听：如果在节点页，按返回键先平滑滑回信息页；如果在信息页，直接退出详情页
    BackHandler(enabled = pageOffsetAnim.value > 0f) {
        scope.launch {
            pageOffsetAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
            )
        }
    }

    var lastDragAmount by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val px = pageOffsetAnim.value
                translationX = if (px < 0f) -px else 0f
            }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startX = down.position.x
                            val startY = down.position.y
                            var isDragging = false
                            var isHorizontalGesture = false
                            lastDragAmount = 0f

                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val changes = event.changes
                                val pointer = changes.find { it.id == down.id } ?: break

                                if (!pointer.pressed) break

                                val dx = pointer.position.x - startX
                                val dy = pointer.position.y - startY

                                if (!isDragging) {
                                    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                    if (dist > viewConfiguration.touchSlop) {
                                        if (kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f) {
                                            isDragging = true
                                            isHorizontalGesture = true
                                        } else {
                                            // 上下滑动放行给列表
                                            break
                                        }
                                    }
                                }

                                if (isHorizontalGesture) {
                                    pointer.consume()
                                    val dragAmount = pointer.position.x - pointer.previousPosition.x
                                    lastDragAmount = dragAmount
                                    val currentPx = pageOffsetAnim.value
                                    // 严格区分：在节点页时绝对限制在 0..screenWidthPx，绝不能向右滑动时超出 0 触发退出！
                                    // 只有在信息页(0)时，向右滑动才允许负数阻尼触发退出动画。
                                    val newOffset = if (currentPx > screenWidthPx * 0.5f) {
                                        (pageOffsetAnim.value - dragAmount).coerceIn(0f, screenWidthPx)
                                    } else {
                                        (pageOffsetAnim.value - dragAmount).coerceIn(-screenWidthPx, screenWidthPx)
                                    }
                                    launch {
                                        pageOffsetAnim.snapTo(newOffset)
                                    }
                                }
                            }

                            if (isHorizontalGesture) {
                                val currentPx = pageOffsetAnim.value
                                val isCurrentlyPeers = currentPx > screenWidthPx * 0.5f

                                val targetOffset = when {
                                    // 强制：在节点页：无论怎么向右划，目标永远是 0f (信息页)，绝对无法触发退出！
                                    isCurrentlyPeers -> 0f
                                    // 仅在信息页：向右划超过阈值（currentPx < -80f 或快速右划） -> 触发滑出屏幕并退出详情页
                                    !isCurrentlyPeers && (currentPx < -80f || lastDragAmount > 12f) -> -screenWidthPx
                                    // 仅在信息页：向左划或滑动过半 -> 进入节点页(1)
                                    !isCurrentlyPeers && (lastDragAmount < -6f || currentPx > screenWidthPx * 0.5f) -> screenWidthPx
                                    else -> 0f
                                }

                                launch {
                                    pageOffsetAnim.animateTo(
                                        targetValue = targetOffset,
                                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                                    )
                                    if (targetOffset == -screenWidthPx) {
                                        onBackClick()
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        if (pageOffsetAnim.value < 0f) {
            val alpha = (-pageOffsetAnim.value / screenWidthPx).coerceIn(0f, 0.6f)
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha)))
        }

        // 1. 被 backdropLayer 离屏录制的底图采样层 (包含全局唯一壁纸 + 信息页 + 节点页超宽画布)
        val detailView = LocalView.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val loc = IntArray(2)
                    detailView.getLocationOnScreen(loc)
                    val offsetInWindow = coordinates.positionInWindow()
                    detailViewLocation = Offset(
                        x = loc[0].toFloat() + offsetInWindow.x,
                        y = loc[1].toFloat() + offsetInWindow.y,
                    )
                }
                .drawWithContent {
                    val dummy = recordTick.toString()
                    if (dummy.isEmpty()) {}
                    if (backdropLayer != null) {
                        try {
                            backdropLayer.record {
                                this@drawWithContent.drawContent()
                            }
                        } catch (_: Exception) {}
                    }
                    drawContent()
                }
        ) {
            // 全局唯一一张沉浸式壁纸 (从 $y = 0$ 最顶端开始铺满全屏，录制进 backdropLayer 中供详情页所有弹窗提取极致折射与磨砂玻璃)
            WallpaperBackground()

            // 超宽无缝平移 Row (信息页 100% 屏宽 + 节点页 100% 屏宽)
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer {
                        val px = pageOffsetAnim.value
                        translationX = if (px < 0f) 0f else -px
                    }
            ) {
                // 左侧 Page 0: 信息页 (TorrentInfoScreen)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .requiredWidth(configuration.screenWidthDp.dp)
                ) {
                    TorrentInfoScreen(
                        torrent = torrentInfoState,
                        rpcUrl = rpcUrl,
                        user = user,
                        pass = pass,
                        backdropLayer = backdropLayer,
                        boxPositionInRoot = detailViewLocation,
                        onRefresh = { fetchDetailData() },
                    )
                }

                // 右侧 Page 1: 节点页 (TorrentPeersScreen)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .requiredWidth(configuration.screenWidthDp.dp)
                ) {
                    TorrentPeersScreen(
                        peers = peersState,
                        isRefreshing = isPeersRefreshing,
                        onRefresh = { reannouncePeers() }
                    )
                }
            }
        }

        // 2. 顶层悬浮控制栏 (不在 backdropLayer 内部录制，彻底防止 RenderNode 递归绘制崩溃)
        val backSwipeRatio = if (pageOffsetAnim.value < 0f) {
            (-pageOffsetAnim.value / 120f).coerceIn(0f, 1f)
        } else {
            0f
        }

        DetailFloatingTopBar(
            currentPage = currentPage,
            backSwipeRatio = backSwipeRatio,
            backdropLayer = backdropLayer,
            boxPositionInRoot = detailViewLocation,
            onTabSelected = { index ->
                scope.launch {
                    pageOffsetAnim.animateTo(
                        targetValue = if (index == 1) screenWidthPx else 0f,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                    )
                }
            },
            onBackClick = onBackClick,
            isDark = isDark,
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopCenter)
        )
    }
}

@Composable
fun DetailFloatingTopBar(
    currentPage: Int,
    backSwipeRatio: Float = 0f,
    backdropLayer: androidx.compose.ui.graphics.layer.GraphicsLayer? = null,
    boxPositionInRoot: Offset = Offset.Zero,
    onTabSelected: (Int) -> Unit,
    onBackClick: () -> Unit,
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
) {
    val barBorderColor = if (isDark) Color(0x3BFFFFFF) else Color(0x55E0E0E0)
    val textColor = if (isDark) Color.White else Color(0xFF2D3436)

    val arrowRotation = backSwipeRatio * 180f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 1. 左侧 44dp 圆形悬浮返回按钮 (采用统一 3D 液态玻璃效果)
        LiquidGlassTopSurface(
            shape = CircleShape,
            border = BorderStroke(1.dp, barBorderColor),
            backdropLayer = backdropLayer,
            boxPositionInRoot = boxPositionInRoot,
            onClick = onBackClick,
            modifier = Modifier
                .size(44.dp)
                .align(Alignment.CenterStart),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = textColor,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            rotationZ = arrowRotation
                        },
                )
            }
        }

        // 2. 中间 180dp 胶囊形悬浮 Tab 切换组 [信息 | 节点] (采用统一 3D 液态玻璃效果)
        val activeBgColor = if (isDark) Color(0x44FFFFFF) else Color(0x55FFFFFF)
        val activeBorderColor = if (isDark) Color(0xAAFFFFFF) else Color(0xCCFFFFFF)

        LiquidGlassTopSurface(
            shape = RoundedCornerShape(100.dp),
            border = BorderStroke(1.dp, barBorderColor),
            backdropLayer = backdropLayer,
            boxPositionInRoot = boxPositionInRoot,
            modifier = Modifier
                .height(44.dp)
                .width(180.dp)
                .align(Alignment.Center),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("信息", "节点").forEachIndexed { index, title ->
                    val isSelected = currentPage == index

                    Surface(
                        onClick = { onTabSelected(index) },
                        shape = RoundedCornerShape(100.dp),
                        color = if (isSelected) activeBgColor else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, activeBorderColor) else null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = title,
                                fontSize = 14.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else textColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun TorrentDetailScreenPreview() {
    MaterialTheme {
        TorrentDetailScreen(
            torrentId = 1,
            rpcUrl = "",
            user = "",
            pass = "",
            onBackClick = {}
        )
    }
}
