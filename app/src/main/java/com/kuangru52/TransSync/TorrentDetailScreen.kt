package com.kuangru52.transsync

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalView
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

    val graphicsContext = LocalGraphicsContext.current
    val backdropLayer = remember(torrentId, pagerState.currentPage) {
        graphicsContext.createGraphicsLayer()
    }
    DisposableEffect(torrentId, pagerState.currentPage) {
        onDispose {
            graphicsContext.releaseGraphicsLayer(backdropLayer)
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

    var swipeOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedSwipeOffset by animateFloatAsState(
        targetValue = swipeOffsetX,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "swipeOffset"
    )

    Box(
        modifier = modifier
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
                backdropLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawContent()
            }
            .graphicsLayer {
                translationX = animatedSwipeOffset
            }
            .pointerInput(pagerState.currentPage) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        val pointerId = down.id
                        var isDraggingRight = false
                        var currentOffsetX = 0f

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val dragChange = event.changes.find { it.id == pointerId } ?: break

                            if (!dragChange.pressed) break

                            val dragAmount = dragChange.position.x - dragChange.previousPosition.x

                            if (pagerState.currentPage == 0 && (dragAmount > 0f || currentOffsetX > 0f)) {
                                isDraggingRight = true
                            }

                            if (isDraggingRight) {
                                dragChange.consume()
                                currentOffsetX = (currentOffsetX + dragAmount).coerceAtLeast(0f)
                                swipeOffsetX = currentOffsetX
                            }
                        }

                        if (isDraggingRight) {
                            if (swipeOffsetX > 100.dp.toPx()) {
                                onBackClick()
                            }
                            swipeOffsetX = 0f
                        }
                    }
                }
            }
    ) {
        WallpaperBackground()

        val currentWallpaperMode = remember(SettingsManager.wallpaperStateVersion) { SettingsManager.getWallpaperMode(context) }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (currentWallpaperMode != "none") Color.Transparent else if (isDark) Color(0xFF161F29) else Color(0xFFF0F2F5),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {},
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(innerPadding)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> TorrentInfoScreen(
                            torrent = torrentInfoState,
                            rpcUrl = rpcUrl,
                            user = user,
                            pass = pass,
                            onRefresh = { fetchDetailData() },
                        )
                        1 -> TorrentPeersScreen(
                            peers = peersState,
                            isRefreshing = isPeersRefreshing,
                            onRefresh = { reannouncePeers() }
                        )
                    }
                }

                val backSwipeRatio = (animatedSwipeOffset / 180f).coerceIn(0f, 1f)

                DetailFloatingTopBar(
                    currentPage = pagerState.currentPage,
                    backSwipeRatio = backSwipeRatio,
                    onTabSelected = { index ->
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    onBackClick = onBackClick,
                    isDark = isDark,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
}
}

@Composable
fun DetailFloatingTopBar(
    currentPage: Int,
    backSwipeRatio: Float = 0f,
    onTabSelected: (Int) -> Unit,
    onBackClick: () -> Unit,
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
) {
    val barBgColor = if (isDark) Color(0x99141D26) else Color(0xA6FFFFFF)
    val barBorderColor = if (isDark) Color(0x3BFFFFFF) else Color(0x55E0E0E0)
    val textColor = if (isDark) Color.White else Color(0xFF2D3436)
    val accentColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF)

    val arrowRotation = backSwipeRatio * 180f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 1. 左侧 44dp 圆形悬浮返回按钮
        Surface(
            onClick = onBackClick,
            shape = CircleShape,
            color = barBgColor,
            border = BorderStroke(1.dp, barBorderColor),
            shadowElevation = 8.dp,
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

        // 2. 中间 180dp 胶囊形悬浮 Tab 切换组 [信息 | 节点] (居中对齐)
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = barBgColor,
            border = BorderStroke(1.dp, barBorderColor),
            shadowElevation = 8.dp,
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
                        color = if (isSelected) accentColor else Color.Transparent,
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
