package com.kuangru52.transsync

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ============================================================================
 * 全应用统一顶部悬浮控制栏组件库 (AppTopBars.kt)
 * - 规范统一所有顶栏元素：
 *   1. 设置页面：[← 设置]
 *   2. 主页常规模式：[三横 菜单 + 全部任务]、[乌龟图标按键]
 *   3. 主页多选模式：[已选择 N 个]、右侧 [全选 / 删除 / 三点] 融合扩展卡片及展开下拉菜单
 *   4. 详情/节点页面：[← 返回] 按键、[信息 / 节点] 分段选项卡胶囊
 * - 统一共享 3D AGSL 凸透镜液态玻璃效果、响应式调参绑定、与 3 层物理图层高清隔离架构！
 * ============================================================================
 */

@Composable
fun AppTopBarSurface(
    shape: androidx.compose.ui.graphics.Shape,
    border: BorderStroke?,
    modifier: Modifier = Modifier,
    shadowElevation: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()

    val topBarVersion by SettingsManager.topBarGlassParamsVersion.collectAsState()
    val glassParams = remember(isDark, topBarVersion) { SettingsManager.getTopBarGlassParams(context, isDark) }

    val barBgColor = if (isDark) Color(0x99141D26) else Color(0xA6FFFFFF)

    val cachedShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader(LIQUID_GLASS_AGSL)
            } catch (_: Exception) { null }
        } else null
    }

    Surface(
        shape = shape,
        color = Color.Transparent,
        border = border,
        shadowElevation = shadowElevation,
        modifier = modifier
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
            // 1. 底层：独占 renderEffect 凸透镜 Shader 渲染层
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                        this.shape = shape
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (cachedShader != null)) {
                                try {
                                    val shader = cachedShader
                                    shader.setFloatUniform("size", size.width, size.height)
                                    val radiusPx = if (shape == CircleShape) {
                                        (minOf(size.width, size.height) / 2f)
                                    } else {
                                        with(density) { 100.dp.toPx() }
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
                        drawRect(color = barBgColor)
                        if (glassParams.whitePoint > 0f) {
                            drawRect(color = Color.White.copy(alpha = (glassParams.whitePoint * 0.3f).coerceIn(0f, 0.4f)))
                        }
                    }
            )

            // 2. 最上层：100% 矢量原生清晰的前景文本与图标
            Box(
                modifier = Modifier.fillMaxHeight().wrapContentWidth(),
                contentAlignment = Alignment.Center,
                content = content
            )
        }
    }
}

/**
 * 1. 设置页面顶栏：[← 设置]
 */
@Composable
fun SettingsTopBar(
    onBackClick: () -> Unit,
    backSwipeRatio: Float = 0f,
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
        AppTopBarSurface(
            shape = RoundedCornerShape(100.dp),
            border = BorderStroke(1.dp, barBorderColor),
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
 * 4. 详情/节点页面顶栏：[← 返回] 按键、[信息 / 节点] 分段选项卡胶囊
 */
@Composable
fun DetailTopBar(
    currentPage: Int,
    backSwipeRatio: Float = 0f,
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
        AppTopBarSurface(
            shape = CircleShape,
            border = BorderStroke(1.dp, barBorderColor),
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

        AppTopBarSurface(
            shape = RoundedCornerShape(100.dp),
            border = BorderStroke(1.dp, barBorderColor),
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
                                color = if (isSelected) Color.White else textColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
