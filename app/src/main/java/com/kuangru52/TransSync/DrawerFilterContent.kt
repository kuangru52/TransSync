package com.kuangru52.transsync

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 1:1 绝对复刻 activity_torrent_list.xml 与 nav_header.xml (含渐变灵动发光分割线 divider_horizontal_glow)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun DrawerFilterContent(
    viewModel: TorrentListViewModel,
    currentFilter: String,
    rpcUrl: String,
    onSelectFilter: (String) -> Unit,
    onServerSwitched: ((ServerConfig) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val drawerData by viewModel.drawerData.observeAsState(emptyMap())
    val trackerData by viewModel.trackerData.observeAsState(emptyMap())
    val freeSpace by viewModel.freeSpace.observeAsState("--")
    val isTrackerBlurEnabled by viewModel.isTrackerBlurEnabled.observeAsState(initial = false)
    val revealedTrackerNames by viewModel.revealedTrackerNames.observeAsState(emptySet())

    val categories = listOf(
        "All" to stringResource(R.string.nav_all),
        "Downloading" to stringResource(R.string.nav_downloading),
        "Seeding" to stringResource(R.string.nav_seeding),
        "Paused" to stringResource(R.string.nav_paused),
        "Active" to stringResource(R.string.nav_active),
        "Inactive" to stringResource(R.string.nav_inactive),
        "Error" to stringResource(R.string.nav_error),
    )

    val dividerGlowColor = if (isDark) Color(0x40FFFFFF) else Color(0x33000000)

    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(if (isDark) Color(0xFF161F29) else Color(0xFFF0F2F5))
    ) {
        // --- 1. Header (交互式圆形服务器备注切换按钮组) ---
        val serversVersion = ServerManager.serversVersion
        val serversList = remember(serversVersion) { ServerManager.getServers(context) }
        val activeServer = remember(serversVersion) { ServerManager.getActiveServer(context) }
        var activeServerId by remember(serversVersion) { mutableStateOf(activeServer?.id) }
        val accentColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF)
        val headerBgColor = if (isDark) Color(0xFF161F29) else Color(0xFF455A64)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBgColor)
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            serversList.forEach { server ->
                val isActive = server.id == activeServerId
                val initialChar = server.alias.trim().take(1).ifEmpty { "S" }
                val urlLower = server.rpcUrl.trim().lowercase()

                var isSelfSigned by remember(server.rpcUrl) { mutableStateOf(value = false) }

                LaunchedEffect(server.rpcUrl) {
                    if (urlLower.startsWith("https://")) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            isSelfSigned = SslCheckUtils.isSelfSignedSsl(server.rpcUrl)
                        }
                    }
                }

                val badgeColor = when {
                    urlLower.startsWith("http://") -> Color(0xFFFF5252) // 红色圆叹号：HTTP 无证书明文连接
                    urlLower.startsWith("https://") && isSelfSigned -> Color(0xFFFFC107) // 黄色圆叹号：mkcert / 自签名证书
                    else -> null // Let's Encrypt 等权威 CA -> 完全受信任，不显示警告！
                }

                val avatarBitmap = remember(server.avatarUri) {
                    if (server.avatarUri.isNotBlank()) {
                        try {
                            val file = java.io.File(server.avatarUri)
                            if (file.exists()) {
                                android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                            } else null
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    } else null
                }

                Box(
                    modifier = Modifier.size(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        onClick = {
                            if (!isActive) {
                                val oldActiveServer = serversList.find { it.id == activeServerId }
                                ServerManager.setActiveServer(context, server.id)
                                activeServerId = server.id

                                val isCrossClientSwitch = (oldActiveServer?.clientType != server.clientType) ||
                                        (server.clientType == ServerConfig.CLIENT_QBITTORRENT)

                                if (isCrossClientSwitch) {
                                    Toast.makeText(context, "正在无缝重启应用以生效 ${server.alias}...", Toast.LENGTH_SHORT).show()
                                    AppRestartUtils.restartApp(context)
                                } else {
                                    Toast.makeText(context, "已切换至: ${server.alias}", Toast.LENGTH_SHORT).show()
                                    viewModel.switchServer(server)
                                    onServerSwitched?.invoke(server)
                                }
                            } else {
                                if (onOpenSettings != null) {
                                    onOpenSettings.invoke()
                                } else {
                                    context.startActivity(Intent(context, SettingsActivity::class.java))
                                }
                            }
                        },
                        shape = CircleShape,
                        color = if (isActive) accentColor else Color(0x33FFFFFF),
                        border = BorderStroke(
                            width = if (isActive) 2.dp else 1.dp,
                            color = if (isActive) Color.White else Color(0x66FFFFFF)
                        ),
                        shadowElevation = if (isActive) 8.dp else 2.dp,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap,
                                    contentDescription = server.alias,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Text(
                                    text = initialChar,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // 侧边栏右上角圆形叹号 Badge (HTTP 显示红色, HTTPS 自签名显示黄色)
                    if (badgeColor != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF161F29) else Color(0xFF455A64))
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
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // --- 2. Category Filter List ---
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            categories.forEach { (key, title) ->
                val isSelected = currentFilter == key
                val itemData = drawerData[key] ?: DrawerItemData(0, 0L)
                val countText = itemData.count.toString()
                val sizeText = FormatUtils.formatSize(itemData.totalSize)

                Surface(
                    onClick = { onSelectFilter(key) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSelected) (if (isDark) Color(0xFF212D3B) else Color(0x22455A64)) else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$title ($countText)",
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isDark) Color.White else Color(0xFF2D3436)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "[$sizeText]",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = if (isDark) Color(0x88FFFFFF) else Color(0xFF636E72)
                            )
                        )
                    }
                }
            }
        }

        // 每次下拉或上推均稳定触发分割线显示（含边界拉动提示）
        val trackerScrollState = rememberScrollState()
        var previousScrollOffset by remember { mutableIntStateOf(0) }
        var topDividerVisible by remember { mutableStateOf(false) }
        var bottomDividerVisible by remember { mutableStateOf(false) }

        LaunchedEffect(trackerScrollState.value, trackerScrollState.isScrollInProgress) {
            if (trackerScrollState.isScrollInProgress) {
                val diff = trackerScrollState.value - previousScrollOffset
                if (diff < 0) {
                    topDividerVisible = true
                    bottomDividerVisible = false
                } else if (diff > 0) {
                    topDividerVisible = false
                    bottomDividerVisible = true
                } else {
                    if (trackerScrollState.value == 0) {
                        topDividerVisible = true
                        bottomDividerVisible = false
                    } else if (trackerScrollState.value == trackerScrollState.maxValue) {
                        topDividerVisible = false
                        bottomDividerVisible = true
                    }
                }
                previousScrollOffset = trackerScrollState.value
            } else {
                topDividerVisible = false
                bottomDividerVisible = false
            }
        }

        val topDividerAlpha by animateFloatAsState(
            targetValue = if (topDividerVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "topDividerAlpha"
        )

        val bottomDividerAlpha by animateFloatAsState(
            targetValue = if (bottomDividerVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "bottomDividerAlpha"
        )

        // --- 3. Top Divider ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .graphicsLayer { alpha = topDividerAlpha }
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            dividerGlowColor,
                            Color.Transparent
                        )
                    )
                )
        )

        // --- 4. Tracker Chips Section ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(trackerScrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (trackerData.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trackerData.entries.sortedByDescending { it.value }.forEach { (trackerName, count) ->
                        val isChipSelected = currentFilter == "tracker:$trackerName"
                        val isRevealed = revealedTrackerNames.contains(trackerName)
                        val shouldBlur = isTrackerBlurEnabled && !isRevealed

                        Surface(
                            onClick = {
                                if (shouldBlur) {
                                    viewModel.revealTracker(trackerName)
                                } else {
                                    onSelectFilter("tracker:$trackerName")
                                }
                            },
                            shape = RoundedCornerShape(100.dp),
                            color = if (isChipSelected) (if (isDark) Color(0xFF2196F3) else Color(0xFF455A64)) else (if (isDark) Color(0xFF212D3B) else Color(0xFFF0F2F5)),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF34495E) else Color(0xFFE0E0E0)),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(100.dp))
                                    .then(if (shouldBlur) Modifier.blur(8.dp) else Modifier)
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$trackerName  $count",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        lineHeight = 12.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        color = if (isChipSelected) Color.White else (if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72))
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 5. Bottom Divider ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .graphicsLayer { alpha = bottomDividerAlpha }
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            dividerGlowColor,
                            Color.Transparent
                        )
                    )
                )
        )

        // --- 6. Drawer Footer ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.free_space_label, freeSpace),
                fontSize = 14.sp,
                color = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72),
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            )

            IconButton(
                onClick = {
                    if (onOpenSettings != null) {
                        onOpenSettings.invoke()
                    } else {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_outlined),
                    contentDescription = "设置",
                    tint = if (isDark) Color.White else Color(0xFF2D3436)
                )
            }
        }
    }
}
