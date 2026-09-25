package com.kuangru52.transsync

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

sealed interface RightPaneTarget {
    object List : RightPaneTarget
    data class Detail(val torrentId: Int, val torrentName: String) : RightPaneTarget
    object Settings : RightPaneTarget
}

/**
 * 1:1 绝对复刻 5 张截图组件元素的重构版 TorrentListScreen 主界面
 */
@android.annotation.SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentListScreen(
    viewModel: TorrentListViewModel,
    onTorrentClick: (Torrent) -> Unit,
    modifier: Modifier = Modifier,
    onAddClick: () -> Unit = {},
    onPickFile: (() -> Unit)? = null,
    externalShowAddTorrentDialog: Boolean = false,
    externalInitialUrl: String? = null,
    externalInitialFileUri: Uri? = null,
    onCloseExternalAddTorrentDialog: (() -> Unit)? = null,
    rpcUrl: String = "",
    user: String = "",
    pass: String = "",
) {
    val torrents by viewModel.torrents.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(initial = false)
    val isTrackerBlurEnabled by viewModel.isTrackerBlurEnabled.observeAsState(initial = false)
    val revealedTrackerNames by viewModel.revealedTrackerNames.observeAsState(emptySet())
    val totalSize by viewModel.totalSize.observeAsState(0L)
    val altSpeedEnabled by viewModel.altSpeedEnabled.observeAsState(initial = false)

    var currentFilter by remember { mutableStateOf("All") }
    var selectedIds by remember { mutableStateOf(emptySet<Int>()) }

    // 弹窗状态管理
    var renameTorrentTarget by remember { mutableStateOf<Torrent?>(null) }
    var deleteIdsTarget by remember { mutableStateOf<List<Int>?>(null) }
    var setLocationTargetIds by remember { mutableStateOf<List<Int>?>(null) }
    var setHrTargetIds by remember { mutableStateOf<List<Int>?>(null) }
    var showAddTorrentDialogState by remember { mutableStateOf(false) }
    var initialUrlForAdd by remember { mutableStateOf<String?>(null) }
    var initialFileUriForAdd by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    var updateInfoState by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialogState by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val info = UpdateCheckUtils.checkForUpdates(context)
        if (info.hasUpdate) {
            updateInfoState = info
            showUpdateDialogState = true
        }
    }

    // 监听外部共享/调起的磁力链/种子文件 Intent
    LaunchedEffect(externalShowAddTorrentDialog, externalInitialUrl, externalInitialFileUri) {
        if (externalShowAddTorrentDialog) {
            if (externalInitialUrl != null) initialUrlForAdd = externalInitialUrl
            if (externalInitialFileUri != null) initialFileUriForAdd = externalInitialFileUri
            showAddTorrentDialogState = true
        }
    }

    val isDark = isSystemInDarkTheme()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val dialogSessionKey = remember(
        showAddTorrentDialogState,
        renameTorrentTarget,
        deleteIdsTarget,
        setLocationTargetIds,
        setHrTargetIds,
        showUpdateDialogState,
    ) {
        java.util.UUID.randomUUID().toString()
    }

    val graphicsContext = LocalGraphicsContext.current
    val backdropLayer = remember(dialogSessionKey) {
        graphicsContext.createGraphicsLayer()
    }
    DisposableEffect(dialogSessionKey) {
        onDispose {
            graphicsContext.releaseGraphicsLayer(backdropLayer)
        }
    }
    var boxPositionInRoot by remember { mutableStateOf(Offset.Zero) }

    var isFabVisible by remember { mutableStateOf(true) }
    var isSearchActive by remember { mutableStateOf(false) }

    var isDrawerOpen by remember { mutableStateOf(false) }

    val drawerWidthDp = 300.dp
    val drawerWidthPx = with(LocalDensity.current) { drawerWidthDp.toPx() }

    val drawerOffsetAnim = remember { Animatable(0f) }

    LaunchedEffect(isDrawerOpen) {
        drawerOffsetAnim.animateTo(
            targetValue = if (isDrawerOpen) drawerWidthPx else 0f,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
        )
    }

    val currentOffset = drawerOffsetAnim.value

    BackHandler(enabled = isDrawerOpen && selectedIds.isEmpty()) {
        isDrawerOpen = false
    }

    // Compose 本地文件选择器 Launcher
    val composeFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            initialFileUriForAdd = it
            showAddTorrentDialogState = true
        }
    }

    val handlePickFile = {
        if (onPickFile != null) {
            onPickFile.invoke()
        } else {
            composeFilePickerLauncher.launch("*/*")
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) && (configuration.screenWidthDp >= 600)
    var rightPaneTarget by remember { mutableStateOf<RightPaneTarget>(RightPaneTarget.List) }

    // 1. 多选模式下按返回键：优先退出多选模式
    BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    // 2. 横屏右侧非列表模式按返回键：重置右侧为列表模式
    BackHandler(enabled = (isLandscape) && (rightPaneTarget != RightPaneTarget.List)) {
        rightPaneTarget = RightPaneTarget.List
    }

    // 3. 常规主页防误触双击返回退出机制
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = !isLandscape && selectedIds.isEmpty()) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000L) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(context, R.string.msg_exit_press_again, Toast.LENGTH_SHORT).show()
        }
    }

    // FAB 动态显隐逻辑
    LaunchedEffect(listState, isSearchActive) {
        if (isSearchActive) {
            isFabVisible = false
        } else {
            var lastOffset = 0
            snapshotFlow {
                Pair(
                    listState.firstVisibleItemIndex * 10000 + listState.firstVisibleItemScrollOffset,
                    listState.isScrollInProgress
                )
            }.collect { (offset, isScrolling) ->
                if (isScrolling) {
                    val diff = offset - lastOffset
                    if (diff > 5) {
                        isFabVisible = false
                    } else if (diff < -5) {
                        isFabVisible = true
                    }
                    lastOffset = offset
                } else {
                    isFabVisible = true
                }
            }
        }
    }

    @Composable
    fun MainScaffoldContent() {
        val currentWallpaperMode = remember(SettingsManager.wallpaperStateVersion) { SettingsManager.getWallpaperMode(context) }
        val mainView = LocalView.current

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. 被录制的底层内容 (壁纸 + 种子列表)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        val loc = IntArray(2)
                        mainView.getLocationOnScreen(loc)
                        val offsetInWindow = coordinates.positionInWindow()
                        boxPositionInRoot = Offset(
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
            ) {
                WallpaperBackground()

                if (torrents.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无种子任务",
                            color = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72),
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .graphicsLayer(),
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            end = 0.dp,
                            top = 60.dp,
                            bottom = 90.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(
                            items = torrents,
                            key = { it.id }
                        ) { torrent ->
                            val isSelected = selectedIds.contains(torrent.id)
                            TorrentItemCard(
                                torrent = torrent,
                                isSelected = isSelected,
                                isTrackerBlurEnabled = isTrackerBlurEnabled,
                                revealedTrackerNames = revealedTrackerNames,
                                onClick = {
                                    if (selectedIds.isNotEmpty()) {
                                        selectedIds = if (isSelected) selectedIds - torrent.id else selectedIds + torrent.id
                                    } else {
                                        if (isLandscape) {
                                            rightPaneTarget = RightPaneTarget.Detail(torrent.id, torrent.name)
                                        } else {
                                            onTorrentClick(torrent)
                                        }
                                    }
                                },
                                onLongClick = {
                                    selectedIds = if (isSelected) selectedIds - torrent.id else selectedIds + torrent.id
                                },
                                onToggleStatus = {
                                    viewModel.toggleTorrentStatus(rpcUrl, user, pass, torrent)
                                }
                            )
                        }
                    }
                }
            }

            val drawerSlideRatio = (currentOffset / drawerWidthPx).coerceIn(0f, 1f)

            // 2. 顶层悬浮控制条 (不在 backdropLayer 内部录制，彻底防止 RenderNode 递归绘制崩溃)
            FloatingTopControls(
                titleText = getFilterTitleText(currentFilter),
                sizeText = FormatUtils.formatSize(totalSize),
                altSpeedEnabled = altSpeedEnabled,
                selectedCount = selectedIds.size,
                drawerSlideRatio = drawerSlideRatio,
                onMenuClick = {
                    if (!isLandscape) {
                        isDrawerOpen = !isDrawerOpen
                    }
                },
                onTurtleClick = { viewModel.toggleAltSpeedLimits(rpcUrl, user, pass) },
                onCloseSelection = { selectedIds = emptySet() },
                onSelectAll = { selectedIds = torrents.map { it.id }.toSet() },
                onDeleteSelected = {
                    val ids = selectedIds.toList()
                    if (ids.isNotEmpty()) {
                        deleteIdsTarget = ids
                    }
                },
                onStartSelected = {
                    val ids = selectedIds.toList()
                    selectedIds = emptySet()
                    viewModel.startTorrents(rpcUrl, user, pass, ids)
                },
                onStopSelected = {
                    val ids = selectedIds.toList()
                    selectedIds = emptySet()
                    viewModel.stopTorrents(rpcUrl, user, pass, ids)
                },
                onRenameSelected = {
                    val ids = selectedIds.toList()
                    if (ids.size == 1) {
                        val id = ids.first()
                        torrents.find { it.id == id }?.let { torrent ->
                            renameTorrentTarget = torrent
                        }
                    }
                },
                onSetLocationSelected = {
                    val ids = selectedIds.toList()
                    if (ids.isNotEmpty()) {
                        setLocationTargetIds = ids
                    }
                },
                onSetHrSelected = {
                    val ids = selectedIds.toList()
                    if (ids.isNotEmpty()) {
                        setHrTargetIds = ids
                    }
                },
                onVerifySelected = {
                    val ids = selectedIds.toList()
                    selectedIds = emptySet()
                    viewModel.verifyTorrents(rpcUrl, user, pass, ids)
                },
                onReannounceSelected = {
                    val ids = selectedIds.toList()
                    selectedIds = emptySet()
                    viewModel.reannounceTorrents(rpcUrl, user, pass, ids) {
                        Toast.makeText(context, R.string.msg_reannounce_success, Toast.LENGTH_SHORT).show()
                    }
                },
                isDark = isDark,
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopCenter)
            )

            // 3. 右下角 FAB 按钮
            AnimatedVisibility(
                visible = isFabVisible && selectedIds.isEmpty(),
                enter = scaleIn(animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)) + fadeIn(),
                exit = scaleOut(animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 24.dp, bottom = 24.dp)
            ) {
                val fabRotation by animateFloatAsState(
                    targetValue = if (showAddTorrentDialogState) 135f else 0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "fabRotation"
                )

                val fabBgColor = if (isDark) Color(0xCC1D88E3) else Color(0xCC00B0FF)
                val fabBorderColor = if (isDark) Color(0x80FFFFFF) else Color(0x8000B0FF)

                Surface(
                    onClick = {
                        onAddClick()
                        showAddTorrentDialogState = !showAddTorrentDialogState
                    },
                    shape = CircleShape,
                    color = fabBgColor,
                    border = BorderStroke(1.5.dp, fabBorderColor),
                    shadowElevation = 12.dp,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加种子",
                            tint = Color.White,
                            modifier = Modifier
                                .size(26.dp)
                                .graphicsLayer {
                                    rotationZ = fabRotation
                                }
                        )
                    }
                }
            }

            // 4. 底部居中悬浮网速条
            LiquidBottomBar(
                viewModel = viewModel,
                backdropLayer = backdropLayer,
                boxPositionInRoot = boxPositionInRoot,
                onSearchToggle = { isExpanded ->
                    isSearchActive = isExpanded
                },
                onScrollToTop = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            )

                    // 1. 重命名统一弹窗
                    renameTorrentTarget?.let { targetTorrent ->
                        RenameTorrentDialog(
                            targetTorrent = targetTorrent,
                            rpcUrl = rpcUrl,
                            user = user,
                            pass = pass,
                            backdropLayer = backdropLayer,
                            onDismiss = { renameTorrentTarget = null },
                            onSuccess = {
                                selectedIds = emptySet()
                                viewModel.refreshTorrents(rpcUrl, user, pass)
                            },
                        )
                    }

                    // 2. 删除 Compose 弹窗
                    deleteIdsTarget?.let { idsToDelete ->
                        val selectedTorrents = torrents.filter { idsToDelete.contains(it.id) }
                        val hasUnfinishedHr = selectedTorrents.any { !isHrFinished(it) }
                        var deleteLocalData by remember(idsToDelete) { mutableStateOf(!hasUnfinishedHr) }

                        LiquidGlassDialog(
                            onDismissRequest = { deleteIdsTarget = null },
                            backdropLayer = backdropLayer,
                            title = stringResource(R.string.dialog_delete_title),
                            confirmButtonText = stringResource(R.string.btn_confirm),
                            confirmButtonColor = Color(0xFFFF5252),
                            onConfirm = {
                                deleteIdsTarget = null
                                val hashesToDelete = torrents.filter { idsToDelete.contains(it.id) }.map { it.hash }
                                DialogUtils.performDelete(
                                    context = context,
                                    rpcUrl = rpcUrl,
                                    user = user,
                                    pass = pass,
                                    torrentIds = idsToDelete,
                                    torrentHashes = hashesToDelete,
                                    deleteData = deleteLocalData,
                                    onSuccess = {
                                        selectedIds = emptySet()
                                        viewModel.refreshTorrents(rpcUrl, user, pass)
                                    }
                                )
                            },
                            bottomLeftContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { deleteLocalData = !deleteLocalData }
                                ) {
                                    Checkbox(
                                        checked = deleteLocalData,
                                        onCheckedChange = { deleteLocalData = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFFFF5252)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.cb_delete_data),
                                        fontSize = 13.sp,
                                        color = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72)
                                    )
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.delete_confirm_msg, idsToDelete.size),
                                fontSize = 15.5.sp,
                                color = if (isDark) Color.White else Color(0xFF2D3436)
                            )
                        }
                    }

                    // 3. 设置保存位置统一弹窗
                    setLocationTargetIds?.let { targetIds ->
                        val selectedTorrents = torrents.filter { targetIds.contains(it.id) }
                        SetLocationDialog(
                            torrents = selectedTorrents,
                            rpcUrl = rpcUrl,
                            user = user,
                            pass = pass,
                            backdropLayer = backdropLayer,
                            onDismiss = { setLocationTargetIds = null },
                            onSuccess = {
                                selectedIds = emptySet()
                                viewModel.refreshTorrents(rpcUrl, user, pass)
                            },
                        )
                    }

                    // 4. 设置 H&R 考核统一弹窗
                    setHrTargetIds?.let { targetIds ->
                        val selectedTorrents = torrents.filter { targetIds.contains(it.id) }
                        SetHrDialog(
                            torrents = selectedTorrents,
                            rpcUrl = rpcUrl,
                            user = user,
                            pass = pass,
                            backdropLayer = backdropLayer,
                            onDismiss = { setHrTargetIds = null },
                            onSuccess = {
                                selectedIds = emptySet()
                                viewModel.refreshTorrents(rpcUrl, user, pass)
                            },
                        )
                    }

                    // 5. 添加种子 Compose 液态玻璃 采样弹窗
                    if (showAddTorrentDialogState) {
                        var torrentUrlInput by remember { mutableStateOf(initialUrlForAdd ?: "") }
                        var downloadDirInput by remember { mutableStateOf(torrents.firstOrNull()?.downloadDir ?: "/downloads") }
                        var hrDaysInput by remember { mutableStateOf("") }
                        var freeSpaceText by remember { mutableStateOf("") }

                        val haptic = LocalHapticFeedback.current
                        val isDeveloperMode = remember(showAddTorrentDialogState) { SettingsManager.isDeveloperMode(context) }
                        var dialogRefractionDp by remember(showAddTorrentDialogState) { mutableFloatStateOf(SettingsManager.getDialogRefraction(context, isDark)) }
                        var dialogRefractionHeightDp by remember(showAddTorrentDialogState) { mutableFloatStateOf(SettingsManager.getDialogHeight(context, isDark)) }
                        var dialogBlurRadiusDp by remember(showAddTorrentDialogState) { mutableFloatStateOf(SettingsManager.getDialogBlur(context, isDark)) }
                        var dialogSaturationBoost by remember(showAddTorrentDialogState) { mutableFloatStateOf(SettingsManager.getDialogSaturation(context, isDark)) }
                        var dialogContrast by remember(showAddTorrentDialogState) { mutableFloatStateOf(SettingsManager.getDialogContrast(context, isDark)) }
                        var dialogWhitePoint by remember(showAddTorrentDialogState) { mutableFloatStateOf(SettingsManager.getDialogWhitePoint(context, isDark)) }
                        var showDialogTuningInspector by remember { mutableStateOf(false) }

                        val allDirs = remember(ServerManager.serversVersion, torrents) { DownloadDirManager.getAllDirs(context, torrents) }

                        LaunchedEffect(downloadDirInput) {
                            val path = downloadDirInput.trim()
                            if (path.isNotEmpty() && rpcUrl.isNotEmpty()) {
                                val (effUrl, effUser, effPass) = DialogUtils.getEffectiveCredentials(context, rpcUrl, user, pass)
                                val service = TransmissionClient.getService(effUrl, effUser, effPass)
                                service.rpc(effUrl, null, RpcRequest("free-space", mapOf("path" to path)))
                                    .enqueue(object : retrofit2.Callback<RpcResponse<Map<String, Any>>> {
                                        override fun onResponse(call: retrofit2.Call<RpcResponse<Map<String, Any>>>, response: retrofit2.Response<RpcResponse<Map<String, Any>>>) {
                                            if (response.isSuccessful) {
                                                val size = (response.body()?.arguments?.get("size-bytes") as? Double)?.toLong() ?: 0L
                                                freeSpaceText = context.getString(R.string.free_space_label, FormatUtils.formatSize(size))
                                            }
                                        }
                                        override fun onFailure(call: retrofit2.Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
                                    })
                            } else {
                                freeSpaceText = ""
                            }
                        }

                        LiquidGlassDialog(
                            onDismissRequest = { 
                                showAddTorrentDialogState = false
                                initialUrlForAdd = null
                                initialFileUriForAdd = null
                                onCloseExternalAddTorrentDialog?.invoke()
                            },
                            backdropLayer = backdropLayer,
                            boxPositionInRoot = boxPositionInRoot,
                            refractionDp = dialogRefractionDp,
                            refractionHeightDp = dialogRefractionHeightDp,
                            blurRadiusDp = dialogBlurRadiusDp,
                            saturationBoost = dialogSaturationBoost,
                            contrast = dialogContrast,
                            whitePoint = dialogWhitePoint,
                            bottomLeftContent = {
                                if (freeSpaceText.isNotEmpty()) {
                                    Text(
                                        text = freeSpaceText,
                                        fontSize = 13.5.sp,
                                        color = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72)
                                    )
                                }
                            },
                            titleContent = {
                                val activeServer = remember(ServerManager.serversVersion) { ServerManager.getActiveServer(context) }
                                val accentColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF)
                                val avatarUri = activeServer?.avatarUri ?: ""
                                val alias = activeServer?.alias ?: "Transmission"
                                val initialChar = alias.trim().take(1).ifEmpty { "S" }
                                val serverUrlLower = activeServer?.rpcUrl?.trim()?.lowercase() ?: ""

                                var isSelfSigned by remember(serverUrlLower) { mutableStateOf(false) }

                                LaunchedEffect(serverUrlLower) {
                                    if (serverUrlLower.startsWith("https://")) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            isSelfSigned = SslCheckUtils.isSelfSignedSsl(serverUrlLower)
                                        }
                                    }
                                }

                                val badgeColor = when {
                                    serverUrlLower.startsWith("http://") -> Color(0xFFFF5252)
                                    serverUrlLower.startsWith("https://") && isSelfSigned -> Color(0xFFFFC107)
                                    else -> null
                                }

                                val avatarBitmap = remember(avatarUri) {
                                    if (avatarUri.isNotBlank()) {
                                        try {
                                            val file = java.io.File(avatarUri)
                                            if (file.exists()) {
                                                android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                                            } else null
                                        } catch (_: Exception) { null }
                                    } else null
                                }

                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .then(
                                            if (isDeveloperMode) {
                                                Modifier.pointerInput(Unit) {
                                                    detectVerticalDragGestures { change, dragAmount ->
                                                        if (dragAmount < -12f) { // 开发者模式上滑调起调参
                                                            change.consume()
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            showDialogTuningInspector = true
                                                        }
                                                    }
                                                }
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = accentColor,
                                        border = BorderStroke(1.5.dp, Color.White),
                                        shadowElevation = 4.dp,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (avatarBitmap != null) {
                                                Image(
                                                    bitmap = avatarBitmap,
                                                    contentDescription = alias,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                )
                                            } else {
                                                Text(
                                                    text = initialChar,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }

                                    if (badgeColor != null) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 2.dp, y = (-2).dp)
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(if (isDark) Color(0xFF1F2A38) else Color.White)
                                                .padding(1.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_exclamation_circle),
                                                contentDescription = "连接安全提示",
                                                tint = badgeColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButtonText = stringResource(R.string.btn_confirm),
                            confirmButtonColor = Color(0xFF1D88E3),
                            onConfirm = {
                                val url = torrentUrlInput.trim()
                                val dir = downloadDirInput.trim()
                                val days = hrDaysInput.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                                val uri = initialFileUriForAdd

                                showAddTorrentDialogState = false
                                initialUrlForAdd = null
                                initialFileUriForAdd = null

                                DialogUtils.performAddTorrent(
                                    context = context,
                                    rpcUrl = rpcUrl,
                                    user = user,
                                    pass = pass,
                                    url = url,
                                    downloadDir = dir,
                                    hrDays = days,
                                    fileUri = uri,
                                    onSuccess = {
                                        viewModel.refreshTorrents(rpcUrl, user, pass)
                                    }
                                )
                            }
                        ) {
                            OutlinedTextField(
                                value = if (initialFileUriForAdd != null) (initialFileUriForAdd?.lastPathSegment ?: stringResource(R.string.msg_local_file_selected)) else torrentUrlInput,
                                onValueChange = { torrentUrlInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (initialFileUriForAdd != null || torrentUrlInput.isEmpty()) {
                                            handlePickFile()
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                label = { Text(stringResource(R.string.hint_torrent_url)) },
                                trailingIcon = {
                                    IconButton(onClick = { handlePickFile() }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_file_open),
                                            contentDescription = "打开本地种子文件",
                                            tint = Color(0xFF1D88E3)
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF),
                                    unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                                    focusedLabelColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF),
                                    unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                                    focusedTextColor = if (isDark) Color.White else Color(0xFF2D3436),
                                    unfocusedTextColor = if (isDark) Color.White else Color(0xFF2D3436)
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            DirectoryDropdownTextField(
                                value = downloadDirInput,
                                onValueChange = { downloadDirInput = it },
                                allDirs = allDirs,
                                label = stringResource(R.string.hint_download_dir),
                                isDark = isDark
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = hrDaysInput,
                                onValueChange = { hrDaysInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                label = { Text(stringResource(R.string.label_hr)) },
                                trailingIcon = {
                                    QuickHrSlidingSelector(
                                        selectedDay = hrDaysInput,
                                        onDaySelected = { hrDaysInput = it }
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF),
                                    unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                                    focusedLabelColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF),
                                    unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                                    focusedTextColor = if (isDark) Color.White else Color(0xFF2D3436),
                                    unfocusedTextColor = if (isDark) Color.White else Color(0xFF2D3436)
                                )
                            )
                        }

                        if (showDialogTuningInspector && isDeveloperMode) {
                            LiquidGlassTuningInspector(
                                refractionDp = dialogRefractionDp,
                                refractionHeightDp = dialogRefractionHeightDp,
                                blurRadiusDp = dialogBlurRadiusDp,
                                saturationBoost = dialogSaturationBoost,
                                contrast = dialogContrast,
                                whitePoint = dialogWhitePoint,
                                onRefractionChange = { dialogRefractionDp = it },
                                onRefractionHeightChange = { dialogRefractionHeightDp = it },
                                onBlurRadiusChange = { dialogBlurRadiusDp = it },
                                onSaturationBoostChange = { dialogSaturationBoost = it },
                                onContrastChange = { dialogContrast = it },
                                onWhitePointChange = { dialogWhitePoint = it },
                                onReset = {
                                    val defRefraction = -60f
                                    val defHeight = if (isDark) 12f else 4f
                                    val defBlur = 32f
                                    val defSaturation = 1.50f
                                    val defContrast = 0.0f
                                    val defWhitePoint = if (isDark) 0.10f else 0.15f

                                    dialogRefractionDp = defRefraction
                                    dialogRefractionHeightDp = defHeight
                                    dialogBlurRadiusDp = defBlur
                                    dialogSaturationBoost = defSaturation
                                    dialogContrast = defContrast
                                    dialogWhitePoint = defWhitePoint

                                    SettingsManager.setDialogRefraction(context, defRefraction)
                                    SettingsManager.setDialogHeight(context, defHeight)
                                    SettingsManager.setDialogBlur(context, defBlur)
                                    SettingsManager.setDialogSaturation(context, defSaturation)
                                    SettingsManager.setDialogContrast(context, defContrast)
                                    SettingsManager.setDialogWhitePoint(context, defWhitePoint)
                                },
                                onSave = {
                                    SettingsManager.setDialogRefraction(context, dialogRefractionDp)
                                    SettingsManager.setDialogHeight(context, dialogRefractionHeightDp)
                                    SettingsManager.setDialogBlur(context, dialogBlurRadiusDp)
                                    SettingsManager.setDialogSaturation(context, dialogSaturationBoost)
                                    SettingsManager.setDialogContrast(context, dialogContrast)
                                    SettingsManager.setDialogWhitePoint(context, dialogWhitePoint)
                                    Toast.makeText(context, "弹窗玻璃参数保存成功", Toast.LENGTH_SHORT).show()
                                    showDialogTuningInspector = false
                                },
                                onDismiss = { showDialogTuningInspector = false },
                            )
                        }

                        // 6. 在线更新弹窗 (检测到 GitHub Releases 新版本时弹窗推送)
                        if (showUpdateDialogState && updateInfoState != null) {
                            val info = updateInfoState!!
                            LiquidGlassDialog(
                                onDismissRequest = { showUpdateDialogState = false },
                                title = "发现新版本 ${info.latestVersion}",
                                confirmButtonText = "前往下载",
                                confirmButtonColor = Color(0xFF1D88E3),
                                onConfirm = {
                                    showUpdateDialogState = false
                                    UpdateCheckUtils.openReleasesPage(context, info.releaseUrl)
                                },
                                bottomLeftContent = {
                                    TextButton(onClick = { showUpdateDialogState = false }) {
                                        Text("稍后再说", fontSize = 13.5.sp, color = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72))
                                    }
                                }
                            ) {
                                Text(
                                    text = "发现 TransSync 新版本，点击【前往下载】可直接前往 GitHub Releases 页面下载最新 APK。",
                                    fontSize = 14.5.sp,
                                    color = if (isDark) Color.White else Color(0xFF2D3436)
                                )
                                if (info.releaseNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "更新日志：\n${info.releaseNotes}",
                                        fontSize = 12.5.sp,
                                        color = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72)
                                    )
                                }
                            }
                        }
                    }
                }
            }

    if (isLandscape) {
        Row(
            modifier = modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
            ) {
                DrawerFilterContent(
                    viewModel = viewModel,
                    currentFilter = currentFilter,
                    rpcUrl = rpcUrl,
                    onSelectFilter = { selectedFilter ->
                        currentFilter = selectedFilter
                        viewModel.setFilter(selectedFilter)
                        rightPaneTarget = RightPaneTarget.List
                    },
                    onServerSwitched = {
                        rightPaneTarget = RightPaneTarget.List
                    },
                    onOpenSettings = {
                        rightPaneTarget = RightPaneTarget.Settings
                    }
                )
            }

            VerticalDivider(
                color = if (isDark) Color(0xFF34495E) else Color(0xFFE0E0E0),
                thickness = 1.dp
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // 1. 种子列表处于最底层静候
                MainScaffoldContent()

                // 2. 处于详情或设置模式时叠加在顶层，右滑手势返回时完美露出底部的种子列表
                when (val target = rightPaneTarget) {
                    is RightPaneTarget.List -> {
                        // 处于列表模式，不加额外覆盖
                    }
                    is RightPaneTarget.Detail -> {
                        TorrentDetailScreen(
                            torrentId = target.torrentId,
                            rpcUrl = rpcUrl,
                            user = user,
                            pass = pass,
                            onBackClick = { rightPaneTarget = RightPaneTarget.List }
                        )
                    }
                    is RightPaneTarget.Settings -> {
                        SettingsScreen(
                            onBackClick = { rightPaneTarget = RightPaneTarget.List }
                        )
                    }
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {},
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = (drawerOffsetAnim.value + dragAmount).coerceIn(0f, drawerWidthPx)
                            scope.launch {
                                drawerOffsetAnim.snapTo(newOffset)
                            }
                        },
                        onDragEnd = {
                            val shouldOpen = drawerOffsetAnim.value > drawerWidthPx * 0.4f
                            isDrawerOpen = shouldOpen
                            scope.launch {
                                drawerOffsetAnim.animateTo(
                                    targetValue = if (shouldOpen) drawerWidthPx else 0f,
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                                )
                            }
                        },
                        onDragCancel = {
                            val shouldOpen = drawerOffsetAnim.value > drawerWidthPx * 0.4f
                            isDrawerOpen = shouldOpen
                            scope.launch {
                                drawerOffsetAnim.animateTo(
                                    targetValue = if (shouldOpen) drawerWidthPx else 0f,
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                                )
                            }
                        }
                    )
                }
        ) {
            // 1. 左侧侧边栏 (宽 300dp，独立拥有自身的壁纸背景)
            Box(
                modifier = Modifier
                    .width(drawerWidthDp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = currentOffset - drawerWidthPx
                    }
            ) {
                WallpaperBackground()

                DrawerFilterContent(
                    viewModel = viewModel,
                    currentFilter = currentFilter,
                    rpcUrl = rpcUrl,
                    onSelectFilter = { selectedFilter ->
                        currentFilter = selectedFilter
                        viewModel.setFilter(selectedFilter)
                        isDrawerOpen = false
                    },
                    onServerSwitched = {
                        isDrawerOpen = false
                    },
                    onOpenSettings = {
                        isDrawerOpen = false
                        val intent = Intent(context, SettingsActivity::class.java)
                        context.startActivity(intent)
                    }
                )
            }

            // 3. 右侧主页面 (被侧边栏向右推开平移)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = currentOffset
                    }
            ) {
                MainScaffoldContent()

                // 当侧边栏平移打开时，点击主页面区域平滑收起侧边栏
                if (currentOffset > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { isDrawerOpen = false }
                            )
                    )
                }
            }
        }
    }
}

/**
 * H&R 考核天数 0 3 5 数字滑动/点击选择器
 */
@Composable
fun QuickHrSlidingSelector(
    selectedDay: String,
    onDaySelected: (String) -> Unit
) {
    val days = listOf("0", "3", "5")
    val isDark = isSystemInDarkTheme()
    var containerWidthPx by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = Modifier
            .padding(end = 12.dp)
            .onGloballyPositioned { layoutCoordinates ->
                containerWidthPx = layoutCoordinates.size.width.toFloat()
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (containerWidthPx > 0f) {
                            val fraction = (offset.x / containerWidthPx).coerceIn(0f, 0.999f)
                            val index = (fraction * days.size).toInt().coerceIn(0, days.size - 1)
                            onDaySelected(days[index])
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (containerWidthPx > 0f) {
                            val fraction = (change.position.x / containerWidthPx).coerceIn(0f, 0.999f)
                            val index = (fraction * days.size).toInt().coerceIn(0, days.size - 1)
                            onDaySelected(days[index])
                        }
                    }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        days.forEach { day ->
            val isSelected = selectedDay == day

            val animatedScale by animateFloatAsState(
                targetValue = if (isSelected) 1.45f else 1.0f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
                label = "magnifierScale"
            )

            Text(
                text = day,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isSelected) (if (isDark) Color(0xFF00B0FF) else Color(0xFF1D88E3)) else (if (isDark) Color(0xFF788896) else Color(0xFF90A4AE))
                ),
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                    }
                    .clickable { onDaySelected(day) }
            )
        }
    }
}

/**
 * 判断种子是否已完成 H&R 考核要求（用于删除种子弹窗中智能预设 "同时删除数据" 勾选状态）
 */
private fun isHrFinished(torrent: Torrent): Boolean {
    val hrLabel = torrent.labels?.find { it.startsWith("HR:") } ?: return true
    val hours = hrLabel.substringAfter("HR:").toDoubleOrNull() ?: 0.0
    if (hours <= 0.0) return true

    if (torrent.percentDone < 1.0) return false

    val doneDateMs = torrent.doneDate * 1000L
    val currentTime = System.currentTimeMillis()
    val totalRequiredMs = (hours * 3600 * 1000L).toLong()
    val elapsedMs = currentTime - doneDateMs
    val remainingMs = totalRequiredMs - elapsedMs
    val bufferMs = 30 * 60 * 1000L

    return remainingMs <= -bufferMs
}

@androidx.compose.ui.tooling.preview.Preview(name = "主列表页 - 浅色模式", showBackground = true)
@Composable
fun TorrentListScreen_Light_Preview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF0F2F5),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val sampleTorrents = listOf(
                    Torrent(
                        id = 1,
                        name = "Jumanji.The.Next.Level.2026.2160p.HQ.WEB-DL.mkv",
                        totalSize = 44238000000L,
                        percentDone = 0.605,
                        rateDownload = 1200000L,
                        rateUpload = 450000L,
                        status = 4,
                        displayProgress = 605,
                        displaySize = "26.7 GB / 44.2 GB",
                        displayDownloadSpeed = "1.2 MB/s ↓",
                        displayUploadSpeed = "450 KB/s ↑",
                        displayStats = "已上传 160.0 GB (分享率 6.04)",
                        trackerName = "Google",
                    ),
                    Torrent(
                        id = 2,
                        name = "Inception.2010.1080p.BluRay.x264.mkv",
                        totalSize = 15400000000L,
                        percentDone = 1.0,
                        rateDownload = 0L,
                        rateUpload = 850000L,
                        status = 6,
                        displayProgress = 1000,
                        displaySize = "14.3 GB",
                        displayDownloadSpeed = "0 B/s ↓",
                        displayUploadSpeed = "850 KB/s ↑",
                        displayStats = "已上传 45.2 GB (分享率 3.16)",
                        trackerName = "Google",
                    ),
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 60.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(sampleTorrents) { torrent ->
                        TorrentItemCard(
                            torrent = torrent,
                            isSelected = false,
                            isTrackerBlurEnabled = false,
                            revealedTrackerNames = emptySet(),
                            onClick = {},
                            onLongClick = {},
                            onToggleStatus = {},
                        )
                    }
                }

                FloatingTopControls(
                    titleText = "全部任务",
                    sizeText = "58.5 GB",
                    altSpeedEnabled = false,
                    selectedCount = 0,
                    onMenuClick = {},
                    onTurtleClick = {},
                    onCloseSelection = {},
                    onSelectAll = {},
                    onDeleteSelected = {},
                    onStartSelected = {},
                    onStopSelected = {},
                    onRenameSelected = {},
                    onSetLocationSelected = {},
                    onSetHrSelected = {},
                    onVerifySelected = {},
                    onReannounceSelected = {},
                    isDark = false,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                LiquidBottomBarContent(
                    dlSpeed = "1.2 MB/s",
                    ulSpeed = "1.3 MB/s",
                    onSearchQueryChange = {},
                    onSearchToggle = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "主列表页 - 深色模式", showBackground = true)
@Composable
fun TorrentListScreen_Dark_Preview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF161F29),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val sampleTorrents = listOf(
                    Torrent(
                        id = 1,
                        name = "Jumanji.The.Next.Level.2026.2160p.HQ.WEB-DL.mkv",
                        totalSize = 44238000000L,
                        percentDone = 0.605,
                        rateDownload = 1200000L,
                        rateUpload = 450000L,
                        status = 4,
                        displayProgress = 605,
                        displaySize = "26.7 GB / 44.2 GB",
                        displayDownloadSpeed = "1.2 MB/s ↓",
                        displayUploadSpeed = "450 KB/s ↑",
                        displayStats = "已上传 160.0 GB (分享率 6.04)",
                        trackerName = "Google",
                    ),
                    Torrent(
                        id = 2,
                        name = "Inception.2010.1080p.BluRay.x264.mkv",
                        totalSize = 15400000000L,
                        percentDone = 1.0,
                        rateDownload = 0L,
                        rateUpload = 850000L,
                        status = 6,
                        displayProgress = 1000,
                        displaySize = "14.3 GB",
                        displayDownloadSpeed = "0 B/s ↓",
                        displayUploadSpeed = "850 KB/s ↑",
                        displayStats = "已上传 45.2 GB (分享率 3.16)",
                        trackerName = "Google",
                    ),
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 60.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(sampleTorrents) { torrent ->
                        TorrentItemCard(
                            torrent = torrent,
                            isSelected = false,
                            isTrackerBlurEnabled = false,
                            revealedTrackerNames = emptySet(),
                            onClick = {},
                            onLongClick = {},
                            onToggleStatus = {},
                        )
                    }
                }

                FloatingTopControls(
                    titleText = "正在下载",
                    sizeText = "44.2 GB",
                    altSpeedEnabled = true,
                    selectedCount = 0,
                    onMenuClick = {},
                    onTurtleClick = {},
                    onCloseSelection = {},
                    onSelectAll = {},
                    onDeleteSelected = {},
                    onStartSelected = {},
                    onStopSelected = {},
                    onRenameSelected = {},
                    onSetLocationSelected = {},
                    onSetHrSelected = {},
                    onVerifySelected = {},
                    onReannounceSelected = {},
                    isDark = true,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                LiquidBottomBarContent(
                    dlSpeed = "1.2 MB/s",
                    ulSpeed = "1.3 MB/s",
                    onSearchQueryChange = {},
                    onSearchToggle = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
