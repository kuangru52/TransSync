package com.kuangru52.transsync

import android.annotation.SuppressLint
import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import android.view.WindowManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Apple & Kyant0 官方标准 Liquid Glass (AndroidLiquidGlass) 悬浮底栏：
 * - 支持上滑手势 (detectVerticalDragGestures) 调起实时参数调节调优面板
 * - 实时动态拉动 Slider 调节：折射量、折射高度、模糊半径、彩度增强，悬浮网速条在下方 120 FPS 实时更新呈现！
 */
@Composable
fun LiquidBottomBar(
    viewModel: TorrentListViewModel,
    backdropLayer: GraphicsLayer?,
    modifier: Modifier = Modifier,
    boxPositionInRoot: Offset = Offset.Zero,
    onSearchToggle: (Boolean) -> Unit = {},
    onScrollToTop: () -> Unit = {},
) {
    val dlSpeed by viewModel.totalDownloadSpeed.observeAsState("0 B/s")
    val ulSpeed by viewModel.totalUploadSpeed.observeAsState("0 B/s")

    val density = LocalDensity.current
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val navBottomDp = with(density) { navBottomPx.toDp() }.coerceAtMost(32.dp)

    Box(
        modifier = modifier
            .padding(bottom = navBottomDp + 16.dp)
    ) {
        LiquidBottomBarContent(
            dlSpeed = dlSpeed,
            ulSpeed = ulSpeed,
            backdropLayer = backdropLayer,
            boxPositionInRoot = boxPositionInRoot,
            onSearchQueryChange = { query ->
                viewModel.setSearchQuery(query)
            },
            onSearchToggle = onSearchToggle,
            onScrollToTop = onScrollToTop,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@SuppressLint("ReturnFromAwaitPointerEvent")
@Composable
fun LiquidBottomBarContent(
    dlSpeed: String,
    ulSpeed: String,
    modifier: Modifier = Modifier,
    backdropLayer: GraphicsLayer? = null,
    boxPositionInRoot: Offset = Offset.Zero,
    onSearchQueryChange: (String) -> Unit = {},
    onSearchToggle: (Boolean) -> Unit = {},
    onScrollToTop: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var isSearchExpanded by remember { mutableStateOf(value = false) }
    var searchQuery by remember { mutableStateOf("") }

    var liveRefractionDp by remember(isDark) { mutableFloatStateOf(SettingsManager.getSpeedbarRefraction(context, isDark)) }
    var liveRefractionHeightDp by remember(isDark) { mutableFloatStateOf(SettingsManager.getSpeedbarHeight(context, isDark)) }
    var liveBlurRadiusDp by remember(isDark) { mutableFloatStateOf(SettingsManager.getSpeedbarBlur(context, isDark)) }
    var liveSaturationBoost by remember(isDark) { mutableFloatStateOf(SettingsManager.getSpeedbarSaturation(context, isDark)) }
    var liveContrast by remember(isDark) { mutableFloatStateOf(SettingsManager.getSpeedbarContrast(context, isDark)) }
    var liveWhitePoint by remember(isDark) { mutableFloatStateOf(SettingsManager.getSpeedbarWhitePoint(context, isDark)) }
    var showTuningInspector by remember { mutableStateOf(value = false) }

    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(isImeVisible) {
        if (!isImeVisible && isSearchExpanded) {
            isSearchExpanded = false
            searchQuery = ""
            onSearchQueryChange("")
            focusManager.clearFocus()
            onSearchToggle(false)
        }
    }

    var barPositionInRoot by remember { mutableStateOf(Offset.Zero) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val interactionScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
        label = "scale"
    )

    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        searchQuery = ""
        onSearchQueryChange("")
        keyboardController?.hide()
        focusManager.clearFocus()
        onSearchToggle(false)
    }

    val mainView = LocalView.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeBottomDp = with(density) { imeBottomPx.toDp() }
    val imeTranslationYPx = if (isSearchExpanded) {
        -with(density) { imeBottomDp.coerceAtMost(320.dp).toPx() }
    } else {
        0f
    }

    val animatedTranslationY by animateFloatAsState(
        targetValue = imeTranslationYPx,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f),
        label = "imeTranslationY"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = animatedTranslationY
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
        modifier = Modifier
            .height(40.dp)
            .then(if (isSearchExpanded) Modifier.fillMaxWidth().padding(horizontal = 12.dp) else Modifier.wrapContentWidth())
            .animateContentSize(animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f))
            .scale(interactionScale),
        shape = RoundedCornerShape(100.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, if (isDark) Color(0x44FFFFFF) else Color(0x66FFFFFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .then(if (isSearchExpanded) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                .clip(RoundedCornerShape(100.dp))
                .onGloballyPositioned { coordinates ->
                    val loc = IntArray(2)
                    mainView.getLocationOnScreen(loc)
                    val offsetInWindow = coordinates.positionInWindow()
                    barPositionInRoot = Offset(
                        x = loc[0].toFloat() + offsetInWindow.x,
                        y = loc[1].toFloat() + offsetInWindow.y,
                    )
                }
        ) {
            val localOffsetX = (barPositionInRoot.x - boxPositionInRoot.x).coerceAtLeast(0f)
            val localOffsetY = (barPositionInRoot.y - boxPositionInRoot.y).coerceAtLeast(0f)

            val cachedShader = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        android.graphics.RuntimeShader(LIQUID_GLASS_AGSL)
                    } catch (_: Exception) { null }
                } else null
            }

            // 1. 底层：Kyant0 4-Edge 实时调参凸透镜折射 Shader (-norm * lensFactor * abs(refraction))
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
                                    shader.setFloatUniform("refraction", with(density) { liveRefractionDp.dp.toPx() })
                                    shader.setFloatUniform("refractionHeight", with(density) { liveRefractionHeightDp.dp.toPx() })
                                    shader.setFloatUniform("saturationBoost", liveSaturationBoost)
                                    shader.setFloatUniform("contrast", liveContrast)
                                    shader.setFloatUniform("whitePoint", liveWhitePoint)

                                    val runtimeShaderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")

                                    renderEffect = if (liveBlurRadiusDp > 0f) {
                                        val blurPx = with(density) { liveBlurRadiusDp.dp.toPx() }
                                        val blur = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                                        android.graphics.RenderEffect.createChainEffect(runtimeShaderEffect, blur).asComposeRenderEffect()
                                    } else {
                                        runtimeShaderEffect.asComposeRenderEffect()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    if (liveBlurRadiusDp > 0f) {
                                        val blurPx = with(density) { liveBlurRadiusDp.dp.toPx() }
                                        renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP).asComposeRenderEffect()
                                    }
                                }
                            } else if (liveBlurRadiusDp > 0f) {
                                val blurPx = with(density) { liveBlurRadiusDp.dp.toPx() }
                                val blur = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                                renderEffect = blur.asComposeRenderEffect()
                            }
                        }
                    }
                    .drawWithContent {
                        if (backdropLayer != null) {
                            translate(
                                left = -localOffsetX,
                                top = -localOffsetY
                            ) {
                                drawLayer(backdropLayer)
                            }
                        }
                        drawRect(color = Color.Transparent)
                    }
            )

            val context = androidx.compose.ui.platform.LocalContext.current
            val isDeveloperMode = remember(showTuningInspector) { SettingsManager.isDeveloperMode(context) }

            // 手势交互层 (仅在开发者模式开启时允许上滑调起调参 Inspector)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (isDeveloperMode) {
                            Modifier.pointerInput(Unit) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    if (dragAmount < -12f) { // 上滑手势
                                        change.consume()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showTuningInspector = true
                                    }
                                }
                            }
                        } else Modifier
                    )
                    .then(
                        if (!isSearchExpanded) {
                            Modifier.combinedClickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isSearchExpanded = true
                                    onSearchToggle(true)
                                },
                                onDoubleClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onScrollToTop()
                                }
                            )
                        } else Modifier
                    )
            )

            // 顶层 100% 绝对清晰的前景网速文字与搜索输入框
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(if (isSearchExpanded) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isSearchExpanded) {
                    SpeedSection(dlSpeed, ulSpeed, isDark)
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = if (isDark) Color.White else Color.Black,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isSearchExpanded = false
                                        searchQuery = ""
                                        onSearchQueryChange("")
                                        focusManager.clearFocus()
                                        onSearchToggle(false)
                                    }
                                )
                        )

                        ActiveSearchField(
                            query = searchQuery,
                            onQueryChange = {
                                searchQuery = it
                                onSearchQueryChange(it.lowercase())
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        )
                    }
                }
            }
        }
    }
    }

    // 上滑调起的实时参数调节调优面板 (仅开发者模式可用)
    val isDeveloperMode = remember(showTuningInspector) { SettingsManager.isDeveloperMode(context) }
    if (showTuningInspector && isDeveloperMode) {
        LiquidGlassTuningInspector(
            refractionDp = liveRefractionDp,
            refractionHeightDp = liveRefractionHeightDp,
            blurRadiusDp = liveBlurRadiusDp,
            saturationBoost = liveSaturationBoost,
            contrast = liveContrast,
            whitePoint = liveWhitePoint,
            onRefractionChange = { liveRefractionDp = it },
            onRefractionHeightChange = { liveRefractionHeightDp = it },
            onBlurRadiusChange = { liveBlurRadiusDp = it },
            onSaturationBoostChange = { liveSaturationBoost = it },
            onContrastChange = { liveContrast = it },
            onWhitePointChange = { liveWhitePoint = it },
            onReset = {
                val defRefraction = -30f
                val defHeight = 3f
                val defBlur = 14f
                val defSaturation = 1.60f
                val defContrast = 0.0f
                val defWhitePoint = if (isDark) 0.10f else 0.20f

                liveRefractionDp = defRefraction
                liveRefractionHeightDp = defHeight
                liveBlurRadiusDp = defBlur
                liveSaturationBoost = defSaturation
                liveContrast = defContrast
                liveWhitePoint = defWhitePoint

                SettingsManager.setSpeedbarRefraction(context, defRefraction)
                SettingsManager.setSpeedbarHeight(context, defHeight)
                SettingsManager.setSpeedbarBlur(context, defBlur)
                SettingsManager.setSpeedbarSaturation(context, defSaturation)
                SettingsManager.setSpeedbarContrast(context, defContrast)
                SettingsManager.setSpeedbarWhitePoint(context, defWhitePoint)
            },
            onSave = {
                SettingsManager.setSpeedbarRefraction(context, liveRefractionDp)
                SettingsManager.setSpeedbarHeight(context, liveRefractionHeightDp)
                SettingsManager.setSpeedbarBlur(context, liveBlurRadiusDp)
                SettingsManager.setSpeedbarSaturation(context, liveSaturationBoost)
                SettingsManager.setSpeedbarContrast(context, liveContrast)
                SettingsManager.setSpeedbarWhitePoint(context, liveWhitePoint)
                android.widget.Toast.makeText(context, "网速条参数保存成功", android.widget.Toast.LENGTH_SHORT).show()
                showTuningInspector = false
            },
            onDismiss = { showTuningInspector = false },
        )
    }
}

/**
 * 屏幕左上角紧凑型悬浮调参面板 (单行化极简控制，不遮挡主体弹窗)
 */
@SuppressLint("NewApi")
@Composable
fun LiquidGlassTuningInspector(
    refractionDp: Float,
    refractionHeightDp: Float,
    blurRadiusDp: Float,
    saturationBoost: Float,
    contrast: Float = 1.0f,
    whitePoint: Float = 0.15f,
    refractionRange: ClosedFloatingPointRange<Float> = -300f..0f,
    refractionHeightRange: ClosedFloatingPointRange<Float> = 0f..30f,
    blurRadiusRange: ClosedFloatingPointRange<Float> = 0f..60f,
    saturationBoostRange: ClosedFloatingPointRange<Float> = 0.50f..2.00f,
    contrastRange: ClosedFloatingPointRange<Float> = -0.50f..0.50f,
    whitePointRange: ClosedFloatingPointRange<Float> = -1.00f..1.00f,
    onRefractionChange: (Float) -> Unit,
    onRefractionHeightChange: (Float) -> Unit,
    onBlurRadiusChange: (Float) -> Unit,
    onSaturationBoostChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit = {},
    onWhitePointChange: (Float) -> Unit = {},
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val cardBgColor = if (isDark) Color(0xFF1F2A38) else Color.White
    val cardBorderColor = if (isDark) Color(0xFF34495E) else Color(0xFFE0E0E0)
    val primaryTextColor = if (isDark) Color.White else Color(0xFF2D3436)

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setDimAmount(0f)
            dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp, start = 16.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Surface(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .width(230.dp)
                    .wrapContentHeight()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(18.dp)
                    ),
                shape = RoundedCornerShape(18.dp),
                color = cardBgColor,
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onReset, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(28.dp)) {
                            Text("重置", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onSave, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(28.dp)) {
                            Text("保存", color = Color(0xFF1D88E3), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(28.dp)) {
                            Text("关闭", color = primaryTextColor, fontSize = 12.sp)
                        }
                    }

                    // 1. 折射量
                    CompactTuningRow("折射", "${refractionDp.toInt()}dp", refractionDp, refractionRange, onRefractionChange)

                    // 2. 折射高度
                    CompactTuningRow("高度", "${refractionHeightDp.toInt()}dp", refractionHeightDp, refractionHeightRange, onRefractionHeightChange)

                    // 3. 模糊半径
                    CompactTuningRow("模糊", "${blurRadiusDp.toInt()}dp", blurRadiusDp, blurRadiusRange, onBlurRadiusChange)

                    // 4. 彩度增强
                    CompactTuningRow("彩度", String.format(java.util.Locale.US, "%.2f", saturationBoost), saturationBoost, saturationBoostRange, onSaturationBoostChange)

                    // 5. 对比度
                    CompactTuningRow("对比", String.format(java.util.Locale.US, "%.2f", contrast), contrast, contrastRange, onContrastChange)

                    // 6. 白点
                    CompactTuningRow("白点", String.format(java.util.Locale.US, "%.2f", whitePoint), whitePoint, whitePointRange, onWhitePointChange)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactTuningRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val primaryTextColor = if (isDark) Color.White else Color(0xFF2D3436)
    val accentColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF)
    val trackInactiveColor = if (isDark) Color(0x33FFFFFF) else Color(0x22000000)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, fontSize = 11.5.sp, color = primaryTextColor, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            thumb = {},
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(3.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = accentColor,
                        inactiveTrackColor = trackInactiveColor
                    )
                )
            },
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
        )

        Text(text = valueText, fontSize = 11.sp, color = primaryTextColor, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
    }
}

@Composable
fun ActiveSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val uiColor = if (isDark) Color.White else Color.Black
    val focusRequester = remember { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(80.milliseconds)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        textStyle = TextStyle(
            color = uiColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        ),
        cursorBrush = SolidColor(uiColor),
        singleLine = true,
        decorationBox = { innerTextField ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索种子...",
                        color = uiColor.copy(alpha = 0.5f),
                        fontSize = 15.sp
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun SpeedSection(
    dlSpeed: String,
    ulSpeed: String,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val textColor = if (isDark) Color.White else Color(0xFF2D3436)

    Row(
        modifier = Modifier
            .wrapContentWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_down),
                contentDescription = "下载",
                tint = Color(0xFF00E676),
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = dlSpeed,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_up),
                contentDescription = "上传",
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = ulSpeed,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        }
    }
}

@Preview(name = "网速条 - 默认", showBackground = true)
@Composable
fun LiquidBottomBarPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .padding(16.dp),
        ) {
            LiquidBottomBarContent(
                dlSpeed = "12.5 MB/s",
                ulSpeed = "4.2 MB/s",
                onSearchQueryChange = {},
                onSearchToggle = {},
            )
        }
    }
}

@Preview(name = "调参 Inspector 面板", showBackground = true)
@Composable
fun LiquidGlassTuningInspector_Preview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            LiquidGlassTuningInspector(
                refractionDp = 60f,
                refractionHeightDp = 50f,
                blurRadiusDp = 120f,
                saturationBoost = 3.0f,
                onRefractionChange = {},
                onRefractionHeightChange = {},
                onBlurRadiusChange = {},
                onSaturationBoostChange = {},
                onReset = {},
                onSave = {},
                onDismiss = {},
            )
        }
    }
}
