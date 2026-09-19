package com.kuangru52.transsync

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 悬浮控制条组件：
 * - 采用 3 层严密物理图层架构：最上层文字与图标 100% 绝对清晰高对比度，中间层玻璃底框单独渲染凸透镜折射 Shader 与模糊！
 * - 开发者模式下，通过【下拉乌龟图标】手势弹出调参 Inspector 调优面板！
 */
@Composable
fun FloatingTopControls(
    titleText: String,
    sizeText: String,
    altSpeedEnabled: Boolean,
    selectedCount: Int,
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
    val barBgColor = if (isDark) Color(0xFF212D3B) else Color.White
    val barBorderColor = if (isDark) Color(0xFF34495E) else Color(0xFFE0E0E0)
    val textColor = if (isDark) Color.White else Color(0xFF2D3436)

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
                color = barBgColor,
                border = BorderStroke(1.dp, barBorderColor),
                shadowElevation = 8.dp,
                modifier = Modifier.height(44.dp),
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
                        modifier = Modifier.size(20.dp),
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

            // 右侧 [乌龟] 独立悬浮按键
            Surface(
                onClick = onTurtleClick,
                shape = CircleShape,
                color = barBgColor,
                border = BorderStroke(1.dp, barBorderColor),
                shadowElevation = 8.dp,
                modifier = Modifier.size(44.dp),
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
            // 多选模式：左侧 [已选择 N 项] 悬浮胶囊
            Surface(
                onClick = onCloseSelection,
                shape = RoundedCornerShape(100.dp),
                color = barBgColor,
                border = BorderStroke(1.dp, barBorderColor),
                shadowElevation = 8.dp,
                modifier = Modifier.height(44.dp),
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

            // 右侧融合扩展悬浮胶囊卡片
            MultiSelectRightCapsule(
                selectedCount = selectedCount,
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
}

/**
 * 多选模式右侧融合扩展悬浮胶囊卡片
 */
@Composable
fun MultiSelectRightCapsule(
    selectedCount: Int,
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

    val barBgColor = if (isDark) Color(0xFF212D3B) else Color.White
    val barBorderColor = if (isDark) Color(0xFF34495E) else Color(0xFFE0E0E0)
    val textColor = if (isDark) Color.White else Color(0xFF2D3436)

    BackHandler(enabled = isExpanded) {
        isExpanded = false
    }

    val cardCornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 18.dp else 100.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "cornerRadius",
    )

    Surface(
        shape = RoundedCornerShape(cardCornerRadius),
        color = barBgColor,
        border = BorderStroke(1.dp, barBorderColor),
        shadowElevation = 8.dp,
        modifier = Modifier.wrapContentSize(),
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .animateContentSize(animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)),
        ) {
            // 顶行按键组 (间距 6dp, 按键尺寸 40dp)
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
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
        else -> if (filter.startsWith("tracker:")) filter.substringAfter("tracker:") else stringResource(R.string.nav_all)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun FloatingTopControlsPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161F29)),
        ) {
            FloatingTopControls(
                titleText = "全部任务",
                sizeText = "71.1 TB",
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
            )
        }
    }
}
