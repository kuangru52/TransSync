package com.kuangru52.transsync

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ============================================================================
 * 全应用统一顶部悬浮控制栏组件库 (TorrentTopAppBar.kt)
 * - 严密的 3 层物理图层隔离架构：
 *   1. 底层 (Bottom Layer): 3D AGSL 凸透镜折射 Shader 与毛玻璃采样层 (只模糊/折射背景)
 *   2. 中层 (Middle Layer): Surface 容器承载轮廓边框与投影阴影
 *   3. 顶层 (Top Layer): 100% 矢量原生清晰的前景文本与图标 (绝对清晰、零模糊)
 * ============================================================================
 */

@Composable
fun TopBarGlassSurface(
    shape: androidx.compose.ui.graphics.Shape,
    border: BorderStroke?,
    modifier: Modifier = Modifier,
    shadowElevation: Dp = 8.dp,
    backdropLayer: GraphicsLayer? = null,
    cornerRadius: Dp = 100.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val isInspection = LocalInspectionMode.current

    val topBarVersion by SettingsManager.topBarGlassParamsVersion.collectAsState()
    val glassParams = remember(isDark, topBarVersion) { SettingsManager.getTopBarGlassParams(context, isDark) }

    val barBgColor = if (isDark) Color(0x99141D26) else Color(0xA6FFFFFF)

    var posInRoot by remember { mutableStateOf(Offset.Zero) }

    val cachedShader = remember {
        if (!isInspection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader(LIQUID_GLASS_AGSL)
            } catch (_: Exception) { null }
        } else null
    }

    val hasGlassEffect = (glassParams.refraction != 0f || glassParams.blurRadius > 0f || glassParams.whitePoint > 0f)

    // Layer 2 (Middle): Surface Container
    Surface(
        shape = shape,
        color = Color.Transparent,
        border = if (hasGlassEffect) border else null,
        shadowElevation = if (hasGlassEffect) shadowElevation else 0.dp,
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                posInRoot = coordinates.positionInRoot()
            }
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        Box(
            modifier = Modifier.wrapContentSize(),
            contentAlignment = Alignment.Center
        ) {
            // Layer 1 (Bottom): Background Shader & Frosted Glass Tint (Only affects background, NEVER touches foreground content)
            if (hasGlassEffect) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape)
                        .graphicsLayer {
                            clip = true
                            this.shape = shape
                            if (!isInspection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (cachedShader != null)) {
                                    try {
                                        val shader = cachedShader
                                        shader.setFloatUniform("size", size.width, size.height)
                                        val radiusPx = if (shape == CircleShape) {
                                            (minOf(size.width, size.height) / 2f)
                                        } else {
                                            with(density) { cornerRadius.toPx() }
                                        }
                                        shader.setFloatUniform("cornerRadius", radiusPx)
                                        shader.setFloatUniform("refraction", with(density) { glassParams.refraction.dp.toPx() })
                                        shader.setFloatUniform("refractionHeight", with(density) { glassParams.refractionHeight.dp.toPx() })
                                        shader.setFloatUniform("saturationBoost", glassParams.saturationBoost)
                                        shader.setFloatUniform("contrast", glassParams.contrast)
                                        shader.setFloatUniform("whitePoint", glassParams.whitePoint)

                                        val runtimeEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                                        val blurPx = with(density) { glassParams.blurRadius.dp.toPx() }
                                        val blurEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP)
                                        renderEffect = android.graphics.RenderEffect.createChainEffect(runtimeEffect, blurEffect).asComposeRenderEffect()
                                    } catch (_: Exception) {
                                        val blurPx = with(density) { glassParams.blurRadius.dp.toPx() }
                                        renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                                    }
                                } else {
                                    val blurPx = with(density) { glassParams.blurRadius.dp.toPx() }
                                    renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                                }
                            }
                        }
                        .drawBehind {
                            if (backdropLayer != null) {
                                try {
                                    translate(left = -posInRoot.x, top = -posInRoot.y) {
                                        drawLayer(backdropLayer)
                                    }
                                } catch (_: Exception) {}
                            }
                            drawRect(color = barBgColor)
                            if (glassParams.whitePoint > 0f) {
                                drawRect(color = Color.White.copy(alpha = (glassParams.whitePoint * 0.3f).coerceIn(0f, 0.4f)))
                            }
                        }
                )
            }

            // Layer 3 (Top): Crisp Foreground Content (100% Vector Sharp, High Contrast, Zero Blur)
            Box(
                modifier = Modifier.wrapContentSize(),
                contentAlignment = Alignment.Center,
                content = content
            )
        }
    }
}

/**
 * 1. 设置页面顶栏：[← 设置] (紧凑胶囊，左上角)
 */
@Composable
fun SettingsTopBar(
    onBackClick: () -> Unit,
    backSwipeRatio: Float = 0f,
    backdropLayer: GraphicsLayer? = null,
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
) {
    val barBorderColor = if (isDark) Color(0x3BFFFFFF) else Color(0x55E0E0E0)
    val textColor = if (isDark) Color.White else Color(0xFF2D3436)
    val arrowRotation = backSwipeRatio * 180f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        TopBarGlassSurface(
            shape = RoundedCornerShape(100.dp),
            border = BorderStroke(1.dp, barBorderColor),
            backdropLayer = backdropLayer,
            onClick = onBackClick,
            modifier = Modifier
                .wrapContentWidth()
                .height(44.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.nav_settings),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
            }
        }
    }
}

/**
 * 2. 主页悬浮控制条：常规模式与多选模式
 */
@Composable
fun FloatingTopControls(
    titleText: String,
    sizeText: String,
    altSpeedEnabled: Boolean,
    selectedCount: Int,
    drawerSlideRatio: Float = 0f,
    backdropLayer: GraphicsLayer? = null,
    boxPositionInRoot: Offset = Offset.Zero,
    onMenuClick: () -> Unit,
    onTurtleClick: () -> Unit,
    onCloseSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onStartSelected: () -> Unit,
    onStopSelected: () -> Unit,
    onRenameSelected: () -> Unit,
    onSetLocationSelected: () -> Unit,
    onSetHrSelected: () -> Unit,
    onVerifySelected: () -> Unit,
    onReannounceSelected: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    val barBorderColor = if (isDark) Color(0x3BFFFFFF) else Color(0x55E0E0E0)
    val textColor = if (isDark) Color.White else Color(0xFF2D3436)

    val menuRotation = drawerSlideRatio * 180f

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val isDeveloperMode = remember { SettingsManager.isDeveloperMode(context) }
    var showTuningInspector by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        if (selectedCount == 0) {
            TopBarGlassSurface(
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, barBorderColor),
                backdropLayer = backdropLayer,
                onClick = onMenuClick,
                modifier = Modifier
                    .wrapContentWidth()
                    .height(44.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_menu),
                        contentDescription = "打开菜单",
                        tint = textColor,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                rotationZ = menuRotation
                            },
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = titleText,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        ),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "($sizeText)",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72),
                        ),
                    )
                }
            }

            TopBarGlassSurface(
                shape = CircleShape,
                border = BorderStroke(1.dp, barBorderColor),
                backdropLayer = backdropLayer,
                onClick = onTurtleClick,
                modifier = Modifier
                    .size(44.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            if (isDeveloperMode && dragAmount > 10f) {
                                change.consume()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showTuningInspector = true
                            }
                        }
                    },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = if (altSpeedEnabled) R.drawable.ic_turtle else R.drawable.ic_turtle_outline),
                        contentDescription = "限速模式",
                        tint = if (altSpeedEnabled) Color(0xFFF9A825) else textColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        } else {
            TopBarGlassSurface(
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, barBorderColor),
                backdropLayer = backdropLayer,
                onClick = onCloseSelection,
                modifier = Modifier
                    .wrapContentWidth()
                    .height(44.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.selected_count, selectedCount),
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        ),
                    )
                }
            }

            MultiSelectRightCapsule(
                selectedCount = selectedCount,
                backdropLayer = backdropLayer,
                boxPositionInRoot = boxPositionInRoot,
                onSelectAll = onSelectAll,
                onDeleteSelected = onDeleteSelected,
                onStartSelected = onStartSelected,
                onStopSelected = onStopSelected,
                onRenameSelected = onRenameSelected,
                onSetLocationSelected = onSetLocationSelected,
                onSetHrSelected = onSetHrSelected,
                onVerifySelected = onVerifySelected,
                onReannounceSelected = onReannounceSelected,
                isDark = isDark,
            )
        }
    }

    if (showTuningInspector && isDeveloperMode) {
        val glassParams = SettingsManager.getTopBarGlassParams(context, isDark)
        var liveParams by remember(isDark, glassParams) { mutableStateOf(glassParams) }
        LiquidGlassTuningInspector(
            refractionDp = liveParams.refraction,
            refractionHeightDp = liveParams.refractionHeight,
            blurRadiusDp = liveParams.blurRadius,
            saturationBoost = liveParams.saturationBoost,
            contrast = liveParams.contrast,
            whitePoint = liveParams.whitePoint,
            onRefractionChange = { liveParams = liveParams.copy(refraction = it); SettingsManager.saveTopBarGlassParams(context, liveParams) },
            onRefractionHeightChange = { liveParams = liveParams.copy(refractionHeight = it); SettingsManager.saveTopBarGlassParams(context, liveParams) },
            onBlurRadiusChange = { liveParams = liveParams.copy(blurRadius = it); SettingsManager.saveTopBarGlassParams(context, liveParams) },
            onSaturationBoostChange = { liveParams = liveParams.copy(saturationBoost = it); SettingsManager.saveTopBarGlassParams(context, liveParams) },
            onContrastChange = { liveParams = liveParams.copy(contrast = it); SettingsManager.saveTopBarGlassParams(context, liveParams) },
            onWhitePointChange = { liveParams = liveParams.copy(whitePoint = it); SettingsManager.saveTopBarGlassParams(context, liveParams) },
            onReset = {
                val defParams = GlassParams(if (isDark) -25f else 25f, 12f, 20f, 1.5f, 0.15f, 0.10f)
                liveParams = defParams
                SettingsManager.saveTopBarGlassParams(context, defParams)
            },
            onSave = { showTuningInspector = false },
            onDismiss = { showTuningInspector = false }
        )
    }
}

/**
 * 主页多选模式右侧融合扩展悬浮胶囊卡片
 */
@Composable
fun MultiSelectRightCapsule(
    selectedCount: Int,
    backdropLayer: GraphicsLayer? = null,
    boxPositionInRoot: Offset = Offset.Zero,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onStartSelected: () -> Unit,
    onStopSelected: () -> Unit,
    onRenameSelected: () -> Unit,
    onSetLocationSelected: () -> Unit,
    onSetHrSelected: () -> Unit,
    onVerifySelected: () -> Unit,
    onReannounceSelected: () -> Unit,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    var isExpanded by remember { mutableStateOf(value = false) }

    val barBorderColor = if (isDark) Color(0x3BFFFFFF) else Color(0x55E0E0E0)
    val textColor = if (isDark) Color.White else Color(0xFF2D3436)

    BackHandler(enabled = isExpanded) {
        isExpanded = false
    }

    val cardCornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 18.dp else 100.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "cornerRadius",
    )

    val topRowSpacing by animateDpAsState(
        targetValue = if (isExpanded) 12.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "topRowSpacing",
    )

    val topRowPaddingHorizontal by animateDpAsState(
        targetValue = if (isExpanded) 12.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "topRowPaddingHorizontal",
    )

    TopBarGlassSurface(
        shape = RoundedCornerShape(cardCornerRadius),
        border = BorderStroke(1.dp, barBorderColor),
        backdropLayer = backdropLayer,
        cornerRadius = cardCornerRadius,
        modifier = Modifier.wrapContentSize(),
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .animateContentSize(animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)),
        ) {
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .fillMaxWidth()
                    .padding(horizontal = topRowPaddingHorizontal),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(topRowSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onSelectAll, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_select_all),
                            contentDescription = "全选",
                            tint = textColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    IconButton(onClick = onDeleteSelected, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = "删除",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_more_vert),
                        contentDescription = "更多操作",
                        tint = textColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    FusedMenuItem(stringResource(R.string.menu_start), onStartSelected) { isExpanded = false }
                    FusedMenuItem(stringResource(R.string.menu_pause), onStopSelected) { isExpanded = false }
                    FusedMenuItem(
                        text = stringResource(R.string.menu_rename),
                        enabled = selectedCount == 1,
                        onClick = onRenameSelected,
                    ) { isExpanded = false }
                    FusedMenuItem(stringResource(R.string.menu_set_location), onSetLocationSelected) { isExpanded = false }
                    FusedMenuItem(stringResource(R.string.menu_set_hr), onSetHrSelected) { isExpanded = false }
                    FusedMenuItem(stringResource(R.string.menu_verify), onVerifySelected) { isExpanded = false }
                    FusedMenuItem(stringResource(R.string.menu_reannounce), onReannounceSelected) { isExpanded = false }
                }
            }
        }
    }
}

@Composable
private fun FusedMenuItem(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    onClose: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (enabled) (if (isDark) Color.White else Color(0xFF2D3436)) else (if (isDark) Color(0xFF636E72) else Color(0xFFB0BEC5))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable(enabled = enabled) {
                onClose()
                onClick()
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}

/**
 * 3. 详情/节点页面顶栏：[← 返回] 按键、[信息 / 节点] 分段选项卡胶囊
 */
@Composable
fun DetailTopBar(
    currentPage: Int,
    backSwipeRatio: Float = 0f,
    backdropLayer: GraphicsLayer? = null,
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
        // 左侧圆形返回按钮
        TopBarGlassSurface(
            shape = CircleShape,
            border = BorderStroke(1.dp, barBorderColor),
            backdropLayer = backdropLayer,
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

        // 中间 [信息 / 节点] 分段选项卡胶囊
        val activeBgColor = if (isDark) Color(0x44FFFFFF) else Color(0x55FFFFFF)
        val activeBorderColor = if (isDark) Color(0xAAFFFFFF) else Color(0xCCFFFFFF)

        TopBarGlassSurface(
            shape = RoundedCornerShape(100.dp),
            border = BorderStroke(1.dp, barBorderColor),
            backdropLayer = backdropLayer,
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
                                color = if (isDark) Color.White else Color(0xFF2D3436),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun getFilterTitleText(filter: String): String {
    return when (filter) {
        "Downloading" -> stringResource(R.string.nav_downloading)
        "Seeding" -> stringResource(R.string.nav_seeding)
        "Paused" -> stringResource(R.string.nav_paused)
        "Active" -> stringResource(R.string.nav_active)
        "Inactive" -> stringResource(R.string.nav_inactive)
        "Error" -> stringResource(R.string.nav_error)
        else -> {
            if (filter.startsWith("tracker:")) {
                filter.substringAfter("tracker:")
            } else if (filter.startsWith("label:")) {
                filter.substringAfter("label:")
            } else {
                stringResource(R.string.nav_all)
            }
        }
    }
}

// ============================================================================
// Safe Compose Previews for TorrentTopAppBar states (LayoutLib Crash-Free)
// ============================================================================

@Preview(name = "常规模式 - 浅色模式", showBackground = true)
@Composable
fun FloatingTopControls_Normal_Light_Preview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxWidth().height(60.dp), color = Color(0xFFF0F2F5)) {
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
                isDark = false
            )
        }
    }
}

@Preview(name = "多选模式 - 深色模式", showBackground = true)
@Composable
fun FloatingTopControls_MultiSelect_Dark_Preview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxWidth().height(120.dp), color = Color(0xFF161F29)) {
            FloatingTopControls(
                titleText = "全部任务",
                sizeText = "58.5 GB",
                altSpeedEnabled = true,
                selectedCount = 3,
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
                isDark = true
            )
        }
    }
}

@Preview(name = "SettingsTopBar Preview", showBackground = true)
@Composable
fun SettingsTopBar_Preview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxWidth().height(60.dp), color = Color(0xFFF0F2F5)) {
            SettingsTopBar(
                onBackClick = {},
                isDark = false
            )
        }
    }
}

@Preview(name = "DetailTopBar Preview", showBackground = true)
@Composable
fun DetailTopBar_Preview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxWidth().height(60.dp), color = Color(0xFF161F29)) {
            DetailTopBar(
                currentPage = 0,
                onTabSelected = {},
                onBackClick = {},
                isDark = true,
            )
        }
    }
}
