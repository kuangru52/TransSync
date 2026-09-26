package com.kuangru52.transsync

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 悬浮控制条组件：
 * - 采用 3 层物理图层隔离架构：最下层渲染 3D AGSL 凸透镜折射与模糊，最上层前景文本与图标 100% 绝对清晰！
 * - 核心修正：使用 translate(-positionInRoot) 锚定屏幕绝对坐标，确保三点下拉等浮窗精准透出当前所挡住的真正列表内容，而不是最顶部的列表初始项；参数为 0 时 100% 完全透明无深色遮罩。
 * - 开发者模式下，通过【下拉乌龟图标】手势调出顶栏液态玻璃调参 Inspector 调优面板！
 */
@Composable
fun FloatingTopControls(
    titleText: String,
    sizeText: String,
    altSpeedEnabled: Boolean,
    selectedCount: Int,
    drawerSlideRatio: Float = 0f,
    backdropLayer: androidx.compose.ui.graphics.layer.GraphicsLayer? = null,
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
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val isDeveloperMode = remember { SettingsManager.isDeveloperMode(context) }
    var showTuningInspector by remember { mutableStateOf(false) }

    val cachedShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader(LIQUID_GLASS_AGSL)
            } catch (_: Exception) { null }
        } else null
    }

    var liveTopGlassParams by remember(isDark) { mutableStateOf(SettingsManager.getTopBarGlassParams(context, isDark)) }

    var leftCapsulePosInRoot by remember { mutableStateOf(Offset.Zero) }
    var turtlePosInRoot by remember { mutableStateOf(Offset.Zero) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        if (selectedCount == 0) {
            // 常规模式：左侧 [三横 菜单 + 标题] 悬浮胶囊
            Surface(
                onClick = onMenuClick,
                shape = RoundedCornerShape(100.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, barBorderColor),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .wrapContentWidth()
                    .height(44.dp)
                    .onGloballyPositioned { coordinates ->
                        leftCapsulePosInRoot = coordinates.positionInRoot()
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .wrapContentWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // 1. 底层：独占 renderEffect 凸透镜 Shader 渲染层 (仅影响底图，绝对不影响上层文字与图标)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(100.dp))
                            .graphicsLayer {
                                clip = true
                                shape = RoundedCornerShape(100.dp)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (cachedShader != null)) {
                                        try {
                                            val shader = cachedShader
                                            shader.setFloatUniform("size", size.width, size.height)
                                            shader.setFloatUniform("cornerRadius", with(density) { 100.dp.toPx() })
                                            shader.setFloatUniform("refraction", with(density) { liveTopGlassParams.refraction.dp.toPx() })
                                            shader.setFloatUniform("refractionHeight", with(density) { liveTopGlassParams.refractionHeight.dp.toPx() })
                                            shader.setFloatUniform("saturationBoost", liveTopGlassParams.saturationBoost)
                                            shader.setFloatUniform("contrast", liveTopGlassParams.contrast)
                                            shader.setFloatUniform("whitePoint", liveTopGlassParams.whitePoint)

                                            val runtimeEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                                            val blurPx = with(density) { liveTopGlassParams.blurRadius.dp.toPx() }
                                            val blurEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP)
                                            renderEffect = android.graphics.RenderEffect.createChainEffect(runtimeEffect, blurEffect).asComposeRenderEffect()
                                        } catch (_: Exception) {
                                            val blurPx = with(density) { liveTopGlassParams.blurRadius.dp.toPx() }
                                            renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                                        }
                                    } else {
                                        val blurPx = with(density) { liveTopGlassParams.blurRadius.dp.toPx() }
                                        renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                                    }
                                }
                            }
                            .drawBehind {
                                if (backdropLayer != null) {
                                    try {
                                        translate(left = -leftCapsulePosInRoot.x, top = -leftCapsulePosInRoot.y) {
                                            drawLayer(backdropLayer)
                                        }
                                    } catch (_: Exception) {}
                                }
                                if (liveTopGlassParams.whitePoint > 0f) {
                                    drawRect(color = Color.White.copy(alpha = (liveTopGlassParams.whitePoint * 0.3f).coerceIn(0f, 0.4f)))
                                }
                            }
                    )

                    // 2. 最上层：100% 绝对清晰的前景文本与菜单图标 (不在 renderEffect 内部，高清透亮!)
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
            }

            // 右侧 [乌龟] 独立悬浮按键 (支持开发者模式下拉进入调参面板)
            Surface(
                onClick = onTurtleClick,
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, barBorderColor),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(44.dp)
                    .onGloballyPositioned { coordinates ->
                        turtlePosInRoot = coordinates.positionInRoot()
                    }
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
                    // 1. 底层：独占 renderEffect 凸透镜 Shader 渲染层
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .graphicsLayer {
                                clip = true
                                shape = CircleShape
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (cachedShader != null)) {
                                        try {
                                            val shader = cachedShader
                                            shader.setFloatUniform("size", size.width, size.height)
                                            shader.setFloatUniform("cornerRadius", with(density) { 22.dp.toPx() })
                                            shader.setFloatUniform("refraction", with(density) { liveTopGlassParams.refraction.dp.toPx() })
                                            shader.setFloatUniform("refractionHeight", with(density) { liveTopGlassParams.refractionHeight.dp.toPx() })
                                            shader.setFloatUniform("saturationBoost", liveTopGlassParams.saturationBoost)
                                            shader.setFloatUniform("contrast", liveTopGlassParams.contrast)
                                            shader.setFloatUniform("whitePoint", liveTopGlassParams.whitePoint)

                                            val runtimeEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                                            val blurPx = with(density) { liveTopGlassParams.blurRadius.dp.toPx() }
                                            val blurEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP)
                                            renderEffect = android.graphics.RenderEffect.createChainEffect(runtimeEffect, blurEffect).asComposeRenderEffect()
                                        } catch (_: Exception) {
                                            val blurPx = with(density) { liveTopGlassParams.blurRadius.dp.toPx() }
                                            renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                                        }
                                    } else {
                                        val blurPx = with(density) { liveTopGlassParams.blurRadius.dp.toPx() }
                                        renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                                    }
                                }
                            }
                            .drawBehind {
                                if (backdropLayer != null) {
                                    try {
                                        translate(left = -turtlePosInRoot.x, top = -turtlePosInRoot.y) {
                                            drawLayer(backdropLayer)
                                        }
                                    } catch (_: Exception) {}
                                }
                                if (liveTopGlassParams.whitePoint > 0f) {
                                    drawRect(color = Color.White.copy(alpha = (liveTopGlassParams.whitePoint * 0.3f).coerceIn(0f, 0.4f)))
                                }
                            }
                    )

                    // 2. 最上层：100% 高清乌龟图标
                    Icon(
                        painter = painterResource(id = if (altSpeedEnabled) R.drawable.ic_turtle else R.drawable.ic_turtle_outline),
                        contentDescription = "限速模式",
                        tint = if (altSpeedEnabled) Color(0xFFF9A825) else textColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        } else {
            // 多选模式：左侧 [已选择 N 项] 悬浮胶囊
            Surface(
                onClick = onCloseSelection,
                shape = RoundedCornerShape(100.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, barBorderColor),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .wrapContentWidth()
                    .height(44.dp)
                    .onGloballyPositioned { coordinates ->
                        leftCapsulePosInRoot = coordinates.positionInRoot()
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .wrapContentWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(100.dp))
                            .graphicsLayer {
                                clip = true
                                shape = RoundedCornerShape(100.dp)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (cachedShader != null)) {
                                        try {
                                            val shader = cachedShader
                                            shader.setFloatUniform("size", size.width, size.height)
                                            shader.setFloatUniform("cornerRadius", with(density) { 100.dp.toPx() })
                                            shader.setFloatUniform("refraction", with(density) { liveTopGlassParams.refraction.dp.toPx() })
                                            shader.setFloatUniform("refractionHeight", with(density) { liveTopGlassParams.refractionHeight.dp.toPx() })
                                            shader.setFloatUniform("saturationBoost", liveTopGlassParams.saturationBoost)
                                            shader.setFloatUniform("contrast", liveTopGlassParams.contrast)
                                            shader.setFloatUniform("whitePoint", liveTopGlassParams.whitePoint)

                                            val runtimeEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                                            val blurPx = with(density) { liveTopGlassParams.blurRadius.dp.toPx() }
                                            val blurEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP)
                                            renderEffect = android.graphics.RenderEffect.createChainEffect(runtimeEffect, blurEffect).asComposeRenderEffect()
                                        } catch (_: Exception) {
                                            val blurPx = with(density) { liveTopGlassParams.blurRadius.dp.toPx() }
                                            renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                                        }
                                    } else {
                                        val blurPx = with(density) { liveTopGlassParams.blurRadius.dp.toPx() }
                                        renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                                    }
                                }
                            }
                            .drawBehind {
                                if (backdropLayer != null) {
                                    try {
                                        translate(left = -leftCapsulePosInRoot.x, top = -leftCapsulePosInRoot.y) {
                                            drawLayer(backdropLayer)
                                        }
                                    } catch (_: Exception) {}
                                }
                                if (liveTopGlassParams.whitePoint > 0f) {
                                    drawRect(color = Color.White.copy(alpha = (liveTopGlassParams.whitePoint * 0.3f).coerceIn(0f, 0.4f)))
                                }
                            }
                    )

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
            }

            // 右侧融合扩展悬浮胶囊卡片
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

    // 开发者模式下通过【下拉乌龟图标】调出顶栏液态玻璃 Inspector 调参窗口
    if (showTuningInspector && isDeveloperMode) {
        LiquidGlassTuningInspector(
            refractionDp = liveTopGlassParams.refraction,
            refractionHeightDp = liveTopGlassParams.refractionHeight,
            blurRadiusDp = liveTopGlassParams.blurRadius,
            saturationBoost = liveTopGlassParams.saturationBoost,
            contrast = liveTopGlassParams.contrast,
            whitePoint = liveTopGlassParams.whitePoint,
            onRefractionChange = { liveTopGlassParams = liveTopGlassParams.copy(refraction = it); SettingsManager.saveTopBarGlassParams(context, liveTopGlassParams) },
            onRefractionHeightChange = { liveTopGlassParams = liveTopGlassParams.copy(refractionHeight = it); SettingsManager.saveTopBarGlassParams(context, liveTopGlassParams) },
            onBlurRadiusChange = { liveTopGlassParams = liveTopGlassParams.copy(blurRadius = it); SettingsManager.saveTopBarGlassParams(context, liveTopGlassParams) },
            onSaturationBoostChange = { liveTopGlassParams = liveTopGlassParams.copy(saturationBoost = it); SettingsManager.saveTopBarGlassParams(context, liveTopGlassParams) },
            onContrastChange = { liveTopGlassParams = liveTopGlassParams.copy(contrast = it); SettingsManager.saveTopBarGlassParams(context, liveTopGlassParams) },
            onWhitePointChange = { liveTopGlassParams = liveTopGlassParams.copy(whitePoint = it); SettingsManager.saveTopBarGlassParams(context, liveTopGlassParams) },
            onReset = {
                val defParams = GlassParams(if (isDark) -25f else 25f, 12f, 20f, 1.5f, 0.15f, 0.10f)
                liveTopGlassParams = defParams
                SettingsManager.saveTopBarGlassParams(context, defParams)
            },
            onSave = { showTuningInspector = false },
            onDismiss = { showTuningInspector = false }
        )
    }
}

/**
 * 多选模式右侧融合扩展悬浮胶囊卡片
 */
@Composable
fun MultiSelectRightCapsule(
    selectedCount: Int,
    backdropLayer: androidx.compose.ui.graphics.layer.GraphicsLayer? = null,
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

    val context = LocalContext.current
    val density = LocalDensity.current
    val cachedShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader(LIQUID_GLASS_AGSL)
            } catch (_: Exception) { null }
        } else null
    }

    val topGlassParams = remember(isDark) { SettingsManager.getTopBarGlassParams(context, isDark) }

    var capsulePosInRoot by remember { mutableStateOf(Offset.Zero) }

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

    Surface(
        shape = RoundedCornerShape(cardCornerRadius),
        color = Color.Transparent,
        border = BorderStroke(1.dp, barBorderColor),
        shadowElevation = 8.dp,
        modifier = Modifier
            .wrapContentSize()
            .onGloballyPositioned { coordinates ->
                capsulePosInRoot = coordinates.positionInRoot()
            },
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(cardCornerRadius)),
            contentAlignment = Alignment.Center
        ) {
            // 1. 底层：独占 renderEffect 凸透镜 Shader 渲染层 (锚定屏幕绝对坐标，精准透出下方被遮挡的真实列表内容)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(cardCornerRadius))
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(cardCornerRadius)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (cachedShader != null)) {
                                try {
                                    val shader = cachedShader
                                    shader.setFloatUniform("size", size.width, size.height)
                                    shader.setFloatUniform("cornerRadius", with(density) { cardCornerRadius.toPx() })
                                    shader.setFloatUniform("refraction", with(density) { topGlassParams.refraction.dp.toPx() })
                                    shader.setFloatUniform("refractionHeight", with(density) { topGlassParams.refractionHeight.dp.toPx() })
                                    shader.setFloatUniform("saturationBoost", topGlassParams.saturationBoost)
                                    shader.setFloatUniform("contrast", topGlassParams.contrast)
                                    shader.setFloatUniform("whitePoint", topGlassParams.whitePoint)

                                    val runtimeEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                                    val blurPx = with(density) { topGlassParams.blurRadius.dp.toPx() }
                                    val blurEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP)
                                    renderEffect = android.graphics.RenderEffect.createChainEffect(runtimeEffect, blurEffect).asComposeRenderEffect()
                                } catch (_: Exception) {
                                    val blurPx = with(density) { topGlassParams.blurRadius.dp.toPx() }
                                    renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                                }
                            } else {
                                val blurPx = with(density) { topGlassParams.blurRadius.dp.toPx() }
                                renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                            }
                        }
                    }
                    .drawBehind {
                        if (backdropLayer != null) {
                            try {
                                translate(left = -capsulePosInRoot.x, top = -capsulePosInRoot.y) {
                                    drawLayer(backdropLayer)
                                }
                            } catch (_: Exception) {}
                        }
                        if (topGlassParams.whitePoint > 0f) {
                            drawRect(color = Color.White.copy(alpha = (topGlassParams.whitePoint * 0.3f).coerceIn(0f, 0.4f)))
                        }
                    }
            )

            // 2. 最上层：100% 高清的操作按钮与下拉菜单
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .animateContentSize(animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)),
            ) {
                // 顶行按键组 (点击三点展开时，全选与删除图标向左平滑移动扩宽卡片，收起时平滑复位)
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
                    HorizontalDivider(color = barBorderColor, thickness = 1.dp, modifier = Modifier.fillMaxWidth())

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
