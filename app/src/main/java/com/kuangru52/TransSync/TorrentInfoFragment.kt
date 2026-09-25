package com.kuangru52.transsync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TorrentInfoFragment : Fragment() {

    private var torrentId: Int = -1
    private var torrentState by mutableStateOf<Torrent?>(null)

    companion object {
        private const val ARG_TORRENT_ID = "torrent_id"

        @Suppress("unused")
        fun newInstance(torrentId: Int): TorrentInfoFragment {
            val fragment = TorrentInfoFragment()
            val args = Bundle()
            args.putInt(ARG_TORRENT_ID, torrentId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        torrentId = arguments?.getInt(ARG_TORRENT_ID) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val activity = activity as? TorrentDetailActivity
        val intent = activity?.intent
        val rpcUrl = intent?.getStringExtra("rpcUrl") ?: ""
        val user = intent?.getStringExtra("user") ?: ""
        val pass = intent?.getStringExtra("pass") ?: ""

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    TorrentInfoScreen(
                        torrent = torrentState,
                        rpcUrl = rpcUrl,
                        user = user,
                        pass = pass,
                        onRefresh = { fetchTorrentDetails() }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetchTorrentDetails()
    }

    fun fetchTorrentDetails() {
        val activity = (activity as? TorrentDetailActivity) ?: return
        val intent = activity.intent
        val rpcUrl = intent.getStringExtra("rpcUrl") ?: ""
        val user = intent.getStringExtra("user") ?: ""
        val pass = intent.getStringExtra("pass") ?: ""

        val service = TransmissionClient.getService(rpcUrl, user, pass)
        val fields = listOf(
            "id", "name", "totalSize", "percentDone", "rateDownload", "rateUpload",
            "downloadedEver", "uploadedEver", "uploadRatio", "status", "downloadDir",
            "addedDate", "doneDate", "activityDate", "secondsSeeding", "secondsDownloading",
            "error", "errorString", "labels", "files", "fileStats", "trackers", "peersGettingFromUs", "peersSendingToUs"
        )

        val args = mapOf<String, Any>(
            "fields" to fields,
            "ids" to listOf(torrentId)
        )

        service.getTorrents(rpcUrl, null, RpcRequest("torrent-get", args)).enqueue(object : Callback<RpcResponse<TorrentListArguments>> {
            override fun onResponse(call: Call<RpcResponse<TorrentListArguments>>, response: Response<RpcResponse<TorrentListArguments>>) {
                if (response.isSuccessful) {
                    val torrent = response.body()?.arguments?.torrents?.firstOrNull()
                    if (torrent != null) {
                        torrentState = torrent
                    }
                }
            }

            override fun onFailure(call: Call<RpcResponse<TorrentListArguments>>, t: Throwable) {
                context?.let {
                    Toast.makeText(it, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}
