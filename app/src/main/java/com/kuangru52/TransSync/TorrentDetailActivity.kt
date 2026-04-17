package com.kuangru52.TransSync

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
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
            tab.text = if (position == 0) "信息" else "节点"
        }.attach()

        binding.btnStatus.setOnClickListener {
            onStatusClick()
        }
    }

    fun updateStatusIcon(status: Int) {
        currentStatus = status
        if (status == 0) {
            binding.btnStatus.setImageResource(R.drawable.ic_play)
        } else {
            binding.btnStatus.setImageResource(R.drawable.ic_pause)
        }
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
                    Toast.makeText(this@TorrentDetailActivity, "操作失败: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                Toast.makeText(this@TorrentDetailActivity, "网络错误", Toast.LENGTH_SHORT).show()
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

