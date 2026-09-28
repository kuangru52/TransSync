package com.kuangru52.transsync

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * 全应用通用对话框组件库 (DialogComponents.kt)
 *
 * 【作用与功能】：
 * 集中管理全应用统一风格的 3D 液态玻璃交互弹窗组件，包含以下控件与功能：
 * 1. [RenameTorrentDialog]：种子重命名弹窗，包含单行 OutlinedTextField 输入框与确认/取消按钮，支持 RPC 异步同步；
 * 2. [SetLocationDialog]：修改下载保存位置弹窗，集成 DirectoryDropdownTextField 历史路径下拉选择器与数据移动复选框；
 * 3. [SetHrDialog]：H&R 考核天数设置弹窗，集成天数滑动选择器与手动输入框；
 * 4. [EditTrackersDialog]：Tracker 服务器编辑弹窗，包含多行文本输入框与实时更新控制。
 */
@Composable
fun RenameTorrentDialog(
    targetTorrent: Torrent,
    rpcUrl: String,
    user: String,
    pass: String,
    backdropLayer: GraphicsLayer? = null,
    boxPositionInRoot: Offset = Offset.Zero,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    var newNameInput by remember(targetTorrent) { mutableStateOf(targetTorrent.name) }

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        backdropLayer = backdropLayer,
        boxPositionInRoot = boxPositionInRoot,
        title = stringResource(R.string.dialog_rename_title),
        confirmButtonText = stringResource(R.string.btn_confirm),
        confirmButtonColor = Color(0xFF1D88E3),
        isConfirmEnabled = (newNameInput.isNotBlank() && newNameInput != targetTorrent.name),
        onConfirm = {
            val nameToSave = newNameInput.trim()
            onDismiss()
            if (nameToSave.isNotEmpty()) {
                DialogUtils.performRename(
                    context = context,
                    rpcUrl = rpcUrl,
                    user = user,
                    pass = pass,
                    torrentId = targetTorrent.id,
                    torrentHash = targetTorrent.hash,
                    currentName = targetTorrent.name,
                    newName = nameToSave,
                    onSuccess = onSuccess,
                )
            }
        },
    ) {
        OutlinedTextField(
            value = newNameInput,
            onValueChange = { newNameInput = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            label = { Text(stringResource(R.string.hint_new_name)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF),
                unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                focusedLabelColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF),
                unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                focusedTextColor = if (isDark) Color.White else Color(0xFF2D3436),
                unfocusedTextColor = if (isDark) Color.White else Color(0xFF2D3436),
            ),
        )
    }
}

/**
 * 全应用统一设置保存位置弹窗
 */
@Composable
fun SetLocationDialog(
    torrents: List<Torrent>,
    rpcUrl: String,
    user: String,
    pass: String,
    backdropLayer: GraphicsLayer? = null,
    boxPositionInRoot: Offset = Offset.Zero,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    var locationInput by remember(torrents) { mutableStateOf(torrents.firstOrNull()?.downloadDir ?: "") }
    var moveData by remember { mutableStateOf(value = true) }
    var freeSpaceText by remember { mutableStateOf("") }

    val allDirs = remember(ServerManager.serversVersion, torrents) { DownloadDirManager.getAllDirs(context, torrents) }

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
        onDismissRequest = onDismiss,
        backdropLayer = backdropLayer,
        boxPositionInRoot = boxPositionInRoot,
        title = stringResource(R.string.dialog_set_location_title),
        confirmButtonText = stringResource(R.string.btn_confirm),
        confirmButtonColor = Color(0xFF1D88E3),
        onConfirm = {
            val loc = locationInput.trim()
            onDismiss()
            if (loc.isNotEmpty()) {
                DialogUtils.performSetLocation(
                    context = context,
                    rpcUrl = rpcUrl,
                    user = user,
                    pass = pass,
                    torrentIds = torrents.map { it.id },
                    torrentHashes = torrents.map { it.hash },
                    newLocation = loc,
                    moveData = moveData,
                    onSuccess = onSuccess,
                )
            }
        },
    ) {
        DirectoryDropdownTextField(
            value = locationInput,
            onValueChange = { locationInput = it },
            allDirs = allDirs,
            isDark = isDark,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { moveData = !moveData },
            ) {
                Checkbox(
                    checked = moveData,
                    onCheckedChange = { moveData = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF1D88E3),
                    ),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.cb_move_data),
                    fontSize = 14.sp,
                    color = if (isDark) Color.White else Color(0xFF2D3436),
                )
            }

            if (freeSpaceText.isNotEmpty()) {
                Text(
                    text = freeSpaceText,
                    fontSize = 14.sp,
                    color = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72),
                )
            }
        }
    }
}

/**
 * 全应用统一设置 H&R 考核要求弹窗
 */
@Composable
fun SetHrDialog(
    torrents: List<Torrent>,
    rpcUrl: String,
    user: String,
    pass: String,
    backdropLayer: GraphicsLayer? = null,
    boxPositionInRoot: Offset = Offset.Zero,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val initialHrDays = remember(torrents) {
        val firstLabel = torrents.firstOrNull()?.labels?.find { it.startsWith("HR:") }
        val days = firstLabel?.substringAfter("HR:")?.toDoubleOrNull()?.div(24.0) ?: 0.0
        if (days == days.toInt().toDouble()) days.toInt().toString() else days.toString()
    }

    var hrDaysInput by remember(torrents) { mutableStateOf(initialHrDays) }

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        backdropLayer = backdropLayer,
        boxPositionInRoot = boxPositionInRoot,
        title = stringResource(R.string.menu_set_hr),
        confirmButtonText = stringResource(R.string.btn_confirm),
        confirmButtonColor = Color(0xFF1D88E3),
        onConfirm = {
            val days = hrDaysInput.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
            onDismiss()
            DialogUtils.performSetHr(
                context = context,
                rpcUrl = rpcUrl,
                user = user,
                pass = pass,
                torrentIds = torrents.map { it.id },
                torrentHashes = torrents.map { it.hash },
                days = days,
                onSuccess = onSuccess,
            )
        },
    ) {
        OutlinedTextField(
            value = hrDaysInput,
            onValueChange = { hrDaysInput = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            label = { Text(stringResource(R.string.label_hr)) },
            trailingIcon = {
                QuickHrSlidingSelector(
                    selectedDay = hrDaysInput,
                ) { hrDaysInput = it }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF),
                unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                focusedLabelColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF),
                unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                focusedTextColor = if (isDark) Color.White else Color(0xFF2D3436),
                unfocusedTextColor = if (isDark) Color.White else Color(0xFF2D3436),
            ),
        )
    }
}

/**
 * 全应用统一编辑/添加 Tracker 弹窗
 */
@Composable
fun EditTrackersDialog(
    torrent: Torrent,
    rpcUrl: String,
    user: String,
    pass: String,
    backdropLayer: GraphicsLayer? = null,
    boxPositionInRoot: Offset = Offset.Zero,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val oldTrackers = (torrent.trackers ?: emptyList()).filter {
        it.announce.isNotBlank() && !it.announce.startsWith("**") && !it.announce.contains("[DHT]") && !it.announce.contains("[PeX]") && !it.announce.contains("[LSD]")
    }
    val initialTrackerUrls = oldTrackers.joinToString("\n") { it.announce }
    var trackerInput by remember(initialTrackerUrls) { mutableStateOf(initialTrackerUrls) }
    val isSaveEnabled = trackerInput.isNotBlank() && trackerInput != initialTrackerUrls

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        backdropLayer = backdropLayer,
        boxPositionInRoot = boxPositionInRoot,
        title = stringResource(R.string.dialog_edit_tracker_title),
        confirmButtonText = stringResource(R.string.btn_save),
        confirmButtonColor = Color(0xFF1D88E3),
        isConfirmEnabled = isSaveEnabled,
        onConfirm = {
            onDismiss()
            val newUrls = trackerInput.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            val oldUrls = oldTrackers.map { it.announce }
            val toAdd = newUrls.filter { it !in oldUrls }
            val toRemoveIds = oldTrackers.asSequence().filter { it.announce !in newUrls }.map { it.id }.toList()

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
                                        onSuccess()
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
                                    onSuccess()
                                }
                            }

                            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
                        })
                }
            }
        },
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
                unfocusedTextColor = if (isDark) Color.White else Color(0xFF2D3436),
            ),
        )
    }
}

/**
 * 全应用统一版本更新提示 3D 液态玻璃弹窗 (AppUpdateDialog)
 */
@Composable
fun AppUpdateDialog(
    updateInfo: UpdateInfo,
    backdropLayer: GraphicsLayer? = null,
    boxPositionInRoot: Offset = Offset.Zero,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val primaryTextColor = if (isDark) Color.White else Color(0xFF2D3436)
    val accentColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF)

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        backdropLayer = backdropLayer,
        boxPositionInRoot = boxPositionInRoot,
        title = "发现新版本 ${updateInfo.latestVersion}",
        confirmButtonText = "立即更新",
        confirmButtonColor = accentColor,
        onConfirm = {
            onDismiss()
            UpdateCheckUtils.openReleasesPage(context, updateInfo.releaseUrl)
        },
        bottomLeftContent = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF131B24) else Color(0xFFF0F2F5)),
                modifier = Modifier.height(36.dp),
            ) {
                Text(
                    text = "稍后再说",
                    fontSize = 12.5.sp,
                    color = primaryTextColor,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "更新日志",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 180.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0x33131B24) else Color(0x33F0F2F5),
                border = BorderStroke(1.dp, if (isDark) Color(0x22FFFFFF) else Color(0x33000000)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        text = updateInfo.releaseNotes.ifBlank { "包含稳定性提升与界面体验优化。" },
                        style = TextStyle(
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = primaryTextColor,
                        ),
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun RenameTorrentDialogPreview() {
    MaterialTheme {
        RenameTorrentDialog(
            targetTorrent = Torrent(name = "Jumanji.The.Next.Level.2026.2160p.HQ.WEB-DL.mkv"),
            rpcUrl = "",
            user = "",
            pass = "",
            onDismiss = {},
            onSuccess = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun SetLocationDialogPreview() {
    MaterialTheme {
        SetLocationDialog(
            torrents = listOf(Torrent(downloadDir = "/downloads/movies")),
            rpcUrl = "",
            user = "",
            pass = "",
            onDismiss = {},
            onSuccess = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun SetHrDialogPreview() {
    MaterialTheme {
        SetHrDialog(
            torrents = listOf(Torrent()),
            rpcUrl = "",
            user = "",
            pass = "",
            onDismiss = {},
            onSuccess = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun EditTrackersDialogPreview() {
    MaterialTheme {
        EditTrackersDialog(
            torrent = Torrent(
                trackers = listOf(Tracker(announce = "https://www.google.com/announce")),
            ),
            rpcUrl = "",
            user = "",
            pass = "",
            onDismiss = {},
            onSuccess = {},
        )
    }
}
