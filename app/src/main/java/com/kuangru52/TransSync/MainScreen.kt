package com.kuangru52.transsync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 100% 纯 Compose 版本的 MainScreen 服务器连接/登录管理主界面：
 * - 服务器 RPC 地址输入框、用户名、密码配置
 * - 登录/测试连接按钮与进度环
 * - 各个元素位置、字号、边距与功能与 XML activity_main.xml 1:1 绝对一致
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    initialHost: String,
    initialUser: String,
    initialPass: String,
    isLoggingIn: Boolean,
    onLoginClick: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()

    var clientTypeInput by remember { mutableStateOf(ServerConfig.CLIENT_TRANSMISSION) }
    var hostInput by remember { mutableStateOf(initialHost) }
    var userInput by remember { mutableStateOf(initialUser) }
    var passInput by remember { mutableStateOf(initialPass) }
    var isPasswordVisible by remember { mutableStateOf(value = false) }

    val cardBgColor = if (isDark) Color(0xFF1A232E) else Color.White
    val cardBorderColor = if (isDark) Color(0x26FFFFFF) else Color(0xFFE0E0E0)
    val accentColor = if (isDark) Color(0xFF1D88E3) else Color(0xFF00B0FF)

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = cardBgColor,
            border = BorderStroke(1.dp, cardBorderColor),
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Logo 图标 (无白框直接呈现)
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "应用 Logo",
                    modifier = Modifier.size(80.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))

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
                                    color = if (clientTypeInput == ServerConfig.CLIENT_TRANSMISSION) Color.White else (if (isDark) Color.White else Color(0xFF2D3436))
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
                                    color = if (clientTypeInput == ServerConfig.CLIENT_QBITTORRENT) Color.White else (if (isDark) Color.White else Color(0xFF2D3436))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. 服务器 RPC 地址输入框
                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { hostInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    label = { Text("服务器地址") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                        focusedTextColor = if (isDark) Color.White else Color(0xFF2D3436),
                        unfocusedTextColor = if (isDark) Color.White else Color(0xFF2D3436)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. 用户名输入框
                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    label = { Text("用户名") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = if (isDark) Color(0xFF455A64) else Color(0xFFB0BEC5),
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = if (isDark) Color(0xFF90CAF9) else Color(0xFF636E72),
                        focusedTextColor = if (isDark) Color.White else Color(0xFF2D3436),
                        unfocusedTextColor = if (isDark) Color.White else Color(0xFF2D3436)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. 密码输入框
                OutlinedTextField(
                    value = passInput,
                    onValueChange = { passInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    label = { Text("密码") },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                painter = painterResource(id = if (isPasswordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off),
                                contentDescription = "切换密码显示",
                                tint = if (isDark) Color(0xFF9EABB8) else Color(0xFF636E72),
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
                        focusedTextColor = if (isDark) Color.White else Color(0xFF2D3436),
                        unfocusedTextColor = if (isDark) Color.White else Color(0xFF2D3436)
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 4. 连接/登录按钮与进度环
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = accentColor,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    OutlinedButton(
                        onClick = { onLoginClick(hostInput, userInput, passInput, clientTypeInput) },
                        enabled = !isLoggingIn,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, accentColor),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(
                            text = "登录",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MaterialTheme {
        MainScreen(
            initialHost = "https://192.168.1.100:9091",
            initialUser = "admin",
            initialPass = "password",
            isLoggingIn = false,
            onLoginClick = { _, _, _, _ -> }
        )
    }
}
