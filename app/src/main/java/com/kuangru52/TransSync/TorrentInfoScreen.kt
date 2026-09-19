package com.kuangru52.transsync

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

/**
 * 100% 纯 Compose 版本的 TorrentInfoScreen 详情信息界面：
 * - 复刻原版递归树状节点结构 (TorrentFileTreeView)，多级目录缩进、文件夹展开/折叠箭头与百分比精准对齐
 * - 各个元素位置、字号、卡片间距、内边距与功能与 XML 1:1 绝对一致
 * - 点击名称展开/收起文件树，长按复制文本
 * - 点击路径或 Tracker 编辑按钮 调起 Compose 100% 实心毛玻璃弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentInfoScreen(
    torrent: Torrent?,
    rpcUrl: String,
    user: String,
    pass: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    var isFileTreeExpanded by remember { mutableStateOf(false) }

    // 弹窗状态管理
    var showSetLocationDialogState by remember { mutableStateOf(false) }
    var showEditTrackersDialogState by remember { mutableStateOf(false) }

    val cardBgColor = if (isDark) Color(0xFF1A232E) else Color.White
    val cardBorderColor = if (isDark) Color(0x26FFFFFF) else Color(0xFFE0E0E0)
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (torrent == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accentColor)
            }
        } else {
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

                        // H&R 考核行
                        HrStatusInfoRow(torrent = torrent, secondaryTextColor = secondaryTextColor, primaryTextColor = primaryTextColor)

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

        // 1. 设置保存位置 Compose 液态玻璃 100% 实心毛玻璃弹窗
        if (showSetLocationDialogState && torrent != null) {
            var locationInput by remember(torrent) { mutableStateOf(torrent.downloadDir ?: "") }
            var moveData by remember { mutableStateOf(true) }
            var freeSpaceText by remember { mutableStateOf("") }

            val allDirs = remember(ServerManager.serversVersion, torrent) { DownloadDirManager.getAllDirs(context, listOf(torrent)) }

            LaunchedEffect(locationInput) {
                val path = locationInput.trim()
                if (path.isNotEmpty() && rpcUrl.isNotEmpty()) {
                    val (effUrl, effUser, effPass) = DialogUtils.getEffectiveCredentials(context, rpcUrl, user, pass)
                    val service = TransmissionClient.getService(effUrl, effUser, effPass)
                    service.rpc(effUrl, null, RpcRequest("free-space", mapOf("path" to path)))
                        .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                                if (response.isSuccessful) {
                                    val size = (response.body()?.arguments?.get("size-bytes") as? Double)?.toLong() ?: 0L
                                    freeSpaceText = context.getString(R.string.free_space_label, FormatUtils.formatSize(size))
                                }
                            }
                            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
                        })
                } else {
                    freeSpaceText = ""
                }
            }

            LiquidGlassDialog(
                onDismissRequest = { showSetLocationDialogState = false },
                backdropLayer = null,
                title = "设置保存位置",
                confirmButtonText = "确定",
                confirmButtonColor = Color(0xFF1D88E3),
                onConfirm = {
                    val loc = locationInput.trim()
                    showSetLocationDialogState = false
                    if (loc.isNotEmpty()) {
                        DialogUtils.performSetLocation(
                            context = context,
                            rpcUrl = rpcUrl,
                            user = user,
                            pass = pass,
                            torrentIds = listOf(torrent.id),
                            torrentHashes = listOf(torrent.hash),
                            newLocation = loc,
                            moveData = moveData
                        ) {
                            onRefresh()
                        }
                    }
                }
            ) {
                DirectoryDropdownTextField(
                    value = locationInput,
                    onValueChange = { locationInput = it },
                    allDirs = allDirs,
                    isDark = isDark
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { moveData = !moveData }
                    ) {
                        Checkbox(
                            checked = moveData,
                            onCheckedChange = { moveData = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF1D88E3)
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "移动数据",
                            fontSize = 14.sp,
                            color = if (isDark) Color.White else Color(0xFF2D3436)
                        )
                    }

                    if (freeSpaceText.isNotEmpty()) {
                        Text(
                            text = freeSpaceText,
                            fontSize = 14.sp,
                            color = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72)
                        )
                    }
                }
            }
        }

        // 2. 编辑 Tracker Compose 液态玻璃 100% 实心毛玻璃弹窗
        if (showEditTrackersDialogState && torrent != null) {
            val oldTrackers = (torrent.trackers ?: emptyList()).filter {
                it.announce.isNotBlank() && !it.announce.startsWith("**") && !it.announce.contains("[DHT]") && !it.announce.contains("[PeX]") && !it.announce.contains("[LSD]")
            }
            val initialTrackerUrls = oldTrackers.joinToString("\n") { it.announce }
            var trackerInput by remember(initialTrackerUrls) { mutableStateOf(initialTrackerUrls) }
            val isSaveEnabled = trackerInput.isNotBlank() && trackerInput != initialTrackerUrls

            LiquidGlassDialog(
                onDismissRequest = { showEditTrackersDialogState = false },
                backdropLayer = null,
                title = "编辑 Tracker",
                confirmButtonText = "保存",
                confirmButtonColor = Color(0xFF1D88E3),
                isConfirmEnabled = isSaveEnabled,
                onConfirm = {
                    showEditTrackersDialogState = false
                    val newUrls = trackerInput.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    val oldUrls = oldTrackers.map { it.announce }
                    val toAdd = newUrls.filter { it !in oldUrls }
                    val toRemoveIds = oldTrackers.filter { it.announce !in newUrls }.map { it.id }

                    if (toAdd.isNotEmpty() || toRemoveIds.isNotEmpty()) {
                        val (effUrl, effUser, effPass) = DialogUtils.getEffectiveCredentials(context, rpcUrl, user, pass)
                        val activeServer = ServerManager.getActiveServer(context)

                        if (activeServer?.clientType == ServerConfig.CLIENT_QBITTORRENT) {
                            val qbitService = QBittorrentClient.getService(effUrl)
                            val doQbitUpdate = {
                                if (toAdd.isNotEmpty()) {
                                    val urlsStr = toAdd.joinToString("\n")
                                    qbitService.addTrackers(torrent.hash, urlsStr).enqueue(object : Callback<String> {
                                        override fun onResponse(call: Call<String>, response: Response<String>) {
                                            if (response.isSuccessful || response.code() == 200) {
                                                Toast.makeText(context, R.string.msg_tracker_updated, Toast.LENGTH_SHORT).show()
                                                onRefresh()
                                            }
                                        }
                                        override fun onFailure(call: Call<String>, t: Throwable) {}
                                    })
                                }
                            }

                            if (effUser.isNotEmpty() || effPass.isNotEmpty()) {
                                qbitService.login(effUser, effPass).enqueue(object : Callback<String> {
                                    override fun onResponse(call: Call<String>, response: Response<String>) { doQbitUpdate() }
                                    override fun onFailure(call: Call<String>, t: Throwable) { doQbitUpdate() }
                                })
                            } else {
                                doQbitUpdate()
                            }
                        } else {
                            val service = TransmissionClient.getService(effUrl, effUser, effPass)
                            val args = mutableMapOf<String, Any>("ids" to listOf(torrent.id))
                            if (toAdd.isNotEmpty()) args["trackerAdd"] = toAdd
                            if (toRemoveIds.isNotEmpty()) args["trackerRemove"] = toRemoveIds

                            service.rpc(effUrl, null, RpcRequest("torrent-set", args))
                                .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                                    override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                                        if (response.isSuccessful) {
                                            Toast.makeText(context, R.string.msg_tracker_updated, Toast.LENGTH_SHORT).show()
                                            onRefresh()
                                        }
                                    }
                                    override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
                                })
                        }
                    }
                }
            ) {
                OutlinedTextField(
                    value = trackerInput,
                    onValueChange = { trackerInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    shape = RoundedCornerShape(12.dp),
                    label = { Text("Tracker") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF),
                        unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                        focusedLabelColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF),
                        unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                        focusedTextColor = if (isDark) Color.White else Color(0xFF2D3436),
                        unfocusedTextColor = if (isDark) Color.White else Color(0xFF2D3436)
                    )
                )
            }
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

@Composable
fun TorrentFileTreeView(
    files: List<TorrentFile>,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    val rootNodes = remember(files) { buildFileTree(files) }
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }

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

                    // 3. 文件夹 / 文件名称
                    Text(
                        text = node.name,
                        fontSize = 13.sp,
                        fontWeight = if (node.isFolder) FontWeight.Bold else FontWeight.Normal,
                        color = primaryTextColor,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 4. 文件大小与完成百分比
                    if (!node.isFolder) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = FormatUtils.formatSize(node.length),
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val progress = if (node.length > 0) (node.bytesCompleted.toDouble() / node.length * 100) else 0.0
                        Text(
                            text = String.format(Locale.US, "%.1f%%", progress),
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }
                }

                // 递归渲染子文件夹
                if (node.isFolder && isExpanded && node.children.isNotEmpty()) {
                    RenderNodes(node.children, currentPath)
                }
            }
        }
    }

    RenderNodes(rootNodes)
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
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
            onRefresh = {}
        )
    }
}
