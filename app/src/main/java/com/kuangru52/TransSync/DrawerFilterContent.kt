package com.kuangru52.transsync

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.MaterialTheme
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
import android.os.Build
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 导航抽屉侧边栏内容组件 (DrawerFilterContent.kt)
 *
 * 【作用与功能】：
 * 侧边栏抽屉的核心 UI 界面，包含以下控件与交互功能：
 * 1. 顶部服务器图标切换行：展示多服务器头像/简写圆按键与连接安全角标，支持一键点击无缝切换当前活动服务器；
 * 2. 状态分类过滤列表：展示全部任务、正在下载、做种中、已暂停、活动中、未活动、错误等分类项及对应任务数与存储占用；
 * 3. Tracker 标签芯片 FlowRow 区域：展示 Tracker 聚合分类与节点数，支持点击过滤与模糊隐藏保护隐私；
 * 4. 底部状态栏：展示可用存储空间及设置入口按键。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun DrawerFilterContent(
    viewModel: TorrentListViewModel? = null,
    currentFilter: String = "All",
    rpcUrl: String = "",
    onSelectFilter: (String) -> Unit = {},
    onServerSwitched: ((ServerConfig) -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val isInspection = LocalInspectionMode.current

    val sampleDrawerData = remember {
        mapOf(
            "All" to DrawerItemData(12, 78174110000000L),
            "Downloading" to DrawerItemData(3, 47460000000L),
            "Seeding" to DrawerItemData(8, 78100000000000L),
            "Paused" to DrawerItemData(1, 6980000000L),
            "Active" to DrawerItemData(4, 53700000000L),
            "Inactive" to DrawerItemData(8, 78100000000000L),
            "Error" to DrawerItemData(0, 0L),
        )
    }

    val sampleTrackerData = remember {
        mapOf(
            "Google" to 5,
        )
    }

    val drawerData = if (viewModel != null && !isInspection) {
        viewModel.drawerData.observeAsState(emptyMap()).value
    } else {
        sampleDrawerData
    }

    val trackerData = if (viewModel != null && !isInspection) {
        viewModel.trackerData.observeAsState(emptyMap()).value
    } else {
        sampleTrackerData
    }

    val freeSpace = if (viewModel != null && !isInspection) {
        viewModel.freeSpace.observeAsState("--").value
    } else {
        "12.4 TB"
    }

    val isTrackerBlurEnabled = if (viewModel != null && !isInspection) {
        viewModel.isTrackerBlurEnabled.observeAsState(initial = false).value
    } else {
        false
    }

    val revealedTrackerNames = if (viewModel != null && !isInspection) {
        viewModel.revealedTrackerNames.observeAsState(emptySet()).value
    } else {
        emptySet()
    }

    val categories = listOf(
        "All" to stringResource(R.string.nav_all),
        "Downloading" to stringResource(R.string.nav_downloading),
        "Seeding" to stringResource(R.string.nav_seeding),
        "Paused" to stringResource(R.string.nav_paused),
        "Active" to stringResource(R.string.nav_active),
        "Inactive" to stringResource(R.string.nav_inactive),
        "Error" to stringResource(R.string.nav_error),
    )

    val unselectedTextColor = if (isDark) Color(0xDDFFFFFF) else Color(0xFF2D3436)
    val selectedTextColor = if (isDark) Color.White else Color(0xFF1D88E3)
    val selectedSurfaceColor = if (isDark) Color(0x44FFFFFF) else Color(0x22000000)
    val selectedBorderColor = if (isDark) Color(0xB3FFFFFF) else Color(0x55000000)
    val chipBgColor = if (isDark) Color(0x1AFFFFFF) else Color(0x1A000000)
    val chipSelectedColor = if (isDark) Color(0x55FFFFFF) else Color(0x33000000)
    val chipTextColor = if (isDark) Color(0xDDFFFFFF) else Color(0xFF2D3436)
    val chipSelectedTextColor = if (isDark) Color.White else Color(0xFF1D88E3)
    val chipBorderColor = if (isDark) Color(0x33FFFFFF) else Color(0x33000000)
    val chipSelectedBorderColor = if (isDark) Color(0xEEFFFFFF) else Color(0xAA000000)
    val dividerGlowColor = if (isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.15f)

    val density = LocalDensity.current
    val speedbarVersion by SettingsManager.speedbarGlassParamsVersion.collectAsState()
    val speedbarParams = remember(isDark, speedbarVersion) { SettingsManager.getSpeedbarGlassParams(context, isDark) }

    val cachedShader = remember {
        if (!isInspection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader(LIQUID_GLASS_AGSL)
            } catch (_: Exception) { null }
        } else null
    }

    val hasGlassEffect = (speedbarParams.refraction != 0f || speedbarParams.blurRadius > 0f || speedbarParams.whitePoint > 0f)

    Column(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(Color.Transparent)
    ) {
        // --- 1. Header (交互式圆形服务器备注切换按钮组) ---
        val serversVersion = ServerManager.serversVersion
        val realServersList = remember(serversVersion) { ServerManager.getServers(context) }
        val serversList = if (isInspection || realServersList.isEmpty()) {
            listOf(
                ServerConfig(id = "1", alias = "Transmission", rpcUrl = "https://192.168.1.100:9091/transmission/rpc"),
                ServerConfig(id = "2", alias = "qBittorrent", rpcUrl = "http://192.168.1.101:8080"),
            )
        } else {
            realServersList
        }

        val activeServer = remember(serversVersion) { ServerManager.getActiveServer(context) }
        var activeServerId by remember(serversVersion) { mutableStateOf(activeServer?.id ?: "1") }
        val accentColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
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
                    if (urlLower.startsWith("https://") && !isInspection) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            isSelfSigned = SslCheckUtils.isSelfSignedSsl(server.rpcUrl)
                        }
                    }
                }

                val badgeColor = when {
                    urlLower.startsWith("http://") -> Color(0xFFFF5252)
                    urlLower.startsWith("https://") && isSelfSigned -> Color(0xFFFFC107)
                    else -> null
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
                                Toast.makeText(context, "切换中…", Toast.LENGTH_SHORT).show()
                                if (!isInspection) {
                                    ServerManager.setActiveServer(context, server.id)
                                    AppRestartUtils.restartApp(context)
                                }
                                activeServerId = server.id
                                onServerSwitched?.invoke(server)
                            } else {
                                if (onOpenSettings != null) {
                                    onOpenSettings.invoke()
                                } else if (!isInspection) {
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
                    shape = RoundedCornerShape(100.dp),
                    color = if (isSelected) selectedSurfaceColor else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, selectedBorderColor) else null,
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
                                color = if (isSelected) selectedTextColor else unselectedTextColor
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "[$sizeText]",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = if (isSelected) selectedTextColor.copy(alpha = 0.9f) else unselectedTextColor.copy(alpha = 0.8f)
                            )
                        )
                    }
                }
            }
        }

        val trackerScrollState = rememberScrollState()
        var previousScrollOffset by remember { mutableIntStateOf(0) }
        var isPullingDown by remember { mutableStateOf(false) }
        var isPullingUp by remember { mutableStateOf(false) }

        LaunchedEffect(trackerScrollState.value, trackerScrollState.isScrollInProgress) {
            if (trackerScrollState.isScrollInProgress) {
                val diff = trackerScrollState.value - previousScrollOffset
                if (diff < 0) {
                    isPullingDown = true
                    isPullingUp = false
                } else if (diff > 0) {
                    isPullingDown = false
                    isPullingUp = true
                }
                previousScrollOffset = trackerScrollState.value
            } else {
                isPullingDown = false
                isPullingUp = false
            }
        }

        val topDividerAlpha by animateFloatAsState(
            targetValue = if (trackerScrollState.value > 0) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
            label = "topDividerAlpha"
        )

        val bottomDividerAlpha by animateFloatAsState(
            targetValue = if (trackerScrollState.maxValue > 0 && trackerScrollState.value < trackerScrollState.maxValue) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
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
                                    viewModel?.revealTracker(trackerName)
                                } else {
                                    onSelectFilter("tracker:$trackerName")
                                }
                            },
                            shape = RoundedCornerShape(100.dp),
                            color = if (hasGlassEffect) Color.Transparent else (if (isChipSelected) chipSelectedColor else chipBgColor),
                            border = BorderStroke(1.dp, if (isChipSelected) chipSelectedBorderColor else chipBorderColor),
                            shadowElevation = if (hasGlassEffect) 4.dp else 0.dp,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(100.dp))
                            ) {
                                if (hasGlassEffect) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(100.dp))
                                            .graphicsLayer {
                                                clip = true
                                                shape = RoundedCornerShape(100.dp)
                                                if (!isInspection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                    if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (cachedShader != null)) {
                                                        try {
                                                            cachedShader.setFloatUniform("size", size.width, size.height)
                                                            cachedShader.setFloatUniform("cornerRadius", size.height * 0.5f)
                                                            cachedShader.setFloatUniform("refraction", with(density) { speedbarParams.refraction.dp.toPx() })
                                                            cachedShader.setFloatUniform("refractionHeight", with(density) { speedbarParams.refractionHeight.dp.toPx() })
                                                            cachedShader.setFloatUniform("saturationBoost", speedbarParams.saturationBoost)
                                                            cachedShader.setFloatUniform("contrast", speedbarParams.contrast)
                                                            cachedShader.setFloatUniform("whitePoint", speedbarParams.whitePoint)

                                                            val runtimeShaderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(cachedShader, "content")
                                                            renderEffect = if (speedbarParams.blurRadius > 0f) {
                                                                val blurPx = with(density) { speedbarParams.blurRadius.dp.toPx() }
                                                                val blurEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP)
                                                                android.graphics.RenderEffect.createChainEffect(runtimeShaderEffect, blurEffect).asComposeRenderEffect()
                                                            } else {
                                                                runtimeShaderEffect.asComposeRenderEffect()
                                                            }
                                                        } catch (_: Exception) {
                                                            if (speedbarParams.blurRadius > 0f) {
                                                                val blurPx = with(density) { speedbarParams.blurRadius.dp.toPx() }
                                                                renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                                                            }
                                                        }
                                                    } else if (speedbarParams.blurRadius > 0f) {
                                                        val blurPx = with(density) { speedbarParams.blurRadius.dp.toPx() }
                                                        renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                                                    }
                                                }
                                            }
                                            .drawBehind {
                                                drawRect(color = if (isChipSelected) chipSelectedColor else chipBgColor)
                                                if (speedbarParams.whitePoint > 0f) {
                                                    drawRect(color = Color.White.copy(alpha = (speedbarParams.whitePoint * 0.3f).coerceIn(0f, 0.4f)))
                                                }
                                            }
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .then(if (shouldBlur) Modifier.blur(8.dp) else Modifier)
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$trackerName  $count",
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            lineHeight = 12.sp,
                                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                                            color = if (isChipSelected) chipSelectedTextColor else chipTextColor,
                                        )
                                    )
                                }
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
                    } else if (!isInspection) {
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

@androidx.compose.ui.tooling.preview.Preview(name = "侧边栏 - 浅色模式", showBackground = true)
@Composable
fun DrawerFilterContent_Light_Preview() {
    MaterialTheme {
        Surface(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight(),
            color = Color(0xFFF0F2F5)
        ) {
            DrawerFilterContent(
                viewModel = null,
                currentFilter = "All",
                rpcUrl = "https://192.168.1.100:9091/transmission/rpc",
                onSelectFilter = {}
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "侧边栏 - 深色模式", showBackground = true)
@Composable
fun DrawerFilterContent_Dark_Preview() {
    MaterialTheme {
        Surface(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight(),
            color = Color(0xFF161F29)
        ) {
            DrawerFilterContent(
                viewModel = null,
                currentFilter = "Downloading",
                rpcUrl = "https://192.168.1.100:9091/transmission/rpc",
                onSelectFilter = {}
            )
        }
    }
}
