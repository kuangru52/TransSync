package com.kuangru52.transsync

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.widget.Toast
import com.kuangru52.transsync.databinding.FragmentTorrentPeersBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TorrentPeersFragment : Fragment() {

    private var _binding: FragmentTorrentPeersBinding? = null
    private val binding get() = _binding!!
    private var torrentId: Int = -1
    private lateinit var adapter: PeerListAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (_binding != null) {
                fetchPeers()
                handler.postDelayed(this, 5000)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTorrentPeersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        torrentId = arguments?.getInt(ARG_TORRENT_ID) ?: -1
        
        adapter = PeerListAdapter()
        binding.rvPeers.layoutManager = LinearLayoutManager(context)
        binding.rvPeers.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            reannounce()
        }
        
        fetchPeers()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun reannounce() {
        val activity = activity as? TorrentDetailActivity ?: return
        val intent = activity.intent
        val rpcUrl = intent.getStringExtra("rpcUrl") ?: ""
        val user = intent.getStringExtra("user") ?: ""
        val pass = intent.getStringExtra("pass") ?: ""

        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        val request = RpcRequest("torrent-reannounce", mapOf("ids" to listOf(torrentId)))

        service.rpc(rpcUrl, null, request).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (_binding == null) return
                binding.swipeRefresh.isRefreshing = false
                if (response.isSuccessful) {
                    Toast.makeText(context, R.string.msg_reannounce_success, Toast.LENGTH_SHORT).show()
                    fetchPeers()
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                if (_binding == null) return
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchPeers() {
        val activity = activity as? TorrentDetailActivity ?: return
        val intent = activity.intent
        val rpcUrl = intent.getStringExtra("rpcUrl") ?: ""
        val user = intent.getStringExtra("user") ?: ""
        val pass = intent.getStringExtra("pass") ?: ""

        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        val fields = listOf("id", "peers")
        val request = RpcRequest("torrent-get", mapOf("ids" to listOf(torrentId), "fields" to fields))

        service.rpc(rpcUrl, null, request).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (_binding == null) return
                if (response.isSuccessful) {
                    val arguments = response.body()?.arguments
                    val torrentsJson = Gson().toJson(arguments?.get("torrents"))
                    val type = object : TypeToken<List<Torrent>>() {}.type
                    val torrents: List<Torrent> = Gson().fromJson(torrentsJson, type)
                    val peers = torrents.firstOrNull()?.peers ?: emptyList()
                    adapter.submitList(peers)
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(refreshRunnable)
        _binding = null
    }

    companion object {
        private const val ARG_TORRENT_ID = "torrent_id"
        fun newInstance(torrentId: Int) = TorrentPeersFragment().apply {
            arguments = Bundle().apply { putInt(ARG_TORRENT_ID, torrentId) }
        }
    }
}

