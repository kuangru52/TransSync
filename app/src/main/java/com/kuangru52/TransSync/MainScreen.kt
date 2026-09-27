package com.kuangru52.transsync

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * 100% 纯 Compose 版本的 MainScreen 服务器连接/登录管理主界面：
 * - 默认沉浸式 Bing 壁纸背景 (录制进 backdropLayer)
 * - 顶端应用 Logo，中间复用与添加服务器弹窗 1:1 完全一致的高端 3D 液态玻璃弹窗卡片登录布局
 * - 包含：客户端类型切换、备注+头像选择、地址、用户名、密码、测试连接按钮与保存/登录按钮
 * - 底部显示 TransSync v4.11 版本号
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    initialHost: String,
    initialUser: String,
    initialPass: String,
    isLoggingIn: Boolean,
    onLoginClick: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val isInspection = LocalInspectionMode.current

    var clientTypeInput by remember { mutableStateOf(ServerConfig.CLIENT_TRANSMISSION) }
    var aliasInput by remember { mutableStateOf("家中NAS") }
    var hostInput by remember { mutableStateOf(initialHost) }
    var userInput by remember { mutableStateOf(initialUser) }
    var passInput by remember { mutableStateOf(initialPass) }
    var avatarUriInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(value = false) }
    var isTestingConnection by remember { mutableStateOf(value = false) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                val inputStream = context.contentResolver.openInputStream(selectedUri)
                if (inputStream != null) {
                    val avatarsDir = java.io.File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
                    val destFile = java.io.File(avatarsDir, "avatar_${System.currentTimeMillis()}.png")
                    val outputStream = destFile.outputStream()
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    avatarUriInput = destFile.absolutePath
                    Toast.makeText(context, "图片图标设置成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "图片加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val graphicsContext = LocalGraphicsContext.current
    val backdropLayer = remember(isInspection) {
        if (!isInspection) {
            try {
                graphicsContext.createGraphicsLayer()
            } catch (_: Exception) { null }
        } else null
    }

    DisposableEffect(isInspection) {
        onDispose {
            if (backdropLayer != null) {
                try {
                    graphicsContext.releaseGraphicsLayer(backdropLayer)
                } catch (_: Exception) {}
            }
        }
    }

    val accentColor = if (isDark) Color(0xFF42A5F5) else Color(0xFF40C4FF)
    val primaryTextColor = if (isDark) Color.White else Color(0xFF2D3436)
    val secondaryTextColor = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72)

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "4.11"
        } catch (_: Exception) {
            "4.11"
        }
    }

    val performTestConnection = {
        val rawUrl = hostInput.trim()
        val u = userInput.trim()
        val p = passInput.trim()
        if (rawUrl.isEmpty()) {
            Toast.makeText(context, "请输入服务器地址", Toast.LENGTH_SHORT).show()
        } else {
            val formattedUrl = formatServerUrl(rawUrl, clientTypeInput)
            isTestingConnection = true

            if (clientTypeInput == ServerConfig.CLIENT_QBITTORRENT) {
                val qbitService = QBittorrentClient.getService(formattedUrl)
                val performTransferCheck = {
                    qbitService.getTransferInfo()
                        .enqueue(
                            object : Callback<QbitTransferInfo> {
                                override fun onResponse(call: Call<QbitTransferInfo>, response: Response<QbitTransferInfo>) {
                                    isTestingConnection = false
                                    if ((response.isSuccessful) || (response.code() == 200)) {
                                        Toast.makeText(context, "连接成功！qBittorrent Web API 握手正常", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "连接失败，HTTP 响应码: ${response.code()}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                override fun onFailure(call: Call<QbitTransferInfo>, t: Throwable) {
                                    isTestingConnection = false
                                    Toast.makeText(context, "连接失败: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                }

                if (u.isNotEmpty() || p.isNotEmpty()) {
                    qbitService.login(u, p).enqueue(
                        object : Callback<String> {
                            override fun onResponse(call: Call<String>, response: Response<String>) {
                                if ((response.isSuccessful) || (response.code() == 200)) {
                                    performTransferCheck()
                                } else {
                                    isTestingConnection = false
                                    Toast.makeText(context, "qBittorrent 登录失败 (HTTP ${response.code()})，请检查账号密码", Toast.LENGTH_SHORT).show()
                                }
                            }
                            override fun onFailure(call: Call<String>, t: Throwable) {
                                isTestingConnection = false
                                Toast.makeText(context, "连接失败: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                } else {
                    performTransferCheck()
                }
            } else {
                val service = TransmissionClient.getService(formattedUrl, u, p)
                service.rpc(formattedUrl, null, RpcRequest("session-get"))
                    .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                    override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                        isTestingConnection = false
                        if ((response.isSuccessful) || (response.code() == 409)) {
                            Toast.makeText(context, "连接成功！Transmission 握手正常", Toast.LENGTH_SHORT).show()
                        } else if (response.code() == 401) {
                            Toast.makeText(context, "连接失败：认证失败 (401)，请检查用户名和密码", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "连接失败，HTTP 响应码: ${response.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                        isTestingConnection = false
                        Toast.makeText(context, "连接失败: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (!isInspection) {
                    Modifier.drawWithContent {
                        if (backdropLayer != null) {
                            try {
                                backdropLayer.record {
                                    this@drawWithContent.drawContent()
                                }
                            } catch (_: Exception) {}
                        }
                        drawContent()
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 1. 沉浸式 Bing 壁纸背景
        WallpaperBackground()

        // 2. 登录主内容区（1:1 复刻添加服务器弹窗布局与 3D 液态玻璃卡片质感）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo 图标 (置于卡片外侧最上方)
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "应用 Logo",
                modifier = Modifier.size(80.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            LiquidGlassDialog(
                onDismissRequest = {},
                backdropLayer = backdropLayer,
                title = "",
                confirmButtonText = stringResource(R.string.btn_save),
                confirmButtonColor = accentColor,
                isConfirmEnabled = !isLoggingIn && !isTestingConnection,
                onConfirm = {
                    onLoginClick(hostInput, userInput, passInput, clientTypeInput)
                },
                bottomLeftContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLoggingIn || isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = accentColor,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Button(
                            onClick = { performTestConnection() },
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF131B24) else Color(0xFFF0F2F5)),
                            modifier = Modifier.height(36.dp),
                            enabled = !isTestingConnection && !isLoggingIn,
                        ) {
                            Text(
                                text = if (isTestingConnection) stringResource(R.string.btn_testing) else stringResource(R.string.btn_test_connection),
                                fontSize = 12.5.sp,
                                color = primaryTextColor,
                            )
                        }
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 客户端类型选择 (Transmission / qBittorrent)
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (isDark) Color(0xFF131B24) else Color(0xFFF0F2F5),
                        border = BorderStroke(1.dp, if (isDark) Color(0x22FFFFFF) else Color(0xFFE0E0E0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { clientTypeInput = ServerConfig.CLIENT_TRANSMISSION },
                                shape = RoundedCornerShape(100.dp),
                                color = if (clientTypeInput == ServerConfig.CLIENT_TRANSMISSION) accentColor else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Transmission",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (clientTypeInput == ServerConfig.CLIENT_TRANSMISSION) Color.White else primaryTextColor
                                    )
                                }
                            }

                            Surface(
                                onClick = { clientTypeInput = ServerConfig.CLIENT_QBITTORRENT },
                                shape = RoundedCornerShape(100.dp),
                                color = if (clientTypeInput == ServerConfig.CLIENT_QBITTORRENT) accentColor else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "qBittorrent",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (clientTypeInput == ServerConfig.CLIENT_QBITTORRENT) Color.White else primaryTextColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. 备注 + 头像选择按键 Row (1:1 复制自添加服务器弹窗)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = aliasInput,
                            onValueChange = { aliasInput = it },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            label = { Text(stringResource(R.string.label_alias)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                                focusedLabelColor = accentColor,
                                unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                                focusedTextColor = primaryTextColor,
                                unfocusedTextColor = primaryTextColor
                            )
                        )

                        val avatarBitmap = remember(avatarUriInput) {
                            if (avatarUriInput.isNotBlank()) {
                                try {
                                    val file = java.io.File(avatarUriInput)
                                    if (file.exists()) {
                                        android.graphics.BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                                    } else null
                                } catch (_: Exception) { null }
                            } else null
                        }

                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isDark) Color(0xFF263445) else Color(0xFFF0F2F5),
                                border = BorderStroke(1.5.dp, accentColor),
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .combinedClickable(
                                        onClick = { avatarPickerLauncher.launch("image/*") },
                                        onLongClick = {
                                            if (avatarUriInput.isNotBlank()) {
                                                avatarUriInput = ""
                                                Toast.makeText(context, "图片图标已重置", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (avatarBitmap != null) {
                                        Image(
                                            bitmap = avatarBitmap,
                                            contentDescription = "服务器图片",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_image),
                                            contentDescription = "选择照片",
                                            tint = accentColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. 服务器 RPC 地址输入框
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_address)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                            focusedTextColor = primaryTextColor,
                            unfocusedTextColor = primaryTextColor
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. 用户名输入框
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_username)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                            focusedTextColor = primaryTextColor,
                            unfocusedTextColor = primaryTextColor
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. 密码输入框
                    OutlinedTextField(
                        value = passInput,
                        onValueChange = { passInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_password)) },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    painter = painterResource(id = if (isPasswordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off),
                                    contentDescription = "切换密码显示",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                onLoginClick(hostInput, userInput, passInput, clientTypeInput)
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                            focusedTextColor = primaryTextColor,
                            unfocusedTextColor = primaryTextColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 底部显示应用名和版本号
            Text(
                text = "TransSync v$versionName",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72),
            )
        }
    }
}

private fun formatServerUrl(inputUrl: String, clientType: String): String {
    var url = inputUrl.trim()
    if ((!url.startsWith("http://")) && (!url.startsWith("https://"))) {
        url = if (url.startsWith("192.168.") || url.startsWith("10.") || url.startsWith("172.") || url.startsWith("127.0.0.1") || url.startsWith("localhost")) {
            "http://$url"
        } else {
            "https://$url"
        }
    }
    return if (clientType == ServerConfig.CLIENT_TRANSMISSION) {
        if (url.endsWith("/transmission/rpc")) url else url.removeSuffix("/") + "/transmission/rpc"
    } else {
        url.removeSuffix("/")
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "登录页 - 浅色模式", showBackground = true)
@Composable
fun MainScreen_Light_Preview() {
    MaterialTheme {
        MainScreen(
            initialHost = "https://192.168.1.100:9091",
            initialUser = "admin",
            initialPass = "password",
            isLoggingIn = false,
            onLoginClick = { _, _, _, _ -> },
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "登录页 - 登录中状态", showBackground = true)
@Composable
fun MainScreen_LoggingIn_Preview() {
    MaterialTheme {
        MainScreen(
            initialHost = "https://192.168.1.100:9091",
            initialUser = "admin",
            initialPass = "password",
            isLoggingIn = true,
            onLoginClick = { _, _, _, _ -> },
        )
    }
}
