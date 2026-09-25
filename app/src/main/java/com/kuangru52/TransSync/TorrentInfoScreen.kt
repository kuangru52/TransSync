package com.kuangru52.transsync

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 100% 纯 Compose 版本的 TorrentInfoScreen 详情信息界面：
 * - 复刻原版递归树状节点结构 (TorrentFileTreeView)，多级目录缩进、文件夹展开/折叠箭头与百分比精准对齐
 * - 各个元素位置、字号、卡片间距、内边距与功能与 XML 1:1 绝对一致
 * - 点击名称展开/收起文件树，长按复制文本
 * - 点击路径或 Tracker 编辑按钮 调起 Compose 100% 实心毛玻璃弹窗
 */
@android.annotation.SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentInfoScreen(
    torrent: Torrent?,
    rpcUrl: String,
    user: String,
    pass: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    var isFileTreeExpanded by remember { mutableStateOf(false) }

    // 弹窗状态管理
    var showRenameDialogState by remember { mutableStateOf(false) }
    var showSetLocationDialogState by remember { mutableStateOf(false) }
    var showEditTrackersDialogState by remember { mutableStateOf(false) }
    var showSetHrDialogState by remember { mutableStateOf(false) }

    val infoDialogSessionKey = remember(
        showRenameDialogState,
        showSetLocationDialogState,
        showEditTrackersDialogState,
        showSetHrDialogState,
    ) {
        java.util.UUID.randomUUID().toString()
    }

    val graphicsContext = LocalGraphicsContext.current
    val infoBackdropLayer = remember(infoDialogSessionKey) {
        graphicsContext.createGraphicsLayer()
    }
    DisposableEffect(infoDialogSessionKey) {
        onDispose {
            graphicsContext.releaseGraphicsLayer(infoBackdropLayer)
        }
    }

    val infoView = LocalView.current
    var infoViewLocation by remember { mutableStateOf(Offset.Zero) }

    val cardBgColor = if (isDark) Color(0x99141D26) else Color(0xA6FFFFFF)
    val cardBorderColor = if (isDark) Color(0x3BFFFFFF) else Color(0x55E0E0E0)
    val primaryTextColor = if (isDark) Color.White else Color(0xFF2D3436)
    val secondaryTextColor = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72)
    val accentColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF)

    val copyToClipboard = { label: String, text: String ->
        if (text.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = android.content.ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
    }

    val samplePreviewTorrent = remember {
        Torrent(
            id = 1,
            name = "Jumanji.The.Next.Level.2026.2160p.HQ.WEB-DL.mkv",
            totalSize = 44238000000L,
            percentDone = 0.605,
            rateDownload = 1200000L,
            rateUpload = 450000L,
            status = 4,
            downloadDir = "/downloads/movies",
            uploadRatio = 6.04,
            downloadedEver = 26700000000L,
            uploadedEver = 160000000000L,
            secondsSeeding = 186400,
            trackerName = "Google",
            trackers = listOf(Tracker(announce = "https://www.google.com/announce")),
            trackerStats = listOf(TrackerStats(announce = "https://www.google.com/announce", seederCount = 42, leecherCount = 5, downloadCount = 120, hasScraped = true))
        )
    }

    val effectiveTorrent = if (torrent == null && androidx.compose.ui.platform.LocalInspectionMode.current) {
        samplePreviewTorrent
    } else {
        torrent
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val loc = IntArray(2)
                infoView.getLocationOnScreen(loc)
                val offsetInWindow = coordinates.positionInWindow()
                infoViewLocation = Offset(
                    x = loc[0].toFloat() + offsetInWindow.x,
                    y = loc[1].toFloat() + offsetInWindow.y,
                )
            }
            .drawWithContent {
                infoBackdropLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawContent()
            }
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 56.dp, bottom = 8.dp),
    ) {
        if (effectiveTorrent == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accentColor)
            }
        } else {
            val torrent = effectiveTorrent
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 卡片 1: 种子名称与 1:1 复刻原版的递归树状文件结构
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = FormatUtils.formatTorrentTitle(torrent.name),
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                lineBreak = LineBreak.Paragraph
                            ),
                            modifier = Modifier.clickable {
                                isFileTreeExpanded = !isFileTreeExpanded
                            }
                        )

                        if (isFileTreeExpanded && !torrent.files.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            TorrentFileTreeView(
                                files = torrent.files,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor
                            )
                        }
                    }
                }

                // 2. 卡片 2: 2x3 网格统计卡片
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            InfoGridCell("总大小", FormatUtils.formatSize(torrent.totalSize), secondaryTextColor, primaryTextColor, Modifier.weight(1f))
                            InfoGridCell("分享率", String.format(Locale.US, "%.2f", torrent.uploadRatio), secondaryTextColor, primaryTextColor, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            val percent = (torrent.percentDone * 100).toInt()
                            InfoGridCell("已下载", "${FormatUtils.formatSize(torrent.downloadedEver)} ($percent%)", secondaryTextColor, Color(0xFF2196F3), Modifier.weight(1f))
                            InfoGridCell("已上传", FormatUtils.formatSize(torrent.uploadedEver), secondaryTextColor, Color(0xFF43A047), Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            val trackerStat = torrent.trackerStats?.firstOrNull { (it.hasScraped) || (it.seederCount > 0) || (it.leecherCount > 0) || (it.downloadCount > 0) }
                                ?: torrent.trackerStats?.firstOrNull()

                            val seeders = trackerStat?.seederCount ?: -1
                            val leechers = trackerStat?.leecherCount ?: -1
                            val completed = trackerStat?.downloadCount ?: -1

                            val swarmText = if ((seeders >= 0) && (leechers >= 0)) {
                                if (completed >= 0) "$seeders / $leechers / $completed" else "$seeders / $leechers / --"
                            } else {
                                "-- / -- / --"
                            }

                            val seedSec = torrent.secondsSeeding
                            val days = seedSec / (24 * 3600)
                            val hrs = (seedSec % (24 * 3600)) / 3600
                            val mins = (seedSec % 3600) / 60
                            val seedTimeText = if (seedSec > 0) "${days}d ${hrs}h ${mins}m" else "--"

                            InfoGridCell("做种 / 下载 / 完成", swarmText, secondaryTextColor, primaryTextColor, Modifier.weight(1f))
                            InfoGridCell("做种时间", seedTimeText, secondaryTextColor, primaryTextColor, Modifier.weight(1f))
                        }
                    }
                }

                // 3. 卡片 3: 保存位置与 Tracker 列表卡片
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // 下载目录行
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("下载目录", fontSize = 12.sp, color = secondaryTextColor, modifier = Modifier.width(90.dp))
                            Text(
                                text = torrent.downloadDir ?: "",
                                fontSize = 13.sp,
                                color = primaryTextColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { copyToClipboard("downloadDir", torrent.downloadDir ?: "") }
                            )
                            IconButton(
                                onClick = { showSetLocationDialogState = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_file_open),
                                    contentDescription = "修改保存位置",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Tracker 行 (仅显示有效域名，自动过滤假/本地伪 Tracker)
                        val displayTrackerDomain = remember(torrent) {
                            val realTrackers = torrent.trackers?.mapNotNull {
                                if (it.announce.isNotBlank() && !it.announce.startsWith("**") && !it.announce.contains("[DHT]") && !it.announce.contains("[PeX]") && !it.announce.contains("[LSD]")) it.announce else null
                            } ?: emptyList()

                            val realStatsTrackers = torrent.trackerStats?.mapNotNull {
                                if (it.announce.isNotBlank() && !it.announce.startsWith("**") && !it.announce.contains("[DHT]") && !it.announce.contains("[PeX]") && !it.announce.contains("[LSD]")) it.announce else null
                            } ?: emptyList()

                            val firstAnnounce = realTrackers.firstOrNull() ?: realStatsTrackers.firstOrNull() ?: ""
                            if (firstAnnounce.isNotEmpty()) {
                                try {
                                    val uri = java.net.URI(firstAnnounce)
                                    uri.host ?: firstAnnounce.substringAfter("://").substringBefore("/").substringBefore(":")
                                } catch (_: Exception) {
                                    firstAnnounce.substringAfter("://").substringBefore("/").substringBefore(":")
                                }
                            } else {
                                "无"
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tracker", fontSize = 12.sp, color = secondaryTextColor, modifier = Modifier.width(90.dp))
                            Text(
                                text = displayTrackerDomain,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { copyToClipboard("tracker", displayTrackerDomain) }
                            )
                            IconButton(
                                onClick = { showEditTrackersDialogState = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_edit),
                                    contentDescription = "编辑 Tracker",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // H&R 考核行 (点击调起 H&R 考核修改弹窗)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSetHrDialogState = true },
                        ) {
                            HrStatusInfoRow(torrent = torrent, secondaryTextColor = secondaryTextColor, primaryTextColor = primaryTextColor)
                        }

                        // 错误信息行 (如果有错误)
                        if (torrent.error != 0 && torrent.errorString.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("错误信息", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935), modifier = Modifier.width(90.dp))
                                Text(
                                    text = torrent.errorString,
                                    fontSize = 13.sp,
                                    color = Color(0xFFE53935),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 4. 卡片 4: 预计剩余、H&R 考核与日期状态卡片
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = cardBgColor,
                    border = BorderStroke(1.dp, cardBorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val etaText = when {
                            torrent.percentDone >= 1.0 -> "已完成"
                            torrent.eta == null || torrent.eta < 0L -> "未知"
                            torrent.eta == 0L -> "已完成"
                            else -> {
                                val etaSec = torrent.eta
                                val days = etaSec / (24 * 3600)
                                val hrs = (etaSec % (24 * 3600)) / 3600
                                val mins = (etaSec % 3600) / 60
                                val secs = etaSec % 60
                                when {
                                    days > 0 -> "${days}d ${hrs}h ${mins}m"
                                    hrs > 0 -> "${hrs}h ${mins}m ${secs}s"
                                    mins > 0 -> "${mins}m ${secs}s"
                                    else -> "${secs}s"
                                }
                            }
                        }

                        InfoRow("预计剩余", etaText, secondaryTextColor, if (torrent.percentDone < 1.0 && (torrent.eta ?: -1L) > 0L) Color(0xFF2196F3) else primaryTextColor)
                        InfoRow("添加日期", FormatUtils.formatDate(torrent.addedDate), secondaryTextColor, primaryTextColor)
                        InfoRow("完成日期", if (torrent.doneDate > 0) FormatUtils.formatDate(torrent.doneDate) else "未完成", secondaryTextColor, primaryTextColor)
                        InfoRow("最后活动", if (torrent.activityDate > 0) FormatUtils.formatDate(torrent.activityDate) else "未活动", secondaryTextColor, primaryTextColor)
                    }
                }
            }
        }

        // 1. 重命名统一弹窗
        if (showRenameDialogState && torrent != null) {
            RenameTorrentDialog(
                targetTorrent = torrent,
                rpcUrl = rpcUrl,
                user = user,
                pass = pass,
                backdropLayer = infoBackdropLayer,
                boxPositionInRoot = infoViewLocation,
                onDismiss = { showRenameDialogState = false },
                onSuccess = onRefresh,
            )
        }

        // 2. 设置保存位置统一弹窗
        if (showSetLocationDialogState && torrent != null) {
            SetLocationDialog(
                torrents = listOf(torrent),
                rpcUrl = rpcUrl,
                user = user,
                pass = pass,
                backdropLayer = infoBackdropLayer,
                boxPositionInRoot = infoViewLocation,
                onDismiss = { showSetLocationDialogState = false },
                onSuccess = onRefresh,
            )
        }

        // 3. 编辑 Tracker 统一弹窗
        if (showEditTrackersDialogState && torrent != null) {
            EditTrackersDialog(
                torrent = torrent,
                rpcUrl = rpcUrl,
                user = user,
                pass = pass,
                backdropLayer = infoBackdropLayer,
                boxPositionInRoot = infoViewLocation,
                onDismiss = { showEditTrackersDialogState = false },
                onSuccess = onRefresh,
            )
        }

        // 4. 设置 H&R 考核统一弹窗
        if (showSetHrDialogState && torrent != null) {
            SetHrDialog(
                torrents = listOf(torrent),
                rpcUrl = rpcUrl,
                user = user,
                pass = pass,
                backdropLayer = infoBackdropLayer,
                boxPositionInRoot = infoViewLocation,
                onDismiss = { showSetHrDialogState = false },
                onSuccess = onRefresh,
            )
        }
    }
}

/**
 * 1:1 复刻原版 TorrentFileAdapter 的多级递归树状节点结构组件
 */
data class FileNodeItem(
    val name: String,
    val isFolder: Boolean,
    val level: Int,
    val length: Long = 0,
    val bytesCompleted: Long = 0,
    val children: MutableList<FileNodeItem> = mutableListOf()
)

fun buildFileTree(files: List<TorrentFile>): List<FileNodeItem> {
    val root = FileNodeItem("", true, -1)
    for (file in files) {
        val parts = file.name.split("/")
        var currentNode = root
        for (i in parts.indices) {
            val part = parts[i]
            val isLast = i == parts.size - 1
            var child = currentNode.children.find { it.name == part }
            if (child == null) {
                child = FileNodeItem(
                    name = part,
                    isFolder = !isLast,
                    level = i,
                    length = if (isLast) file.length else 0,
                    bytesCompleted = if (isLast) file.bytesCompleted else 0
                )
                currentNode.children.add(child)
            }
            currentNode = child
        }
    }
    return root.children
}

private data class NodeLayoutItem(
    val name: String,
    val topY: Float,
    val bottomY: Float
)

@Composable
fun TorrentFileTreeView(
    files: List<TorrentFile>,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    val rootNodes = remember(files) { buildFileTree(files) }
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }

    var hoveredFileName by remember { mutableStateOf<String?>(null) }
    val nodeLayoutMap = remember { mutableStateMapOf<String, NodeLayoutItem>() }
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    fun getPath(node: FileNodeItem, parentPath: String = ""): String {
        return if (parentPath.isEmpty()) node.name else "$parentPath/${node.name}"
    }

    @Composable
    fun RenderNodes(nodes: List<FileNodeItem>, parentPath: String = "") {
        val sortedNodes = remember(nodes) { nodes.sortedBy { !it.isFolder } }
        Column(modifier = Modifier.fillMaxWidth()) {
            for (node in sortedNodes) {
                val currentPath = getPath(node, parentPath)
                val isExpanded = expandedPaths.contains(currentPath)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            val topY = coordinates.positionInParent().y
                            val height = coordinates.size.height.toFloat()
                            nodeLayoutMap[currentPath] = NodeLayoutItem(node.name, topY, topY + height)
                        }
                        .clickable(enabled = node.isFolder) {
                            expandedPaths = if (isExpanded) {
                                expandedPaths - currentPath
                            } else {
                                expandedPaths + currentPath
                            }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 缩进 (每级 16dp)
                    if (node.level > 0) {
                        Spacer(modifier = Modifier.width((node.level * 16).dp))
                    }

                    // 2. 文件夹展开/折叠 Icon 箭头
                    if (node.isFolder) {
                        Icon(
                            painter = painterResource(id = if (isExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_right),
                            contentDescription = if (isExpanded) "折叠" else "展开",
                            tint = secondaryTextColor,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    // 3. 文件夹 / 文件名称（单行截断显示）
                    Text(
                        text = node.name,
                        fontSize = 13.sp,
                        fontWeight = if (node.isFolder) FontWeight.Bold else FontWeight.Normal,
                        color = primaryTextColor,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 4. 文件大小与完成百分比（已 100% 完成的文件省略显示 100.0%，未完成的下载中文件显示具体百分比如 20.2%）
                    if (!node.isFolder) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = FormatUtils.formatSize(node.length),
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                        val progress = if (node.length > 0) (node.bytesCompleted.toDouble() / node.length * 100) else 0.0
                        if (progress < 99.95) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = String.format(Locale.US, "%.1f%%", progress),
                                fontSize = 12.sp,
                                color = secondaryTextColor
                            )
                        }
                    }
                }

                // 递归渲染子文件夹
                if (node.isFolder && isExpanded && node.children.isNotEmpty()) {
                    RenderNodes(node.children, currentPath)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val target = nodeLayoutMap.values.find { offset.y >= it.topY && offset.y <= it.bottomY }
                        if (target != null) {
                            hoveredFileName = target.name
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val target = nodeLayoutMap.values.find { change.position.y >= it.topY && change.position.y <= it.bottomY }
                        if (target != null && target.name != hoveredFileName) {
                            hoveredFileName = target.name
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        hoveredFileName = null
                    },
                    onDragCancel = {
                        hoveredFileName = null
                    }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            RenderNodes(rootNodes)
        }

        // 5. 长按/滑动触发的多行完整名称悬浮栏 (名称悬浮栏)
        androidx.compose.animation.AnimatedVisibility(
            visible = hoveredFileName != null,
            enter = scaleIn(animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)) + fadeIn(),
            exit = scaleOut(animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            hoveredFileName?.let { fullName ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) Color(0xF21F2A38) else Color(0xF2FFFFFF),
                    border = BorderStroke(1.dp, if (isDark) Color(0x661D88E3) else Color(0x6600B0FF)),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_file_open),
                            contentDescription = "全文件名",
                            tint = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF),
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 6.dp)
                        )
                        Text(
                            text = fullName,
                            style = TextStyle(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor,
                                lineHeight = 18.sp
                            ),
                            softWrap = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoGridCell(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(6.dp)) {
        Text(text = label, fontSize = 11.sp, color = labelColor)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = labelColor, modifier = Modifier.width(90.dp))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor, modifier = Modifier.weight(1f))
    }
}

@Composable
fun HrStatusInfoRow(
    torrent: Torrent,
    secondaryTextColor: Color,
    primaryTextColor: Color
) {
    val hrLabel = torrent.labels?.find { it.startsWith("HR:") }
    val hours = if (hrLabel != null) (hrLabel.substringAfter("HR:").toDoubleOrNull() ?: 0.0) else 0.0

    val (statusText, isCompleted) = remember(torrent) {
        if (hours <= 0.0) {
            "无考核" to false
        } else if (torrent.percentDone < 1.0) {
            val d = (hours / 24.0)
            val dStr = if (d == d.toInt().toDouble()) d.toInt().toString() else String.format(Locale.US, "%.1f", d)
            "未完成 (需做种 $dStr 天)" to false
        } else {
            val doneDateMs = torrent.doneDate * 1000L
            val currentTime = System.currentTimeMillis()
            val totalRequiredMs = (hours * 3600 * 1000L).toLong()
            val elapsedMs = currentTime - doneDateMs
            val remainingMs = totalRequiredMs - elapsedMs
            val bufferMs = 30 * 60 * 1000L

            if (remainingMs <= -bufferMs) {
                "已满足 (考核通过)" to true
            } else if (remainingMs > 0) {
                val totalMins = remainingMs / (60 * 1000L)
                val hrs = totalMins / 60
                val mins = totalMins % 60
                val text = if (hrs >= 24) {
                    val days = hrs / 24
                    val h = hrs % 24
                    "剩余 ${days}天 ${h}小时"
                } else {
                    "剩余 ${hrs}小时 ${mins}分钟"
                }
                text to false
            } else {
                "缓冲期中" to false
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("H&R 考核", fontSize = 12.sp, color = secondaryTextColor, modifier = Modifier.width(90.dp))
        Text(
            text = statusText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isCompleted) Color(0xFF43A047) else (if (hours > 0.0) Color(0xFFFF9800) else primaryTextColor),
            modifier = Modifier.weight(1f)
        )
        if (isCompleted) {
            Icon(
                painter = painterResource(id = R.drawable.ic_done),
                contentDescription = "考核通过",
                tint = Color(0xFF43A047),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "信息页概览", showBackground = true)
@Composable
fun TorrentInfoScreenPreview() {
    MaterialTheme {
        TorrentInfoScreen(
            torrent = Torrent(
                id = 1,
                name = "Jumanji.The.Next.Level.2026.2160p.HQ.WEB-DL.mkv",
                totalSize = 44238000000L,
                uploadRatio = 6.04,
                downloadDir = "/downloads/movies",
                trackerName = "Google",
            ),
            rpcUrl = "",
            user = "",
            pass = "",
            onRefresh = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "种子文件树状节点视图", showBackground = true)
@Composable
fun TorrentFileTreeView_Preview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            TorrentFileTreeView(
                files = listOf(
                    TorrentFile(name = "Movies/Jumanji.mkv", length = 44000000000L, bytesCompleted = 22000000000L),
                    TorrentFile(name = "Movies/Subtitles/Chs.srt", length = 100000L, bytesCompleted = 100000L),
                ),
                primaryTextColor = Color(0xFF2D3436),
                secondaryTextColor = Color(0xFF636E72),
            )
        }
    }
}
