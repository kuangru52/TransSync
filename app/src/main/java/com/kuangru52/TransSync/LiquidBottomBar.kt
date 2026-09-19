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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.layout.positionInRoot
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

    Box(
        modifier = modifier
            .imePadding()
            .padding(bottom = 16.dp)
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
    backdropLayer: GraphicsLayer? = null,
    boxPositionInRoot: Offset = Offset.Zero,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onScrollToTop: () -> Unit = {}
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

    Surface(
        modifier = Modifier
            .height(40.dp)
            .then(if (isSearchExpanded) Modifier.width(310.dp) else Modifier.widthIn(min = 180.dp, max = 260.dp))
            .animateContentSize(animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f))
            .scale(interactionScale)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(100.dp),
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.4f else 0.15f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.5f else 0.2f)
            ),
        shape = RoundedCornerShape(100.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, if (isDark) Color(0x44FFFFFF) else Color(0x66FFFFFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentWidth()
                .clip(RoundedCornerShape(100.dp))
                .onGloballyPositioned { coordinates ->
                    barPositionInRoot = coordinates.positionInRoot()
                }
        ) {
            val localOffsetX = (barPositionInRoot.x - boxPositionInRoot.x).coerceAtLeast(0f)
            val localOffsetY = (barPositionInRoot.y - boxPositionInRoot.y).coerceAtLeast(0f)

            // 1. 底层：Kyant0 4-Edge 实时调参凸透镜折射 Shader (-norm * lensFactor * abs(refraction))
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(100.dp))
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(100.dp)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val agsl = """
                                    uniform shader content;
                                    uniform float2 size;
                                    uniform float cornerRadius;
                                    uniform float refraction;
                                    uniform float refractionHeight;
                                    uniform float saturationBoost;

                                    float sdRoundedBox(float2 p, float2 b, float r) {
                                        float2 q = abs(p) - b + float2(r);
                                        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
                                    }

                                    float2 getNormal(float2 p, float2 b, float r) {
                                        float e = 1.0;
                                        float d = sdRoundedBox(p, b, r);
                                        float dx = sdRoundedBox(p + float2(e, 0.0), b, r) - d;
                                        float dy = sdRoundedBox(p + float2(0.0, e), b, r) - d;
                                        float len = length(float2(dx, dy));
                                        return len > 0.0001 ? float2(dx, dy) / len : float2(0.0);
                                    }

                                    vec4 main(float2 coord) {
                                        float2 halfSize = size * 0.5;
                                        float2 p = coord - halfSize;
                                        float r = min(cornerRadius, min(halfSize.x, halfSize.y));
                                        
                                        float dist = sdRoundedBox(p, halfSize, r);
                                        if (dist > 0.0) {
                                            return vec4(0.0);
                                        }
                                        
                                        float2 norm = getNormal(p, halfSize, r);
                                        float edgeFactor = clamp(-dist / max(refractionHeight, 1.0), 0.0, 1.0);
                                        
                                        // Kyant0 凸透镜曲率折射
                                        float lensFactor = sin((1.0 - edgeFactor) * 1.5707963);
                                        float2 disp = -norm * (lensFactor * abs(refraction));
                                        
                                        float4 colR = content.eval(coord + disp * 1.08);
                                        float4 colG = content.eval(coord + disp);
                                        float4 colB = content.eval(coord + disp * 0.92);
                                        
                                        // 实时彩度增强
                                        vec3 baseRgb = vec3(colR.r, colG.g, colB.b);
                                        float luma = dot(baseRgb, vec3(0.2126, 0.7152, 0.0722));
                                        vec3 satRgb = mix(vec3(luma), baseRgb, saturationBoost);
                                        
                                        float alpha = max(colG.a, max(colR.a, colB.a));
                                        return vec4(satRgb, alpha);
                                    }
                                """.trimIndent()

                                val shader = android.graphics.RuntimeShader(agsl)
                                shader.setFloatUniform("size", size.width, size.height)
                                shader.setFloatUniform("cornerRadius", with(density) { 100.dp.toPx() })
                                shader.setFloatUniform("refraction", with(density) { liveRefractionDp.dp.toPx() })
                                shader.setFloatUniform("refractionHeight", with(density) { liveRefractionHeightDp.dp.toPx() })
                                shader.setFloatUniform("saturationBoost", liveSaturationBoost)

                                val runtimeShaderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")

                                renderEffect = if (liveBlurRadiusDp > 0f) {
                                    val blurPx = with(density) { liveBlurRadiusDp.dp.toPx() }
                                    val blur = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                                    android.graphics.RenderEffect.createChainEffect(runtimeShaderEffect, blur).asComposeRenderEffect()
                                } else {
                                    runtimeShaderEffect.asComposeRenderEffect()
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
                    .wrapContentWidth()
                    .padding(horizontal = 12.dp),
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
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭搜索",
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

    // 上滑调起的实时参数调节调优面板 (仅开发者模式可用)
    val isDeveloperMode = remember(showTuningInspector) { SettingsManager.isDeveloperMode(context) }
    if (showTuningInspector && isDeveloperMode) {
        LiquidGlassTuningInspector(
            refractionDp = liveRefractionDp,
            refractionHeightDp = liveRefractionHeightDp,
            blurRadiusDp = liveBlurRadiusDp,
            saturationBoost = liveSaturationBoost,
            onRefractionChange = {
                liveRefractionDp = it
                SettingsManager.setSpeedbarRefraction(context, it)
            },
            onRefractionHeightChange = {
                liveRefractionHeightDp = it
                SettingsManager.setSpeedbarHeight(context, it)
            },
            onBlurRadiusChange = {
                liveBlurRadiusDp = it
                SettingsManager.setSpeedbarBlur(context, it)
            },
            onSaturationBoostChange = {
                liveSaturationBoost = it
                SettingsManager.setSpeedbarSaturation(context, it)
            },
            onReset = {
                val defRefraction = if (isDark) 51f else 14f
                val defHeight = 3f
                val defBlur = 14f
                val defSaturation = 1.60f

                liveRefractionDp = defRefraction
                liveRefractionHeightDp = defHeight
                liveBlurRadiusDp = defBlur
                liveSaturationBoost = defSaturation

                SettingsManager.setSpeedbarRefraction(context, defRefraction)
                SettingsManager.setSpeedbarHeight(context, defHeight)
                SettingsManager.setSpeedbarBlur(context, defBlur)
                SettingsManager.setSpeedbarSaturation(context, defSaturation)
            },
            onSave = {
                SettingsManager.setSpeedbarRefraction(context, liveRefractionDp)
                SettingsManager.setSpeedbarHeight(context, liveRefractionHeightDp)
                SettingsManager.setSpeedbarBlur(context, liveBlurRadiusDp)
                SettingsManager.setSpeedbarSaturation(context, liveSaturationBoost)
                android.widget.Toast.makeText(context, "网速条参数保存成功", android.widget.Toast.LENGTH_SHORT).show()
                showTuningInspector = false
            },
            onDismiss = { showTuningInspector = false }
        )
    }
}

/**
 * 屏幕左上角紧凑型悬浮调参面板 (单行化极简控制，不遮挡主体弹窗)
 */
@Composable
fun LiquidGlassTuningInspector(
    refractionDp: Float,
    refractionHeightDp: Float,
    blurRadiusDp: Float,
    saturationBoost: Float,
    onRefractionChange: (Float) -> Unit,
    onRefractionHeightChange: (Float) -> Unit,
    onBlurRadiusChange: (Float) -> Unit,
    onSaturationBoostChange: (Float) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
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

                    // 1. 折射量 (Refraction)
                    CompactTuningRow("折射", "${refractionDp.toInt()}dp", refractionDp, -60f..60f, onRefractionChange)

                    // 2. 折射高度 (Refraction Height)
                    CompactTuningRow("高度", "${refractionHeightDp.toInt()}dp", refractionHeightDp, 1f..50f, onRefractionHeightChange)

                    // 3. 模糊半径 (Blur Radius)
                    CompactTuningRow("模糊", "${blurRadiusDp.toInt()}dp", blurRadiusDp, 0f..200f, onBlurRadiusChange)

                    // 4. 彩度增强 (Saturation Boost)
                    CompactTuningRow("彩度", String.format(java.util.Locale.US, "%.1f", saturationBoost), saturationBoost, 0.8f..3.0f, onSaturationBoostChange)
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

@Preview(showBackground = true)
@Composable
fun LiquidBottomBarPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .padding(16.dp)
        ) {
            LiquidBottomBarContent(
                dlSpeed = "12.5 MB/s",
                ulSpeed = "4.2 MB/s",
                onSearchQueryChange = {},
                onSearchToggle = {}
            )
        }
    }
}
