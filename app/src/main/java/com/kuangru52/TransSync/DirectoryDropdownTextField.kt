package com.kuangru52.transsync

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties

/**
 * 100% 统一规范的保存路径下拉输入框组件：
 * - 限制下拉列表为精致悬浮卡片形式 (heightIn(max = 200.dp))，绝不上下顶到屏幕边缘
 * - 限制下拉列表宽度与输入框 1:1 精确对齐 (不全屏拉伸)
 * - 给予 16dp 优雅圆角与阴影外边框 (shape = RoundedCornerShape(16.dp))
 * - 展开时按返回键 100% 仅收起下拉列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryDropdownTextField(
    value: String,
    onValueChange: (String) -> Unit,
    allDirs: List<String>,
    modifier: Modifier = Modifier,
    label: String = "下载目录 (可选)",
    isDark: Boolean = isSystemInDarkTheme(),
) {
    var isExpanded by remember { mutableStateOf(value = false) }
    var textFieldWidthDp by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    // 1. 当下拉菜单展开时，按系统返回键只收起下拉菜单，不关闭主弹窗
    BackHandler(enabled = isExpanded) {
        isExpanded = false
    }

    val customSelectionColors = remember(isDark) {
        TextSelectionColors(
            handleColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF),
            backgroundColor = (if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF)).copy(alpha = 0.3f),
        )
    }

    CompositionLocalProvider(LocalTextSelectionColors provides customSelectionColors) {
        ExposedDropdownMenuBox(
            expanded = isExpanded,
            onExpandedChange = { },
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    onValueChange(newValue)
                },
                modifier = modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        textFieldWidthDp = with(density) { coordinates.size.width.toDp() }
                    }
                    .menuAnchor(MenuAnchorType.PrimaryEditable),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                label = { Text(label) },
                trailingIcon = {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF),
                    unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                    focusedLabelColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF),
                    unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                    focusedTextColor = if (isDark) Color.White else Color(0xFF2D3436),
                    unfocusedTextColor = if (isDark) Color.White else Color(0xFF2D3436),
                )
            )

            if (allDirs.isNotEmpty()) {
                DropdownMenu(
                    expanded = isExpanded,
                    onDismissRequest = { isExpanded = false },
                    properties = PopupProperties(focusable = true, dismissOnBackPress = true),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .then(if (textFieldWidthDp > 0.dp) Modifier.width(textFieldWidthDp) else Modifier.fillMaxWidth(0.9f))
                        .heightIn(max = 200.dp) // 限制最大高度为 200dp 悬浮卡片形式，决不上下顶到屏幕边缘
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color(0xFF1A232E) else Color.White)
                ) {
                    allDirs.forEach { dir ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = dir,
                                    style = TextStyle(
                                        fontSize = 12.5.sp,
                                        lineHeight = 15.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        color = if (isDark) Color.White else Color(0xFF2D3436)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp),
                            onClick = {
                                onValueChange(dir)
                                isExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DirectoryDropdownTextFieldPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            DirectoryDropdownTextField(
                value = "/downloads/movies",
                onValueChange = {},
                allDirs = listOf("/downloads/movies", "/downloads/tv", "/downloads/music")
            )
        }
    }
}
