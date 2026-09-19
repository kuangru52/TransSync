package com.kuangru52.transsync

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowInsetsControllerCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private var isLoggingInState by mutableStateOf(value = false)

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setupStatusBar()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { isGranted: Boolean ->
                if (!isGranted) {
                    Toast.makeText(this, R.string.msg_notification_denied, Toast.LENGTH_LONG).show()
                }
            }
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val savedRpcUrl = prefs.getString("rpcUrl", null)
        val savedUser = prefs.getString("user", null)
        val savedPass = prefs.getString("pass", null)

        val isEditing = intent.getBooleanExtra("isEditing", false)
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val externalUri = intent.data ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)

        // 如果不是编辑模式且有保存的凭据，直接跳转
        if ((!isEditing) && (savedRpcUrl != null) && (savedUser != null) && (savedPass != null)) {
            startTorrentList(savedRpcUrl, savedUser, savedPass, externalUri, sharedText)
            return
        }

        val displayUrl = savedRpcUrl?.substringBefore(RPC_PATH)?.removeSuffix("/") ?: ""

        setContent {
            MaterialTheme {
                MainScreen(
                    initialHost = displayUrl,
                    initialUser = savedUser ?: "",
                    initialPass = savedPass ?: "",
                    isLoggingIn = isLoggingInState,
                    onLoginClick = { hostInput, userInput, passInput, clientTypeInput ->
                        performLoginClick(hostInput, userInput, passInput, clientTypeInput, isEditing, savedRpcUrl, savedUser, savedPass)
                    }
                )
            }
        }
    }

    private fun performLoginClick(
        hostInput: String,
        userInput: String,
        passInput: String,
        clientTypeInput: String,
        isEditing: Boolean,
        savedRpcUrl: String?,
        savedUser: String?,
        savedPass: String?
    ) {
        var host = hostInput.trim()
        val user = userInput.trim()
        val pass = passInput.trim()

        if (isEditing) {
            val currentDisplayUrl = (savedRpcUrl ?: "").substringBefore(RPC_PATH).removeSuffix("/")
            if ((host == currentDisplayUrl) && (user == savedUser) && (pass == savedPass)) {
                onBackPressedDispatcher.onBackPressed()
                return
            }
        }

        if (host.isBlank()) {
            Toast.makeText(this, R.string.msg_fill_all_fields, Toast.LENGTH_SHORT).show()
            return
        }

        if (host.startsWith("http://")) {
            Toast.makeText(this, R.string.msg_insecure_http, Toast.LENGTH_LONG).show()
            return
        }
        if (!host.startsWith("https://") && !host.startsWith("http://")) {
            host = "https://$host"
        }

        val rpcUrl = if (clientTypeInput == ServerConfig.CLIENT_TRANSMISSION) {
            if (host.endsWith("/transmission/rpc")) host else host.removeSuffix("/") + RPC_PATH
        } else {
            host
        }

        login(rpcUrl, user, pass, clientTypeInput)
    }

    private fun startTorrentList(rpcUrl: String, user: String, pass: String, uri: Uri?, text: String?) {
        val nextIntent = Intent(this, TorrentListActivity::class.java).apply {
            putExtra("rpcUrl", rpcUrl)
            putExtra("user", user)
            putExtra("pass", pass)
            data = uri
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(nextIntent)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun setupStatusBar() {
        if (android.os.Build.VERSION.SDK_INT < 35) {
            window.statusBarColor = "#F3F3F3".toColorInt()
        } else {
            window.statusBarColor = Color.TRANSPARENT
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        }
    }

    private fun login(rpcUrl: String, user: String, pass: String, clientType: String = ServerConfig.CLIENT_TRANSMISSION) {
        isLoggingInState = true

        val serverConfig = ServerConfig(
            alias = if (clientType == ServerConfig.CLIENT_QBITTORRENT) "qBittorrent" else "主服务器",
            clientType = clientType,
            rpcUrl = rpcUrl,
            user = user,
            pass = pass,
            isActive = true
        )
        ServerManager.saveServer(this, serverConfig)

        if (clientType == ServerConfig.CLIENT_QBITTORRENT) {
            val qbitService = QBittorrentClient.getService(rpcUrl)
            qbitService.getTransferInfo().enqueue(object : Callback<QbitTransferInfo> {
                override fun onResponse(call: Call<QbitTransferInfo>, response: Response<QbitTransferInfo>) {
                    isLoggingInState = false
                    if (response.isSuccessful || response.code() == 200) {
                        startTorrentList(rpcUrl, user, pass, intent.data, intent.getStringExtra(Intent.EXTRA_TEXT))
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.msg_login_failed, response.code()), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<QbitTransferInfo>, t: Throwable) {
                    isLoggingInState = false
                    Toast.makeText(this@MainActivity, getString(R.string.msg_error_with_msg, t.message), Toast.LENGTH_LONG).show()
                }
            })
        } else {
            val baseUrl = if (rpcUrl.contains(RPC_PATH)) rpcUrl.substringBefore(RPC_PATH) + "/" else rpcUrl
            val service = TransmissionClient.getService(baseUrl, user, pass)
            val request = RpcRequest("torrent-get", mapOf("fields" to listOf("id", "name")))

            service.rpc(rpcUrl, null, request).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(
                    call: Call<RpcResponse<Map<String, Any>>>,
                    response: Response<RpcResponse<Map<String, Any>>>
                ) {
                    isLoggingInState = false
                    if (response.isSuccessful || response.code() == 409) {
                        startTorrentList(rpcUrl, user, pass, intent.data, intent.getStringExtra(Intent.EXTRA_TEXT))
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.msg_login_failed, response.code()), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                    isLoggingInState = false
                    Toast.makeText(this@MainActivity, getString(R.string.msg_error_with_msg, t.message), Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    companion object {
        const val RPC_PATH = "/transmission/rpc"
    }
}
