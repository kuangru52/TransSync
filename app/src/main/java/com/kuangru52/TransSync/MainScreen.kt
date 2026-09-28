package com.kuangru52.transsync

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
 * 沉浸式服务器连接/登录 Compose 界面 (MainScreen.kt)
 *
 * 【作用与功能】：
 * 1:1 复刻添加服务器弹窗造型的登录主界面，包含以下 UI 控件与交互功能：
 * 1. 背景：渲染 Bing 每日精选沉浸式壁纸，并录制到 backdropLayer 供 3D 液态玻璃卡片透射；
 * 2. 顶部 Logo：登录卡片外侧上方放置应用品牌 Logo；
 * 3. 内联 3D 液态玻璃卡片：不触发 Window 覆盖，直接内联渲染带 3D AGSL 折射与高斯模糊的卡片，包含客户端类型切换器 (Transmission / qBittorrent)、备注与圆头像选择、地址、用户名、密码输入框；
 * 4. 底部动作区：左侧集成测试连接按钮与 Loading 进度环，右侧包含保存/登录按钮；
 * 5. 底部版本标识：显示应用名称与当前版本号 (TransSync v4.22)。
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
    val density = LocalDensity.current

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

    val dialogParams = remember(isDark) { SettingsManager.getDialogGlassParams(context, isDark) }
    val barBgColor = if (isDark) Color(0x99141D26) else Color(0xA6FFFFFF)
    val glassBorderBrush = Brush.linearGradient(
        colors = if (isDark) listOf(Color(0x55FFFFFF), Color(0x11FFFFFF)) else listOf(Color(0x88FFFFFF), Color(0x33FFFFFF)),
    )

    val cachedShader = remember {
        if (!isInspection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.graphics.RuntimeShader(LIQUID_GLASS_AGSL)
            } catch (_: Exception) { null }
        } else null
    }

    val accentColor = if (isDark) Color(0xFF42A5F5) else Color(0xFF40C4FF)
    val primaryTextColor = if (isDark) Color.White else Color(0xFF2D3436)
    val secondaryTextColor = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72)

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "4.22"
        } catch (_: Exception) {
            "4.22"
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
                    qbitService.getTransferInfo().enqueue(
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
                    .enqueue(
                        object : Callback<RpcResponse<Map<String, Any>>> {
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
                        },
                    )
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
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 1. 沉浸式 Bing 壁纸背景
        WallpaperBackground()

        // 2. 全布局页面 Column：上为 Logo (放置于顶部红圈区域)，中为 3D 液态玻璃内联登录卡片 (红框区域)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // A. 顶部 Logo 图标 (圆润 18dp 软圆角 + 柔和阴影)
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "应用 Logo",
                modifier = Modifier
                    .size(64.dp)
                    .shadow(6.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp)),
            )

            Spacer(modifier = Modifier.height(88.dp))

            // B. 中间 3D 液态玻璃登录卡片 (对应红框区域)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, glassBorderBrush, RoundedCornerShape(24.dp))
                    .shadow(12.dp, RoundedCornerShape(24.dp)),
            ) {
                // 1. 底层 3D AGSL 凸透镜折射与高斯模糊渲染层
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(24.dp))
                        .graphicsLayer {
                            clip = true
                            shape = RoundedCornerShape(24.dp)
                            if (!isInspection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (dialogParams.refraction != 0f) && (cachedShader != null)) {
                                    try {
                                        cachedShader.setFloatUniform("size", size.width, size.height)
                                        cachedShader.setFloatUniform("cornerRadius", with(density) { 24.dp.toPx() })
                                        cachedShader.setFloatUniform("refraction", with(density) { dialogParams.refraction.dp.toPx() })
                                        cachedShader.setFloatUniform("refractionHeight", with(density) { dialogParams.refractionHeight.dp.toPx() })
                                        cachedShader.setFloatUniform("saturationBoost", dialogParams.saturationBoost)
                                        cachedShader.setFloatUniform("contrast", dialogParams.contrast)
                                        cachedShader.setFloatUniform("whitePoint", dialogParams.whitePoint)

                                        val runtimeShaderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(cachedShader, "content")
                                        renderEffect = if (dialogParams.blurRadius > 0f) {
                                            val blurPx = with(density) { dialogParams.blurRadius.dp.toPx() }
                                            val blur = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP)
                                            android.graphics.RenderEffect.createChainEffect(runtimeShaderEffect, blur).asComposeRenderEffect()
                                        } else {
                                            runtimeShaderEffect.asComposeRenderEffect()
                                        }
                                    } catch (_: Exception) {
                                        if (dialogParams.blurRadius > 0f) {
                                            val blurPx = with(density) { dialogParams.blurRadius.dp.toPx() }
                                            renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                                        }
                                    }
                                } else if (dialogParams.blurRadius > 0f) {
                                    val blurPx = with(density) { dialogParams.blurRadius.dp.toPx() }
                                    renderEffect = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                                }
                            }
                        }
                        .drawBehind {
                            drawRect(color = barBgColor)
                            if (dialogParams.whitePoint > 0f) {
                                drawRect(color = Color.White.copy(alpha = (dialogParams.whitePoint * 0.3f).coerceIn(0f, 0.4f)))
                            }
                        },
                )

                // 2. 顶层 前景输入表单内容
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
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
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                onClick = { clientTypeInput = ServerConfig.CLIENT_TRANSMISSION },
                                shape = RoundedCornerShape(100.dp),
                                color = if (clientTypeInput == ServerConfig.CLIENT_TRANSMISSION) accentColor else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Transmission",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (clientTypeInput == ServerConfig.CLIENT_TRANSMISSION) Color.White else primaryTextColor,
                                    )
                                }
                            }

                            Surface(
                                onClick = { clientTypeInput = ServerConfig.CLIENT_QBITTORRENT },
                                shape = RoundedCornerShape(100.dp),
                                color = if (clientTypeInput == ServerConfig.CLIENT_QBITTORRENT) accentColor else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "qBittorrent",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (clientTypeInput == ServerConfig.CLIENT_QBITTORRENT) Color.White else primaryTextColor,
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                unfocusedTextColor = primaryTextColor,
                            ),
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
                            contentAlignment = Alignment.Center,
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
                                        },
                                    ),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (avatarBitmap != null) {
                                        Image(
                                            bitmap = avatarBitmap,
                                            contentDescription = "服务器图片",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_image),
                                            contentDescription = "选择照片",
                                            tint = accentColor,
                                            modifier = Modifier.size(24.dp),
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
                            imeAction = ImeAction.Next,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                            focusedTextColor = primaryTextColor,
                            unfocusedTextColor = primaryTextColor,
                        ),
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
                            imeAction = ImeAction.Next,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                            focusedTextColor = primaryTextColor,
                            unfocusedTextColor = primaryTextColor,
                        ),
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
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                onLoginClick(hostInput, userInput, passInput, clientTypeInput)
                            },
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                            focusedTextColor = primaryTextColor,
                            unfocusedTextColor = primaryTextColor,
                        ),
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 5. 底部操作按键行：左测试连接，右保存登录
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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

                        Button(
                            onClick = { onLoginClick(hostInput, userInput, passInput, clientTypeInput) },
                            enabled = !isLoggingIn && !isTestingConnection,
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.height(36.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.btn_save),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }

        // C. 屏幕最底部固定显示醒目版本的版本号
        Text(
            text = "TransSync v$versionName",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isDark) Color.White else Color(0xFF1E2733),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        )
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
