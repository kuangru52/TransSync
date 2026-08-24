package com.kuangru52.TransSync

import android.content.pm.ActivityInfo
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val isTablet = resources.getBoolean(R.bool.isTablet)
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val savedRpcUrl = prefs.getString("rpcUrl", null)
        val savedUser = prefs.getString("user", null)
        val savedPass = prefs.getString("pass", null)

        val isEditing = intent.getBooleanExtra("isEditing", false)

        if (!isEditing && savedRpcUrl != null && savedUser != null && savedPass != null) {
            val intent = Intent(this, TorrentListActivity::class.java).apply {
                putExtra("rpcUrl", savedRpcUrl)
                putExtra("user", savedUser)
                putExtra("pass", savedPass)
            }
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        val mainView = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        val etHost = findViewById<TextInputEditText>(R.id.etHost)
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // Pre-fill fields if we have saved info
        val displayUrl = savedRpcUrl?.substringBefore("/transmission/rpc")?.removeSuffix("/") ?: ""
        etHost.setText(displayUrl)
        etUsername.setText(savedUser ?: "")
        etPassword.setText(savedPass ?: "")

        btnLogin.setOnClickListener {
            var host = etHost.text.toString().trim()
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            // If in editing mode and content hasn't changed, just return
            if (isEditing) {
                val currentDisplayUrl = (savedRpcUrl ?: "").substringBefore("/transmission/rpc").removeSuffix("/")
                if (host == currentDisplayUrl && user == savedUser && pass == savedPass) {
                    onBackPressedDispatcher.onBackPressed()
                    return@setOnClickListener
                }
            }

            if (host.isBlank() || user.isBlank() || pass.isBlank()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Enforce HTTPS
            if (host.startsWith("http://")) {
                Toast.makeText(this, "Insecure HTTP is not allowed. Please use HTTPS.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!host.startsWith("https://")) {
                host = "https://$host"
            }

            // Standardize Transmission RPC URL
            val rpcUrl = host.removeSuffix("/") + "/transmission/rpc"

            login(rpcUrl, user, pass)
        }
    }

    private fun login(rpcUrl: String, user: String, pass: String) {
        val baseUrl = rpcUrl.substringBefore("/transmission/rpc") + "/"
        val service = TransmissionClient.getService(baseUrl, user, pass)
        
        val request = RpcRequest("torrent-get", mapOf("fields" to listOf("id", "name")))
        
        service.rpc(rpcUrl, null, request).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(
                call: Call<RpcResponse<Map<String, Any>>>,
                response: Response<RpcResponse<Map<String, Any>>>
            ) {
                if (response.isSuccessful || response.code() == 409) {
                    // Save login state
                    getSharedPreferences("auth", MODE_PRIVATE).edit().apply {
                        putString("rpcUrl", rpcUrl)
                        putString("user", user)
                        putString("pass", pass)
                        apply()
                    }

                    val intent = Intent(this@MainActivity, TorrentListActivity::class.java).apply {
                        putExtra("rpcUrl", rpcUrl)
                        putExtra("user", user)
                        putExtra("pass", pass)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@MainActivity, "Login failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }
}

