package com.kuangru52.transsync

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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var isFabVisible by remember { mutableStateOf(true) }
    var isSearchActive by remember { mutableStateOf(false) }

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
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
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
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (isDark) Color(0xFF161F29) else Color(0xFFF0F2F5),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {},
            floatingActionButton = {
                AnimatedVisibility(
                    visible = isFabVisible && selectedIds.isEmpty(),
                    enter = scaleIn(animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)) + fadeIn(),
                    exit = scaleOut(animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)) + fadeOut()
                ) {
                    val fabRotation by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (showAddTorrentDialogState) 135f else 0f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "fabRotation"
                    )

                    FloatingActionButton(
                        onClick = {
                            onAddClick()
                            showAddTorrentDialogState = !showAddTorrentDialogState
                        },
                        containerColor = if (isDark) Color(0xFF2196F3) else Color(0xFFFF5252),
                        contentColor = Color.White,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 12.dp),
                        modifier = Modifier.padding(end = 24.dp, bottom = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加种子",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    rotationZ = fabRotation
                                }
                        )
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.End,
            content = { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(innerPadding)
                ) {
                    val backdropLayer = rememberGraphicsLayer()
                    var boxPositionInRoot by remember { mutableStateOf(Offset.Zero) }

                    if (torrents.isEmpty() && !isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无种子任务",
                                color = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72),
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coordinates ->
                                    boxPositionInRoot = coordinates.positionInRoot()
                                }
                                .drawWithContent {
                                    backdropLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    drawContent()
                                }
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(),
                                contentPadding = PaddingValues(
                                    start = 0.dp,
                                    end = 0.dp,
                                    top = 60.dp,
                                    bottom = 80.dp
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

                    FloatingTopControls(
                        titleText = getFilterTitleText(currentFilter),
                        sizeText = FormatUtils.formatSize(totalSize),
                        altSpeedEnabled = altSpeedEnabled,
                        selectedCount = selectedIds.size,
                        onMenuClick = {
                            if (!isLandscape) {
                                scope.launch { drawerState.open() }
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
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

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
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    // 1. 重命名 Compose 液态玻璃 120 FPS 采样弹窗
                    renameTorrentTarget?.let { targetTorrent ->
                        var newNameInput by remember(targetTorrent) { mutableStateOf(targetTorrent.name) }
                        val isConfirmEnabled = newNameInput.trim().isNotEmpty() && newNameInput.trim() != targetTorrent.name

                        LiquidGlassDialog(
                            onDismissRequest = { renameTorrentTarget = null },
                            backdropLayer = backdropLayer,
                            title = stringResource(R.string.dialog_rename_title),
                            confirmButtonText = stringResource(R.string.btn_confirm),
                            confirmButtonColor = Color(0xFF1D88E3),
                            isConfirmEnabled = isConfirmEnabled,
                            onConfirm = {
                                val nameToSave = newNameInput.trim()
                                renameTorrentTarget = null
                                DialogUtils.performRename(
                                    context = context,
                                    rpcUrl = rpcUrl,
                                    user = user,
                                    pass = pass,
                                    torrentId = targetTorrent.id,
                                    torrentHash = targetTorrent.hash,
                                    currentName = targetTorrent.name,
                                    newName = nameToSave,
                                    onSuccess = {
                                        selectedIds = emptySet()
                                        viewModel.refreshTorrents(rpcUrl, user, pass)
                                    }
                                )
                            }
                        ) {
                            OutlinedTextField(
                                value = newNameInput,
                                onValueChange = { newNameInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                label = { Text(stringResource(R.string.hint_new_name)) },
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
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.delete_confirm_msg, idsToDelete.size),
                                fontSize = 16.sp,
                                color = if (isDark) Color.White else Color(0xFF2D3436)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { deleteLocalData = !deleteLocalData }
                            ) {
                                Checkbox(
                                    checked = deleteLocalData,
                                    onCheckedChange = { deleteLocalData = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFFFF5252)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.cb_delete_data),
                                    fontSize = 14.sp,
                                    color = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72)
                                )
                            }
                        }
                    }

                    // 3. 设置保存位置 Compose 液态玻璃 120 FPS 采样弹窗
                    setLocationTargetIds?.let { targetIds ->
                        val firstTorrent = torrents.find { it.id == targetIds.firstOrNull() }
                        var locationInput by remember(targetIds) { mutableStateOf(firstTorrent?.downloadDir ?: "/downloads") }
                        var moveData by remember { mutableStateOf(true) }
                        var freeSpaceText by remember { mutableStateOf("") }

                        val allDirs = remember(ServerManager.serversVersion, torrents) { DownloadDirManager.getAllDirs(context, torrents) }

                        LaunchedEffect(locationInput) {
                            val path = locationInput.trim()
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
                            onDismissRequest = { setLocationTargetIds = null },
                            backdropLayer = backdropLayer,
                            title = stringResource(R.string.dialog_set_location_title),
                            confirmButtonText = stringResource(R.string.btn_confirm),
                            confirmButtonColor = Color(0xFF1D88E3),
                            onConfirm = {
                                val loc = locationInput.trim()
                                setLocationTargetIds = null
                                if (loc.isNotEmpty()) {
                                    val hashesTarget = torrents.filter { targetIds.contains(it.id) }.map { it.hash }
                                    DialogUtils.performSetLocation(
                                        context = context,
                                        rpcUrl = rpcUrl,
                                        user = user,
                                        pass = pass,
                                        torrentIds = targetIds,
                                        torrentHashes = hashesTarget,
                                        newLocation = loc,
                                        moveData = moveData,
                                        onSuccess = {
                                            selectedIds = emptySet()
                                            viewModel.refreshTorrents(rpcUrl, user, pass)
                                        }
                                    )
                                }
                            }
                        ) {
                            DirectoryDropdownTextField(
                                value = locationInput,
                                onValueChange = { locationInput = it },
                                allDirs = allDirs,
                                label = stringResource(R.string.hint_download_dir),
                                isDark = isDark
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { moveData = !moveData }
                                ) {
                                    Checkbox(
                                        checked = moveData,
                                        onCheckedChange = { moveData = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFF1D88E3)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.cb_move_data),
                                        fontSize = 14.sp,
                                        color = if (isDark) Color.White else Color(0xFF2D3436)
                                    )
                                }

                                if (freeSpaceText.isNotEmpty()) {
                                    Text(
                                        text = freeSpaceText,
                                        fontSize = 14.sp,
                                        color = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72)
                                    )
                                }
                            }
                        }
                    }

                    // 4. 设置 H&R 考核 Compose 液态玻璃 120 FPS 采样弹窗
                    setHrTargetIds?.let { targetIds ->
                        val firstTorrent = torrents.find { it.id == targetIds.firstOrNull() }
                        val currentHrLabel = firstTorrent?.labels?.find { it.startsWith("HR:") }
                        val initialDaysStr = if (currentHrLabel != null) {
                            val hrs = currentHrLabel.substringAfter("HR:").toDoubleOrNull() ?: 0.0
                            if (hrs > 0) (hrs / 24.0).let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() } else "0"
                        } else "0"

                        var hrDaysInput by remember(targetIds) { mutableStateOf(initialDaysStr) }

                        LiquidGlassDialog(
                            onDismissRequest = { setHrTargetIds = null },
                            backdropLayer = backdropLayer,
                            title = stringResource(R.string.menu_set_hr),
                            confirmButtonText = stringResource(R.string.btn_confirm),
                            confirmButtonColor = Color(0xFF1D88E3),
                            onConfirm = {
                                val days = hrDaysInput.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
                                setHrTargetIds = null
                                val hashesTarget = torrents.filter { targetIds.contains(it.id) }.map { it.hash }
                                DialogUtils.performSetHr(
                                    context = context,
                                    rpcUrl = rpcUrl,
                                    user = user,
                                    pass = pass,
                                    torrentIds = targetIds,
                                    torrentHashes = hashesTarget,
                                    days = days,
                                    onSuccess = {
                                        selectedIds = emptySet()
                                        viewModel.refreshTorrents(rpcUrl, user, pass)
                                    }
                                )
                            }
                        ) {
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
                                onRefractionChange = {
                                    dialogRefractionDp = it
                                    SettingsManager.setDialogRefraction(context, it)
                                },
                                onRefractionHeightChange = {
                                    dialogRefractionHeightDp = it
                                    SettingsManager.setDialogHeight(context, it)
                                },
                                onBlurRadiusChange = {
                                    dialogBlurRadiusDp = it
                                    SettingsManager.setDialogBlur(context, it)
                                },
                                onSaturationBoostChange = {
                                    dialogSaturationBoost = it
                                    SettingsManager.setDialogSaturation(context, it)
                                },
                                onReset = {
                                    val defRefraction = if (isDark) -11f else -27f
                                    val defHeight = if (isDark) 21f else 30f
                                    val defBlur = if (isDark) 22f else 19f
                                    val defSaturation = if (isDark) 2.1f else 2.5f

                                    dialogRefractionDp = defRefraction
                                    dialogRefractionHeightDp = defHeight
                                    dialogBlurRadiusDp = defBlur
                                    dialogSaturationBoost = defSaturation

                                    SettingsManager.setDialogRefraction(context, defRefraction)
                                    SettingsManager.setDialogHeight(context, defHeight)
                                    SettingsManager.setDialogBlur(context, defBlur)
                                    SettingsManager.setDialogSaturation(context, defSaturation)
                                },
                                onSave = {
                                    SettingsManager.setDialogRefraction(context, dialogRefractionDp)
                                    SettingsManager.setDialogHeight(context, dialogRefractionHeightDp)
                                    SettingsManager.setDialogBlur(context, dialogBlurRadiusDp)
                                    SettingsManager.setDialogSaturation(context, dialogSaturationBoost)
                                    Toast.makeText(context, "弹窗玻璃参数保存成功", Toast.LENGTH_SHORT).show()
                                    showDialogTuningInspector = false
                                },
                                onDismiss = { showDialogTuningInspector = false }
                            )
                        }
                    }
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
        )
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
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = if (isDark) Color(0xFF161F29) else Color(0xFF455A64),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.width(320.dp)
                ) {
                    DrawerFilterContent(
                        viewModel = viewModel,
                        currentFilter = currentFilter,
                        rpcUrl = rpcUrl,
                        onSelectFilter = { selectedFilter ->
                            currentFilter = selectedFilter
                            viewModel.setFilter(selectedFilter)
                            scope.launch { drawerState.close() }
                        },
                        onServerSwitched = {
                            // 竖屏模式下，切换服务器后留在侧边栏，不关闭 drawer
                        }
                    )
                }
            }
        ) {
            MainScaffoldContent()
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

            val animatedScale by androidx.compose.animation.core.animateFloatAsState(
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
