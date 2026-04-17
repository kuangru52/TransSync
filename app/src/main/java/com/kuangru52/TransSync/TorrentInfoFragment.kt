package com.kuangru52.TransSync

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kuangru52.TransSync.databinding.FragmentTorrentInfoBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class TorrentInfoFragment : Fragment() {

    private var _binding: FragmentTorrentInfoBinding? = null
    private val binding get() = _binding!!
    private var torrentId: Int = -1

    private val handler = Handler(Looper.getMainLooper())
    private var fileAdapter: TorrentFileAdapter? = null
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (_binding != null) {
                fetchTorrentDetails()
                handler.postDelayed(this, 5000)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTorrentInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        torrentId = arguments?.getInt(ARG_TORRENT_ID) ?: -1
        
        binding.ivEditTracker.setOnClickListener {
            showEditTrackersDialog()
        }

        binding.tvName.setOnClickListener {
            val isVisible = binding.rvFiles.visibility == View.VISIBLE
            if (isVisible) {
                binding.rvFiles.visibility = View.GONE
                fileAdapter?.collapseAll()
            } else {
                binding.rvFiles.visibility = View.VISIBLE
            }
        }

        fileAdapter = TorrentFileAdapter()
        binding.rvFiles.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = fileAdapter
        
        fetchTorrentDetails()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun fetchTorrentDetails() {
        val activity = activity as? TorrentDetailActivity ?: return
        val intent = activity.intent
        val rpcUrl = intent.getStringExtra("rpcUrl") ?: ""
        val user = intent.getStringExtra("user") ?: ""
        val pass = intent.getStringExtra("pass") ?: ""

        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        val fields = listOf(
            "id", "name", "status", "totalSize", "percentDone", "downloadDir", "trackers", "trackerStats", "downloadedEver",
            "uploadedEver", "uploadRatio", "addedDate", "doneDate", "activityDate", "secondsSeeding", "files",
            "error", "errorString", "eta"
        )
        val request = RpcRequest("torrent-get", mapOf("ids" to listOf(torrentId), "fields" to fields))

        service.rpc(rpcUrl, null, request).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (_binding == null) return
                if (response.isSuccessful) {
                    val arguments = response.body()?.arguments
                    val torrentsJson = Gson().toJson(arguments?.get("torrents"))
                    val type = object : TypeToken<List<Torrent>>() {}.type
                    val torrents: List<Torrent> = Gson().fromJson(torrentsJson, type)
                    torrents.firstOrNull()?.let { 
                        updateUi(it)
                        (activity as? TorrentDetailActivity)?.updateStatusIcon(it.status)
                    }
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
        })
    }

    private fun updateUi(torrent: Torrent) {
        // Handle Error Display
        if (torrent.error != 0 && torrent.errorString.isNotEmpty()) {
            binding.llError.visibility = View.VISIBLE
            binding.tvError.text = torrent.errorString
        } else {
            binding.llError.visibility = View.GONE
        }

        binding.tvName.text = torrent.name
        val progressStr = String.format(Locale.US, "%.1f%%", torrent.percentDone * 100)
        binding.tvDownloaded.text = formatSize(torrent.downloadedEver) + " ($progressStr)"
        binding.tvTotalSize.text = formatSize(torrent.totalSize)
        binding.tvDownloadDir.text = torrent.downloadDir

        // ETA Display
        binding.tvEta.text = if (torrent.eta != null && torrent.eta > 0) {
            formatDuration(torrent.eta)
        } else if (torrent.percentDone >= 1.0 || (torrent.eta != null && torrent.eta == 0L)) {
            "0"
        } else {
            "∞"
        }

        // Tracker Display (Domain only for privacy)
        val trackerDisplay = torrent.trackers?.joinToString("\n") { tracker ->
            try {
                val uri = java.net.URI(tracker.announce)
                uri.host ?: tracker.announce
            } catch (e: Exception) {
                tracker.announce.substringBefore("/")
            }
        } ?: getString(R.string.state_none)
        binding.tvTracker.text = trackerDisplay

        binding.tvUploaded.text = formatSize(torrent.uploadedEver)
        binding.tvRatio.text = String.format(Locale.US, "%.2f", torrent.uploadRatio)
        binding.tvAddedDate.text = formatDate(torrent.addedDate)
        binding.tvDoneDate.text = if (torrent.doneDate > 0) formatDate(torrent.doneDate) else getString(R.string.state_not_finished)
        binding.tvActivityDate.text = if (torrent.activityDate > 0) formatDate(torrent.activityDate) else getString(R.string.state_none)
        binding.tvSeedingTime.text = formatDuration(torrent.secondsSeeding)

        // Update files
        torrent.files?.let { fileAdapter?.setFiles(it) }

        // Update Tracker Counts
        val stats = torrent.trackerStats?.firstOrNull()
        if (stats != null) {
            binding.tvPeers.text = "${stats.seederCount} / ${stats.leecherCount} / ${stats.downloadCount}"
        } else {
            binding.tvPeers.text = "0 / 0 / 0"
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kbs = bytesPerSec / 1024.0
        return if (kbs < 1024) {
            String.format(Locale.getDefault(), "%.1f KB/s", kbs)
        } else {
            String.format(Locale.getDefault(), "%.1f MB/s", kbs / 1024.0)
        }
    }

    private fun formatDate(seconds: Long): String {
        val date = Date(seconds * 1000)
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = date }

        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = sdfTime.format(date)

        return when {
            isSameDay(now, target) -> getString(R.string.time_today, timeStr)
            isYesterday(now, target) -> getString(R.string.time_yesterday, timeStr)
            else -> {
                val sdfDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                sdfDate.format(date)
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, target: Calendar): Boolean {
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return isSameDay(yesterday, target)
    }

    private fun formatDuration(seconds: Long): String {
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        val sb = StringBuilder()
        if (d > 0) sb.append(getString(R.string.unit_day, d))
        if (h > 0 || d > 0) sb.append(getString(R.string.unit_hour, h))
        sb.append(getString(R.string.unit_minute, m))
        return sb.toString()
    }

    private fun showEditTrackersDialog() {
        val activity = activity as? TorrentDetailActivity ?: return
        val intent = activity.intent
        val rpcUrl = intent.getStringExtra("rpcUrl") ?: ""
        val user = intent.getStringExtra("user") ?: ""
        val pass = intent.getStringExtra("pass") ?: ""

        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        
        // Fetch full tracker list first
        val request = RpcRequest("torrent-get", mapOf("ids" to listOf(torrentId), "fields" to listOf("trackers")))
        
        service.rpc(rpcUrl, null, request).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (_binding == null) return
                if (response.isSuccessful) {
                    val arguments = response.body()?.arguments
                    val torrentsJson = Gson().toJson(arguments?.get("torrents"))
                    val type = object : TypeToken<List<Torrent>>() {}.type
                    val torrents: List<Torrent> = Gson().fromJson(torrentsJson, type)
                    val oldTrackers = torrents.firstOrNull()?.trackers ?: emptyList()
                    
                    val trackerUrls = oldTrackers.map { it.announce }.joinToString("\n")
                    
                    val input = android.widget.EditText(context).apply {
                        setText(trackerUrls)
                        setPadding(48, 48, 48, 48)
                        gravity = android.view.Gravity.TOP
                        minLines = 5
                    }

                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(R.string.dialog_edit_tracker_title)
                        .setView(input)
                        .setPositiveButton(R.string.dialog_save) { _, _ ->
                            val newUrls = input.text.toString().split("\n")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            updateTrackers(newUrls, oldTrackers)
                        }
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .show()
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                if (_binding == null) return
                android.widget.Toast.makeText(context, R.string.msg_fetch_tracker_failed, android.widget.Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateTrackers(newUrls: List<String>, oldTrackers: List<Tracker>) {
        val activity = activity as? TorrentDetailActivity ?: return
        val intent = activity.intent
        val rpcUrl = intent.getStringExtra("rpcUrl") ?: ""
        val user = intent.getStringExtra("user") ?: ""
        val pass = intent.getStringExtra("pass") ?: ""

        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        
        val oldUrls = oldTrackers.map { it.announce }
        val toAdd = newUrls.filter { it !in oldUrls }
        val toRemoveIds = oldTrackers.filter { it.announce !in newUrls }.map { it.id }

        if (toAdd.isEmpty() && toRemoveIds.isEmpty()) return

        val args = mutableMapOf<String, Any>(
            "ids" to listOf(torrentId)
        )
        if (toAdd.isNotEmpty()) args["trackerAdd"] = toAdd
        if (toRemoveIds.isNotEmpty()) args["trackerRemove"] = toRemoveIds

        val request = RpcRequest("torrent-set", args)

        service.rpc(rpcUrl, null, request).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (_binding == null) return
                if (response.isSuccessful) {
                    android.widget.Toast.makeText(context, R.string.msg_tracker_updated, android.widget.Toast.LENGTH_SHORT).show()
                    fetchTorrentDetails()
                } else {
                    android.widget.Toast.makeText(context, getString(R.string.msg_update_failed, response.code()), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                if (_binding == null) return
                android.widget.Toast.makeText(context, R.string.msg_network_error, android.widget.Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(refreshRunnable)
        _binding = null
    }

    companion object {
        private const val ARG_TORRENT_ID = "torrent_id"
        fun newInstance(torrentId: Int) = TorrentInfoFragment().apply {
            arguments = Bundle().apply { putInt(ARG_TORRENT_ID, torrentId) }
        }
    }
}

