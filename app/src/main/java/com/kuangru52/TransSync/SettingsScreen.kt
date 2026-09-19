package com.kuangru52.transsync

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * 完整设置界面：
 * - 支持从左往右滑动返回/退出手势 (detectHorizontalDragGestures)
 * - 【最顶端卡片】Transmission / qBittorrent 服务器连接配置卡片 (直接在卡片内呈现全量多服务器管理/切换/编辑/添加)
 * - 外观主题：横排 3 按键 [跟随系统 | 浅色模式 | 深色模式]
 * - 应用语言：横排 3 按键 [跟随系统 | 简体中文 | English]
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val backdropLayer = rememberGraphicsLayer()
    var serversList by remember { mutableStateOf(ServerManager.getServers(context)) }
    var activeServer by remember { mutableStateOf(ServerManager.getActiveServer(context)) }

    var currentThemeMode by remember { mutableStateOf(SettingsManager.getThemeMode(context)) }
    var currentLanguage by remember { mutableStateOf(SettingsManager.getLanguage(context)) }
    var isPrivacyMode by remember { mutableStateOf(SettingsManager.isPrivacyMode(context)) }
    var isDeveloperMode by remember { mutableStateOf(SettingsManager.isDeveloperMode(context)) }
    var customTrackerMappings by remember { mutableStateOf(SettingsManager.getCustomTrackerMappings(context)) }

    var editingServerTarget by remember { mutableStateOf<ServerConfig?>(null) }
    var serverToDeleteTarget by remember { mutableStateOf<ServerConfig?>(null) }
    var showCreateServerDialog by remember { mutableStateOf(value = false) }
    var showAddTrackerDialog by remember { mutableStateOf(value = false) }

    // 从左往右滑动返回/退出手势偏移量
    var swipeOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedSwipeOffset by animateFloatAsState(
        targetValue = swipeOffsetX,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "swipeOffset",
    )

    val cardBgColor = if (isDark) Color(0xFF1F2A38) else Color.White
    val cardBorderColor = if (isDark) Color(0xFF34495E) else Color(0xFFE0E0E0)
    val primaryTextColor = if (isDark) Color.White else Color(0xFF2D3436)
    val secondaryTextColor = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72)
    val accentColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF)
    val topBarBgColor = if (isDark) Color(0xFF161F29) else Color(0xFF455A64)

    val settingsView = LocalView.current
    var settingsViewLocation by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val loc = IntArray(2)
                settingsView.getLocationOnScreen(loc)
                val offsetInWindow = coordinates.positionInWindow()
                settingsViewLocation = Offset(
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
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        swipeOffsetX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if ((dragAmount > 0) || (swipeOffsetX > 0)) {
                            change.consume()
                            swipeOffsetX = (swipeOffsetX + dragAmount).coerceAtLeast(0f)
                        }
                    },
                    onDragEnd = {
                        if (swipeOffsetX > 120.dp.toPx()) {
                            onBackClick()
                        } else {
                            swipeOffsetX = 0f
                        }
                    },
                    onDragCancel = {
                        swipeOffsetX = 0f
                    }
                )
            }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (isDark) Color(0xFF161F29) else Color(0xFFF0F2F5),
            topBar = {
                Surface(
                    color = topBarBgColor,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = stringResource(R.string.nav_settings),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 【置于最顶端】1. 服务器连接配置卡片 (直接在卡片内呈现全量服务器管理与一键切换，默认展开，点击标题可折叠/展开)
                var isServerListExpanded by remember { mutableStateOf(true) }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { isServerListExpanded = !isServerListExpanded }
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_title_server),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    painter = painterResource(id = if (isServerListExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_right),
                                    contentDescription = if (isServerListExpanded) "折叠列表" else "展开列表",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(onClick = { showCreateServerDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "添加",
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        if (isServerListExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                            serversList.forEach { server ->
                                val isActive = server.id == (activeServer?.id ?: "")

                                val avatarBitmap = remember(server.avatarUri) {
                                    if (server.avatarUri.isNotBlank()) {
                                        try {
                                            val file = java.io.File(server.avatarUri)
                                            if (file.exists()) {
                                                android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                                            } else null
                                        } catch (_: Exception) { null }
                                    } else null
                                }

                                Surface(
                                    onClick = {
                                        if (!isActive) {
                                            val oldActiveServer = activeServer
                                            ServerManager.setActiveServer(context, server.id)
                                            serversList = ServerManager.getServers(context)
                                            activeServer = ServerManager.getActiveServer(context)

                                            val isCrossClientSwitch = (oldActiveServer?.clientType != server.clientType) ||
                                                    (server.clientType == ServerConfig.CLIENT_QBITTORRENT)

                                            if (isCrossClientSwitch) {
                                                Toast.makeText(context, "正在无缝重启应用以生效 ${server.alias}...", Toast.LENGTH_SHORT).show()
                                                AppRestartUtils.restartApp(context)
                                            } else {
                                                Toast.makeText(context, "已切换至: ${server.alias}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isActive) accentColor.copy(alpha = 0.12f) else (if (isDark) Color(0xFF131B24) else Color(0xFFF8F9FA)),
                                    border = BorderStroke(1.dp, if (isActive) accentColor else (if (isDark) Color(0x22FFFFFF) else Color(0xFFE9ECEF)))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val urlLower = server.rpcUrl.trim().lowercase()
                                        var isSelfSigned by remember(server.rpcUrl) { mutableStateOf(false) }

                                        LaunchedEffect(server.rpcUrl) {
                                            if (urlLower.startsWith("https://")) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    isSelfSigned = SslCheckUtils.isSelfSignedSsl(server.rpcUrl)
                                                }
                                            }
                                        }

                                        val badgeColor = when {
                                            urlLower.startsWith("http://") -> Color(0xFFFF5252) // 红色圆叹号：HTTP 明文不安全
                                            urlLower.startsWith("https://") && isSelfSigned -> Color(0xFFFFC107) // 黄色圆叹号：自签名 / mkcert 证书
                                            else -> null // Let's Encrypt 等权威 CA -> 100% 受信任，无警告
                                        }

                                        Box(
                                            modifier = Modifier.size(36.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isActive) accentColor else (if (isDark) Color(0x33FFFFFF) else Color(0xFFE0E0E0)),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    if (avatarBitmap != null) {
                                                        Image(
                                                            bitmap = avatarBitmap,
                                                            contentDescription = server.alias,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                                        )
                                                    } else {
                                                        Text(
                                                            text = server.alias.trim().take(1).ifEmpty { "S" },
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Bold,
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
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isDark) Color(0xFF1F2A38) else Color.White)
                                                        .padding(1.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_exclamation_circle),
                                                        contentDescription = "连接安全提示",
                                                        tint = badgeColor,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = server.alias.ifEmpty { "Transmission" },
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = primaryTextColor
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "(${if (server.clientType == ServerConfig.CLIENT_QBITTORRENT) "qBittorrent" else "Transmission"})",
                                                    fontSize = 11.5.sp,
                                                    color = secondaryTextColor
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = server.rpcUrl,
                                                fontSize = 12.sp,
                                                color = secondaryTextColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = if (isPrivacyMode) Modifier.blur(6.dp) else Modifier
                                            )
                                        }

                                        IconButton(onClick = { editingServerTarget = server }, modifier = Modifier.size(32.dp)) {
                                            Icon(imageVector = Icons.Default.Edit, contentDescription = "编辑", tint = secondaryTextColor, modifier = Modifier.size(18.dp))
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        IconButton(
                                            onClick = {
                                                serverToDeleteTarget = server
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "删除", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }

                // 2. 外观主题模式卡片 (横排 3 按键)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_title_theme),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        CompactSegmentedGroup(
                            options = listOf(
                                stringResource(R.string.settings_theme_system) to "system",
                                stringResource(R.string.settings_theme_light) to "light",
                                stringResource(R.string.settings_theme_dark) to "dark"
                            ),
                            selectedKey = currentThemeMode,
                            onOptionSelected = { mode ->
                                currentThemeMode = mode
                                SettingsManager.setThemeMode(context, mode)
                            }
                        )
                    }
                }

                // 3. 应用语言卡片 (横排 3 按键)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_title_language),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        CompactSegmentedGroup(
                            options = listOf(
                                stringResource(R.string.settings_lang_system) to "system",
                                stringResource(R.string.settings_lang_zh) to "zh",
                                stringResource(R.string.settings_lang_en) to "en"
                            ),
                            selectedKey = currentLanguage,
                            onOptionSelected = { lang ->
                                currentLanguage = lang
                                SettingsManager.setLanguage(context, lang)
                            }
                        )
                    }
                }

                // 4. 隐私与安全卡片 (隐私模式开关)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_title_privacy),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isPrivacyMode,
                            onCheckedChange = { enabled ->
                                isPrivacyMode = enabled
                                SettingsManager.setPrivacyMode(context, enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentColor
                            )
                        )
                    }
                }

                // 5. 自定义 Tracker 映射卡片
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_title_custom_trackers),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor
                        )
                        IconButton(onClick = { showAddTrackerDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "编辑/添加",
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // 6. 底部居中版本号 (彩蛋：连点 5 次切换开发者模式，支持 GitHub Releases 在线更新提醒)
                val versionName = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "3.08"
                    } catch (_: Exception) {
                        "3.08"
                    }
                }

                var devModeTapCount by remember { mutableIntStateOf(0) }
                var lastTapTimeMs by remember { mutableLongStateOf(0L) }
                var updateInfoState by remember { mutableStateOf(UpdateCheckUtils.cachedUpdateInfo) }

                LaunchedEffect(Unit) {
                    if (updateInfoState == null) {
                        updateInfoState = UpdateCheckUtils.checkForUpdates(context)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TransSync v$versionName",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDeveloperMode) Color(0xFFFF5252) else secondaryTextColor,
                        modifier = Modifier.clickable {
                            val currentTime = System.currentTimeMillis()
                            if ((currentTime - lastTapTimeMs) < 600L) {
                                devModeTapCount++
                            } else {
                                devModeTapCount = 1
                            }
                            lastTapTimeMs = currentTime

                            if (devModeTapCount >= 5) {
                                devModeTapCount = 0
                                val newDevMode = !isDeveloperMode
                                isDeveloperMode = newDevMode
                                SettingsManager.setDeveloperMode(context, newDevMode)
                                if (newDevMode) {
                                    Toast.makeText(context, "已进入开发者模式", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "已退出开发者模式", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    if (updateInfoState?.hasUpdate == true) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = {
                                UpdateCheckUtils.openReleasesPage(context, updateInfoState?.releaseUrl ?: UpdateCheckUtils.GITHUB_RELEASES_URL)
                            },
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFFFF5252),
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "可更新 ${updateInfoState?.latestVersion}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 添加自定义 Tracker 映射弹窗
    if (showAddTrackerDialog) {
        AddCustomTrackerDialog(
            existingMappings = customTrackerMappings,
            backdropLayer = backdropLayer,
            boxPositionInRoot = settingsViewLocation,
            onSave = { updatedMap ->
                SettingsManager.saveCustomTrackerMappings(context, updatedMap)
                customTrackerMappings = SettingsManager.getCustomTrackerMappings(context)
                showAddTrackerDialog = false
            },
            onDismiss = { showAddTrackerDialog = false }
        )
    }

    // 删除服务器二次确认弹窗 (使用全统一液态玻璃弹窗)
    serverToDeleteTarget?.let { serverToDelete ->
        LiquidGlassDialog(
            onDismissRequest = { serverToDeleteTarget = null },
            backdropLayer = backdropLayer,
            boxPositionInRoot = settingsViewLocation,
            title = stringResource(R.string.dialog_delete_server_title),
            confirmButtonText = stringResource(R.string.btn_delete),
            confirmButtonColor = Color(0xFFFF5252),
            onConfirm = {
                ServerManager.deleteServer(context, serverToDelete.id)
                serversList = ServerManager.getServers(context)
                activeServer = ServerManager.getActiveServer(context)
                serverToDeleteTarget = null
                Toast.makeText(context, "已删除服务器配置", Toast.LENGTH_SHORT).show()
            },
        ) {
            Text(
                text = stringResource(R.string.dialog_delete_server_text, serverToDelete.alias.ifEmpty { "Transmission" }),
                fontSize = 15.sp,
                color = if (isDark) Color.White else Color(0xFF2D3436),
            )
        }
    }

    // 编辑已有服务器弹窗
    editingServerTarget?.let { server ->
        ServerEditDialog(
            initialServer = server,
            backdropLayer = backdropLayer,
            boxPositionInRoot = settingsViewLocation,
            onSave = { updated ->
                val wasActive = server.id == (activeServer?.id ?: "")
                val typeChanged = server.clientType != updated.clientType
                ServerManager.saveServer(context, updated)
                serversList = ServerManager.getServers(context)
                activeServer = ServerManager.getActiveServer(context)
                editingServerTarget = null

                if (wasActive && (typeChanged || (updated.clientType == ServerConfig.CLIENT_QBITTORRENT))) {
                    Toast.makeText(context, "服务器配置已更变，正在重启应用...", Toast.LENGTH_SHORT).show()
                    AppRestartUtils.restartApp(context)
                }
            },
            onDismiss = { editingServerTarget = null }
        )
    }

    // 添加新服务器弹窗
    if (showCreateServerDialog) {
        ServerEditDialog(
            initialServer = null,
            backdropLayer = backdropLayer,
            boxPositionInRoot = settingsViewLocation,
            onSave = { newServer ->
                ServerManager.saveServer(context, newServer)
                serversList = ServerManager.getServers(context)
                activeServer = ServerManager.getActiveServer(context)
                showCreateServerDialog = false
            },
            onDismiss = { showCreateServerDialog = false }
        )
    }
}

private fun formatServerUrl(inputUrl: String, clientType: String): String {
    var url = inputUrl.trim()
    if (url.isEmpty()) return ""
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        url = if (url.startsWith("192.168.") || url.startsWith("10.") || url.startsWith("172.") || url.startsWith("127.0.0.1") || url.startsWith("localhost")) {
            "http://$url"
        } else {
            "https://$url"
        }
    }
    return if (clientType == ServerConfig.CLIENT_TRANSMISSION) {
        if (url.endsWith("/transmission/rpc")) url else url.removeSuffix("/") + "/transmission/rpc"
    } else {
        url.removeSuffix("/")
    }
}

/**
 * 添加/编辑 Transmission / qBittorrent 服务器配置弹窗 (带连接测试功能)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServerEditDialog(
    initialServer: ServerConfig?,
    backdropLayer: GraphicsLayer? = null,
    boxPositionInRoot: Offset = Offset.Zero,
    onSave: (ServerConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    var clientTypeInput by remember { mutableStateOf(initialServer?.clientType ?: ServerConfig.CLIENT_TRANSMISSION) }
    var aliasInput by remember { mutableStateOf(initialServer?.alias ?: "家中NAS") }
    var rpcUrlInput by remember { mutableStateOf(initialServer?.rpcUrl ?: "") }
    var userInput by remember { mutableStateOf(initialServer?.user ?: "") }
    var passInput by remember { mutableStateOf(initialServer?.pass ?: "") }
    var avatarUriInput by remember { mutableStateOf(initialServer?.avatarUri ?: "") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                val inputStream = context.contentResolver.openInputStream(selectedUri)
                if (inputStream != null) {
                    val avatarsDir = java.io.File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
                    val destFile = java.io.File(avatarsDir, "avatar_${System.currentTimeMillis()}.png")
                    val outputStream = destFile.outputStream()
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    avatarUriInput = destFile.absolutePath
                    Toast.makeText(context, "图片图标设置成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "图片加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var isTestingConnection by remember { mutableStateOf(false) }

    val primaryTextColor = if (isDark) Color.White else Color(0xFF2D3436)
    val accentColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF)

    val dialogTitle = if (initialServer != null) stringResource(R.string.dialog_edit_server_title) else stringResource(R.string.dialog_add_server_title)

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        backdropLayer = backdropLayer,
        boxPositionInRoot = boxPositionInRoot,
        title = dialogTitle,
        confirmButtonText = stringResource(R.string.btn_save),
        confirmButtonColor = accentColor,
        onConfirm = {
            val rawUrl = rpcUrlInput.trim()
            val alias = aliasInput.trim().ifEmpty { if (clientTypeInput == ServerConfig.CLIENT_QBITTORRENT) "qBittorrent" else "Transmission" }
            if (rawUrl.isEmpty()) {
                Toast.makeText(context, "请输入服务器地址", Toast.LENGTH_SHORT).show()
                return@LiquidGlassDialog
            }
            val formattedUrl = formatServerUrl(rawUrl, clientTypeInput)

            val newConfig = ServerConfig(
                id = initialServer?.id ?: java.util.UUID.randomUUID().toString(),
                alias = alias,
                clientType = clientTypeInput,
                rpcUrl = formattedUrl,
                user = userInput.trim(),
                pass = passInput.trim(),
                isActive = initialServer?.isActive ?: true,
                avatarUri = avatarUriInput,
            )
            onSave(newConfig)
        },
        bottomLeftContent = {
            Button(
                onClick = {
                    val rawUrl = rpcUrlInput.trim()
                    val u = userInput.trim()
                    val p = passInput.trim()
                    if (rawUrl.isEmpty()) {
                        Toast.makeText(context, "请输入服务器地址", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val formattedUrl = formatServerUrl(rawUrl, clientTypeInput)
                    isTestingConnection = true

                    if (clientTypeInput == ServerConfig.CLIENT_QBITTORRENT) {
                        val qbitService = QBittorrentClient.getService(formattedUrl)
                        val performTransferCheck = {
                            qbitService.getTransferInfo().enqueue(object : Callback<QbitTransferInfo> {
                                override fun onResponse(call: Call<QbitTransferInfo>, response: Response<QbitTransferInfo>) {
                                    isTestingConnection = false
                                    if ((response.isSuccessful) || (response.code() == 200)) {
                                        Toast.makeText(context, "连接成功！qBittorrent Web API 握手正常", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "连接失败，HTTP 响应码: ${response.code()}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                override fun onFailure(call: Call<QbitTransferInfo>, t: Throwable) {
                                    isTestingConnection = false
                                    Toast.makeText(context, "连接失败: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            })
                        }

                        if (u.isNotEmpty() || p.isNotEmpty()) {
                            qbitService.login(u, p).enqueue(object : Callback<String> {
                                override fun onResponse(call: Call<String>, response: Response<String>) {
                                    if ((response.isSuccessful) || (response.code() == 200)) {
                                        performTransferCheck()
                                    } else {
                                        isTestingConnection = false
                                        Toast.makeText(context, "qBittorrent 登录失败 (HTTP ${response.code()})，请检查账号密码", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                override fun onFailure(call: Call<String>, t: Throwable) {
                                    isTestingConnection = false
                                    Toast.makeText(context, "连接失败: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            })
                        } else {
                            performTransferCheck()
                        }
                    } else {
                        val service = TransmissionClient.getService(formattedUrl, u, p)
                        service.rpc(formattedUrl, null, RpcRequest("session-get")).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                                isTestingConnection = false
                                if ((response.isSuccessful) || (response.code() == 409)) {
                                    Toast.makeText(context, "连接成功！Transmission 握手正常", Toast.LENGTH_SHORT).show()
                                } else if (response.code() == 401) {
                                    Toast.makeText(context, "连接失败：认证失败 (401)，请检查用户名和密码", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "连接失败，HTTP 响应码: ${response.code()}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                                isTestingConnection = false
                                Toast.makeText(context, "连接失败: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                },
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF131B24) else Color(0xFFF0F2F5)),
                modifier = Modifier.height(36.dp),
                enabled = !isTestingConnection,
            ) {
                Text(
                    text = if (isTestingConnection) stringResource(R.string.btn_testing) else stringResource(R.string.btn_test_connection),
                    fontSize = 12.5.sp,
                    color = primaryTextColor,
                )
            }
        },
    ) {
        Text(stringResource(R.string.label_client_type), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
        Spacer(modifier = Modifier.height(6.dp))

                CompactSegmentedGroup(
                    options = listOf(
                        "Transmission" to ServerConfig.CLIENT_TRANSMISSION,
                        "qBittorrent" to ServerConfig.CLIENT_QBITTORRENT
                    ),
                    selectedKey = clientTypeInput,
                    onOptionSelected = { clientTypeInput = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 左侧：缩短宽度的【备注】输入框
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_alias)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                            focusedTextColor = primaryTextColor,
                            unfocusedTextColor = primaryTextColor
                        )
                    )

                    // 右侧：圆形照片图标 (未选中显示照片图标，选中后自动变成所选图片)
                    val avatarBitmap = remember(avatarUriInput) {
                        if (avatarUriInput.isNotBlank()) {
                            try {
                                val file = java.io.File(avatarUriInput)
                                if (file.exists()) {
                                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                                } else null
                            } catch (_: Exception) { null }
                        } else null
                    }

                    val formattedCheckUrl = formatServerUrl(rpcUrlInput, clientTypeInput).trim().lowercase()
                    var isSelfSignedEdit by remember(formattedCheckUrl) { mutableStateOf(false) }

                    LaunchedEffect(formattedCheckUrl) {
                        if (formattedCheckUrl.startsWith("https://")) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                isSelfSignedEdit = SslCheckUtils.isSelfSignedSsl(formattedCheckUrl)
                            }
                        }
                    }

                    val editBadgeColor = when {
                        formattedCheckUrl.startsWith("http://") -> Color(0xFFFF5252)
                        formattedCheckUrl.startsWith("https://") && isSelfSignedEdit -> Color(0xFFFFC107)
                        else -> null
                    }

                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF263445) else Color(0xFFF0F2F5),
                            border = BorderStroke(1.5.dp, accentColor),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = { avatarPickerLauncher.launch("image/*") },
                                    onLongClick = {
                                        if (avatarUriInput.isNotBlank()) {
                                            avatarUriInput = ""
                                            Toast.makeText(context, "已清除所选图片", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarBitmap != null) {
                                    Image(
                                        bitmap = avatarBitmap,
                                        contentDescription = "服务器图标",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_image),
                                        contentDescription = "选择照片",
                                        tint = accentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        if (editBadgeColor != null) {
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
                                    tint = editBadgeColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = rpcUrlInput,
                    onValueChange = { rpcUrlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_address)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                        focusedTextColor = primaryTextColor,
                        unfocusedTextColor = primaryTextColor
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_username)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                        focusedTextColor = primaryTextColor,
                        unfocusedTextColor = primaryTextColor
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = passInput,
                    onValueChange = { passInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_password)) },
                    visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                painter = painterResource(id = if (isPasswordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off),
                                contentDescription = "切换密码显示",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                        focusedTextColor = primaryTextColor,
                        unfocusedTextColor = primaryTextColor,
                    )
                )
    }
}

private fun backupTrackersToDownloads(context: Context, textContent: String): String? {
    val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    val fileName = "tracker-$dateStr.ini"
    val iniContent = buildString {
        appendLine("[Trackers]")
        appendLine(textContent)
    }

    try {
        // 1. 优先 File API 写入 Download 文件夹，精确保留 .ini 扩展名，绝不被系统追加 .txt
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val targetFile = java.io.File(downloadsDir, fileName)
        targetFile.writeText(iniContent, Charsets.UTF_8)
        if (targetFile.exists()) {
            return "Download/$fileName"
        }
    } catch (_: Exception) {}

    try {
        // 2. MediaStore 备用方案：MIME_TYPE 指定为 application/octet-stream，防止系统 MediaProvider 自动追加 .txt
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(iniContent.toByteArray(Charsets.UTF_8))
                }
                return "Download/$fileName"
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

/**
 * 添加/批量编辑自定义 Tracker 映射大弹窗 (支持备份与恢复功能，输入格式: www.google.com=google, 每行一个)
 */
@Composable
fun AddCustomTrackerDialog(
    existingMappings: Map<String, String>,
    backdropLayer: GraphicsLayer? = null,
    boxPositionInRoot: Offset = Offset.Zero,
    onSave: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val initialText = remember(existingMappings) {
        existingMappings.entries.joinToString("\n") { "${it.key}=${it.value}" }
    }
    var inputText by remember(initialText) { mutableStateOf(initialText) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                // 严格校验恢复文件扩展名，必须为 .ini 格式，拒绝 .txt 或其他格式
                var fileName = ""
                context.contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIdx) ?: ""
                    }
                }
                if (fileName.isEmpty()) {
                    fileName = selectedUri.lastPathSegment ?: ""
                }

                if (!fileName.lowercase().endsWith(".ini")) {
                    Toast.makeText(context, "只能恢复 .ini 格式的 Tracker 备份文件", Toast.LENGTH_SHORT).show()
                    return@rememberLauncherForActivityResult
                }

                val inputStream = context.contentResolver.openInputStream(selectedUri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

                val existingLines = inputText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
                val existingDomains = existingLines.asSequence().map { it.substringBefore("=").trim().lowercase() }.toSet()

                val newLines = mutableListOf<String>()
                var addedCount = 0

                val iniLines = content.split("\n")
                for (line in iniLines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("[") || trimmed.startsWith("#") || trimmed.isBlank()) continue
                    if (trimmed.contains("=")) {
                        val domain = trimmed.substringBefore("=").trim()
                        val label = trimmed.substringAfter("=").trim()
                        if (domain.isNotEmpty() && label.isNotEmpty()) {
                            if (!existingDomains.contains(domain.lowercase())) {
                                newLines.add("$domain=$label")
                                addedCount++
                            }
                        }
                    }
                }

                if (addedCount > 0) {
                    val combinedText = buildString {
                        if (inputText.isNotBlank()) {
                            append(inputText.trim())
                            append("\n")
                        }
                        append(newLines.joinToString("\n"))
                    }
                    inputText = combinedText
                    Toast.makeText(context, "增量恢复成功，已新增 $addedCount 条映射", Toast.LENGTH_SHORT).show()
                } else if (existingLines.isNotEmpty()) {
                    Toast.makeText(context, "备份文件中的映射已存在，无需重复恢复", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "未能在 ini 文件中找到有效的 Tracker 映射", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "读取 ini 文件失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val primaryTextColor = if (isDark) Color.White else Color(0xFF2D3436)
    val secondaryTextColor = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72)
    val accentColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF)

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        backdropLayer = backdropLayer,
        boxPositionInRoot = boxPositionInRoot,
        title = "自定义 Tracker 映射",
        confirmButtonText = stringResource(R.string.btn_save),
        confirmButtonColor = accentColor,
        onConfirm = {
            val updatedMap = mutableMapOf<String, String>()
            val lines = inputText.split("\n")
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) continue
                val parts = trimmed.split("=")
                if (parts.size >= 2) {
                    val key = parts[0].trim().lowercase()
                    val value = parts[1].trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        updatedMap[key] = value
                    }
                }
            }
            onSave(updatedMap)
            Toast.makeText(context, "Tracker 映射保存成功", Toast.LENGTH_SHORT).show()
        },
        bottomLeftContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val savedPath = backupTrackersToDownloads(context, inputText)
                        if (savedPath != null) {
                            Toast.makeText(context, "已备份至: $savedPath", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "备份失败", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text(stringResource(R.string.btn_backup), fontSize = 12.5.sp, color = primaryTextColor)
                }

                OutlinedButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text(stringResource(R.string.btn_restore), fontSize = 12.5.sp, color = primaryTextColor)
                }
            }
        },
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 280.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = false,
            placeholder = {
                Text(
                    text = stringResource(R.string.tracker_dialog_placeholder),
                    fontSize = 13.sp,
                    color = secondaryTextColor.copy(alpha = 0.6f),
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                focusedLabelColor = accentColor,
                unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                focusedTextColor = primaryTextColor,
                unfocusedTextColor = primaryTextColor,
            ),
        )
    }
}

/**
 * 紧凑型横排分段切换按钮组
 */
@Composable
private fun CompactSegmentedGroup(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onOptionSelected: (String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val groupBgColor = if (isDark) Color(0xFF131B24) else Color(0xFFF0F2F5)
    val activeBgColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF)
    val textColor = if (isDark) Color.White else Color(0xFF2D3436)

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = groupBgColor,
        border = BorderStroke(1.dp, if (isDark) Color(0x22FFFFFF) else Color(0xFFE0E0E0)),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { (label, valueKey) ->
                val isSelected = selectedKey == valueKey

                Surface(
                    onClick = { onOptionSelected(valueKey) },
                    shape = RoundedCornerShape(100.dp),
                    color = if (isSelected) activeBgColor else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else textColor
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "设置页 - 浅色模式", showBackground = true)
@Composable
fun SettingsScreen_Light_Preview() {
    MaterialTheme {
        SettingsScreen(onBackClick = {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "添加/编辑服务器弹窗", showBackground = true)
@Composable
fun ServerEditDialog_Preview() {
    MaterialTheme {
        ServerEditDialog(
            initialServer = ServerConfig(alias = "家中 NAS", rpcUrl = "https://192.168.1.100:9091/transmission/rpc", user = "admin", pass = "password"),
            onSave = {},
            onDismiss = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "自定义 Tracker 映射弹窗", showBackground = true)
@Composable
fun AddCustomTrackerDialog_Preview() {
    MaterialTheme {
        AddCustomTrackerDialog(
            existingMappings = mapOf("www.google.com" to "Google"),
            onSave = {},
            onDismiss = {},
        )
    }
}
