package com.kuangru52.transsync

import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 3层严密物理图层规范 Compose 弹窗组件：
 * - 1. 最底层 (Bottom Layer)：背景种子列表 (captured by backdropLayer)
 * - 2. 中间层 (Middle Glass Layer)：液体玻璃卡片底框，独立应用凸透镜折射 Shader 与高斯模糊 Filter
 * - 3. 最上层 (Top Foreground Layer)：100% 绝对清晰的前景输入框、标题与动作按钮，坐落在玻璃卡片上方，完全不受折射模糊影响！
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidGlassDialog(
    onDismissRequest: () -> Unit,
    backdropLayer: GraphicsLayer? = null,
    boxPositionInRoot: Offset = Offset.Zero,
    title: String = "",
    titleContent: (@Composable () -> Unit)? = null,
    confirmButtonText: String = "确定",
    confirmButtonColor: Color = Color(0xFF1D88E3),
    isConfirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    bottomLeftContent: (@Composable () -> Unit)? = null,
    refractionDp: Float? = null,
    refractionHeightDp: Float? = null,
    blurRadiusDp: Float? = null,
    saturationBoost: Float? = null,
    contrast: Float? = null,
    whitePoint: Float? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isInspection = LocalInspectionMode.current

    val dialogCard = @Composable {
        val context = LocalContext.current
        val isDark = isSystemInDarkTheme()
        val effectiveRefractionDp = refractionDp ?: SettingsManager.getDialogRefraction(context, isDark)
        val effectiveRefractionHeightDp = refractionHeightDp ?: SettingsManager.getDialogHeight(context, isDark)
        val effectiveBlurRadiusDp = blurRadiusDp ?: SettingsManager.getDialogBlur(context, isDark)
        val effectiveSaturationBoost = saturationBoost ?: SettingsManager.getDialogSaturation(context, isDark)
        val effectiveContrast = contrast ?: SettingsManager.getDialogContrast(context, isDark)
        val effectiveWhitePoint = whitePoint ?: SettingsManager.getDialogWhitePoint(context, isDark)

        val glassBorderBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) 0.35f else 0.85f),
                if (isDark) Color(0x3BFFFFFF) else Color(0x40E0E0E0),
            ),
            start = Offset.Zero,
            end = Offset(400f, 400f),
        )
        val glassSpecularGradient = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) 0.15f else 0.40f),
                Color.White.copy(alpha = if (isDark) 0.02f else 0.08f),
                Color.Transparent,
            ),
            start = Offset.Zero,
            end = Offset(300f, 300f),
        )
        val density = LocalDensity.current
        val dialogView = LocalView.current

        var cardAbsoluteScreenPosition by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.4f else 0.15f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.5f else 0.2f),
                )
                .onGloballyPositioned { coordinates ->
                    val loc = IntArray(2)
                    dialogView.getLocationOnScreen(loc)
                    val offsetInWindow = coordinates.positionInWindow()
                    cardAbsoluteScreenPosition = Offset(
                        x = loc[0].toFloat() + offsetInWindow.x,
                        y = loc[1].toFloat() + offsetInWindow.y,
                    )
                },
        ) {
            val localOffsetX = (cardAbsoluteScreenPosition.x - boxPositionInRoot.x).coerceAtLeast(0f)
            val localOffsetY = (cardAbsoluteScreenPosition.y - boxPositionInRoot.y).coerceAtLeast(0f)

            // 1. 中间层 (Middle Glass Layer)：独立折射与模糊卡片底框，不包含任何文字与按钮
            val glassTint = if (isDark) Color(0x331F2A38) else Color(0x33FFFFFF)

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, glassBorderBrush, RoundedCornerShape(24.dp))
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(24.dp)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (effectiveRefractionDp != 0f)) {
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
                                        float lensFactor = sin((1.0 - edgeFactor) * 1.5707963);
                                        float2 disp = -norm * (lensFactor * abs(refraction));
                                        
                                        float4 colR = content.eval(coord + disp * 1.08);
                                        float4 colG = content.eval(coord + disp);
                                        float4 colB = content.eval(coord + disp * 0.92);
                                        
                                        vec3 baseRgb = vec3(colR.r, colG.g, colB.b);
                                        float luma = dot(baseRgb, vec3(0.2126, 0.7152, 0.0722));
                                        vec3 satRgb = mix(vec3(luma), baseRgb, saturationBoost);
                                        
                                        float alpha = max(colG.a, max(colR.a, colB.a));
                                        return vec4(satRgb, alpha);
                                    }
                                """.trimIndent()

                                val shader = android.graphics.RuntimeShader(agsl)
                                shader.setFloatUniform("size", size.width, size.height)
                                shader.setFloatUniform("cornerRadius", with(density) { 24.dp.toPx() })
                                shader.setFloatUniform("refraction", with(density) { effectiveRefractionDp.dp.toPx() })
                                shader.setFloatUniform("refractionHeight", with(density) { effectiveRefractionHeightDp.dp.toPx() })
                                shader.setFloatUniform("saturationBoost", effectiveSaturationBoost)
                                shader.setFloatUniform("contrast", effectiveContrast)
                                shader.setFloatUniform("whitePoint", effectiveWhitePoint)

                                val runtimeShaderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")

                                renderEffect = if (effectiveBlurRadiusDp > 0f) {
                                    val blurPx = with(density) { effectiveBlurRadiusDp.dp.toPx() }
                                    val blur = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                                    android.graphics.RenderEffect.createChainEffect(runtimeShaderEffect, blur).asComposeRenderEffect()
                                } else {
                                    runtimeShaderEffect.asComposeRenderEffect()
                                }
                            } else if (effectiveBlurRadiusDp > 0f) {
                                val blurPx = with(density) { effectiveBlurRadiusDp.dp.toPx() }
                                val blur = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                                renderEffect = blur.asComposeRenderEffect()
                            }
                        }
                    }
                    .drawWithContent {
                        if (backdropLayer != null) {
                            translate(
                                left = -localOffsetX,
                                top = -localOffsetY,
                            ) {
                                drawLayer(backdropLayer)
                            }
                            drawRect(color = glassTint)
                        } else {
                            val glassMeshBrush = Brush.radialGradient(
                                colors = if (isDark) listOf(
                                    Color(0xFF2A3B4E),
                                    Color(0xFF1E2C3D),
                                    Color(0xFF131E2C),
                                ) else listOf(
                                    Color(0xFFFFFFFF),
                                    Color(0xFFE4EFF5),
                                    Color(0xFFCCE0EC),
                                ),
                                center = Offset(size.width * 0.3f, size.height * 0.2f),
                                radius = maxOf(size.width, size.height) * 1.2f,
                            )
                            drawRect(brush = glassMeshBrush)
                        }
                        drawRect(brush = glassSpecularGradient)
                    },
            )

            // 2. 最上层 (Top Foreground Layer)：100% 绝对清晰的前景文本、输入框与操作按钮
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                if (titleContent != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        titleContent()
                    }
                } else if (title.isNotEmpty()) {
                    Text(
                        text = title,
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = if (isDark) Color.White else Color(0xFF2D3436),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                content()

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        bottomLeftContent?.invoke()
                    }

                    Surface(
                        onClick = { if (isConfirmEnabled) onConfirm() },
                        shape = RoundedCornerShape(100.dp),
                        color = if (isConfirmEnabled) confirmButtonColor else confirmButtonColor.copy(alpha = 0.4f),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = confirmButtonText,
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    if (isInspection) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            dialogCard()
        }
    } else {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            dialogCard()
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun LiquidGlassDialogPreview() {
    MaterialTheme {
        LiquidGlassDialog(
            onDismissRequest = {},
            backdropLayer = null,
            title = "添加种子",
            onConfirm = {},
        ) {
            Text("预览弹窗内容", fontSize = 14.sp)
        }
    }
}
