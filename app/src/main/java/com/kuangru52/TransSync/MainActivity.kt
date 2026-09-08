package com.kuangru52.transsync

import android.content.pm.ActivityInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kuangru52.transsync.databinding.ActivityMainBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        val isTablet = resources.getBoolean(R.bool.isTablet)
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
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
        if (!isEditing && savedRpcUrl != null && savedUser != null && savedPass != null) {
            startTorrentList(savedRpcUrl, savedUser, savedPass, externalUri, sharedText)
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.btnLogin.performClick()
                true
            } else {
                false
            }
        }

        // 预填充字段
        val displayUrl = savedRpcUrl?.substringBefore(RPC_PATH)?.removeSuffix("/") ?: ""
        binding.etHost.setText(displayUrl)
        binding.etUsername.setText(savedUser ?: "")
        binding.etPassword.setText(savedPass ?: "")

        binding.btnLogin.setOnClickListener {
            var host = binding.etHost.text.toString().trim()
            val user = binding.etUsername.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (isEditing) {
                val currentDisplayUrl = (savedRpcUrl ?: "").substringBefore(RPC_PATH).removeSuffix("/")
                if (host == currentDisplayUrl && user == savedUser && pass == savedPass) {
                    onBackPressedDispatcher.onBackPressed()
                    return@setOnClickListener
                }
            }

            if (host.isBlank() || user.isBlank() || pass.isBlank()) {
                Toast.makeText(this, R.string.msg_fill_all_fields, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (host.startsWith("http://")) {
                Toast.makeText(this, R.string.msg_insecure_http, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!host.startsWith("https://")) {
                host = "https://$host"
            }

            val rpcUrl = host.removeSuffix("/") + RPC_PATH
            login(rpcUrl, user, pass)
        }
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

    private fun login(rpcUrl: String, user: String, pass: String) {
        binding.loginProgress.visibility = View.VISIBLE
        val baseUrl = rpcUrl.substringBefore(RPC_PATH) + "/"
        val service = TransmissionClient.getService(baseUrl, user, pass)
        
        val request = RpcRequest("torrent-get", mapOf("fields" to listOf("id", "name")))
        
        service.rpc(rpcUrl, null, request).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(
                call: Call<RpcResponse<Map<String, Any>>>,
                response: Response<RpcResponse<Map<String, Any>>>
            ) {
                binding.loginProgress.visibility = View.GONE
                if (response.isSuccessful || response.code() == 409) {
                    getSharedPreferences("auth", MODE_PRIVATE).edit().apply {
                        putString("rpcUrl", rpcUrl)
                        putString("user", user)
                        putString("pass", pass)
                        apply()
                    }
                    startTorrentList(rpcUrl, user, pass, intent.data, intent.getStringExtra(Intent.EXTRA_TEXT))
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.msg_login_failed, response.code()), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                binding.loginProgress.visibility = View.GONE
                Toast.makeText(this@MainActivity, getString(R.string.msg_error_with_msg, t.message), Toast.LENGTH_LONG).show()
            }
        })
    }

    companion object {
        const val RPC_PATH = "/transmission/rpc"
    }
}
