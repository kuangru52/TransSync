package com.kuangru52.TransSync

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.kuangru52.TransSync.databinding.ActivityTorrentDetailBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TorrentDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTorrentDetailBinding
    private var torrentId: Int = -1
    private var currentStatus: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        val isTablet = resources.getBoolean(R.bool.isTablet)
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityTorrentDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        torrentId = intent.getIntExtra("torrent_id", -1)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int) = when (position) {
                0 -> TorrentInfoFragment.newInstance(torrentId)
                else -> TorrentPeersFragment.newInstance(torrentId)
            }
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) getString(R.string.tab_info) else getString(R.string.tab_peers)
        }.attach()
    }

    fun updateStatusIcon(status: Int) {
        // 由于右侧按钮已移除，此方法可以保持空或移除相关调用
    }

    private fun onStatusClick() {
        if (torrentId == -1 || currentStatus == -1) return

        val rpcUrl = intent.getStringExtra("rpcUrl") ?: ""
        val user = intent.getStringExtra("user") ?: ""
        val pass = intent.getStringExtra("pass") ?: ""
        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)

        val isPaused = currentStatus == 0
        val method = if (isPaused) "torrent-start" else "torrent-stop"

        // 乐观更新 UI
        updateStatusIcon(if (isPaused) 4 else 0)

        val request = RpcRequest(method, mapOf("ids" to listOf(torrentId)))
        service.rpc(rpcUrl, null, request).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (response.isSuccessful) {
                    // 等待下一轮轮询刷新状态
                } else {
                    Toast.makeText(this@TorrentDetailActivity, getString(R.string.error_operation_failed, response.code()), Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                Toast.makeText(this@TorrentDetailActivity, R.string.error_network, Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

