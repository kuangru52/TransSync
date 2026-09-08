package com.kuangru52.transsync

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kuangru52.transsync.databinding.FragmentTorrentInfoBinding
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

        binding.ivEditLocation.setOnClickListener {
            showSetLocationDialog()
        }

        binding.tvName.setOnClickListener {
            binding.rvFiles.isVisible = !binding.rvFiles.isVisible
        }

        binding.tvName.setOnLongClickListener {
            val activity = activity as? TorrentDetailActivity ?: return@setOnLongClickListener true
            val intent = activity.intent
            val rpcUrl = intent.getStringExtra("rpcUrl") ?: ""
            val user = intent.getStringExtra("user") ?: ""
            val pass = intent.getStringExtra("pass") ?: ""
            
            DialogUtils.showRenameDialog(
                context = requireContext(),
                rpcUrl = rpcUrl,
                user = user,
                pass = pass,
                torrentId = torrentId,
                currentName = binding.tvName.text.toString(),
                onSuccess = {
                    fetchTorrentDetails()
                }
            )
            true
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

        val service = TransmissionClient.getService(rpcUrl, user, pass)
        val fields = listOf(
            "id", "name", "status", "totalSize", "percentDone", "downloadDir", "trackers", "trackerStats", "downloadedEver",
            "uploadedEver", "uploadRatio", "addedDate", "doneDate", "activityDate", "secondsSeeding", "files",
            "error", "errorString", "eta", "labels"
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
        binding.tvDownloaded.text = getString(R.string.label_downloaded_with_progress, FormatUtils.formatSize(torrent.downloadedEver), progressStr)
        binding.tvTotalSize.text = FormatUtils.formatSize(torrent.totalSize)
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
            } catch (_: Exception) {
                tracker.announce.substringBefore("/")
            }
        } ?: getString(R.string.state_none)
        binding.tvTracker.text = trackerDisplay

        binding.tvUploaded.text = FormatUtils.formatSize(torrent.uploadedEver)
        binding.tvRatio.text = String.format(Locale.US, "%.2f", torrent.uploadRatio)
        binding.tvAddedDate.text = formatDate(torrent.addedDate)
        binding.tvDoneDate.text = if (torrent.doneDate > 0) formatDate(torrent.doneDate) else getString(R.string.state_not_finished)
        binding.tvActivityDate.text = if (torrent.activityDate > 0) formatDate(torrent.activityDate) else getString(R.string.state_none)
        binding.tvSeedingTime.text = formatDuration(torrent.secondsSeeding)

        // H&R Status
        val hrLabel = torrent.labels?.find { it.startsWith("HR:") }
        if (hrLabel != null) {
            val requiredHours = hrLabel.substringAfter("HR:").toDoubleOrNull() ?: 0.0
            if (torrent.percentDone < 1.0) {
                binding.tvHrStatus.text = getString(R.string.hr_tag)
                binding.tvHrStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            } else if (torrent.doneDate > 0) {
                val doneTimeMs = torrent.doneDate * 1000L
                val limitTimeMs = doneTimeMs + (requiredHours * 3600 * 1000L).toLong()
                val currentTime = System.currentTimeMillis()
                val remainingMs = limitTimeMs - currentTime
                val bufferMs = 30 * 60 * 1000L

                if (remainingMs > -bufferMs) {
                    if (remainingMs > 0) {
                        val days = remainingMs / (24 * 3600 * 1000L)
                        val hours = (remainingMs % (24 * 3600 * 1000L)) / (3600 * 1000L)
                        val mins = (remainingMs % (3600 * 1000L)) / (60 * 1000L)
                        
                        val timeStr = buildString {
                            if (days > 0) append("${days}d ")
                            if (hours > 0 || days > 0) append("${hours}h ")
                            append("${mins}m")
                        }
                        binding.tvHrStatus.text = getString(R.string.label_hr_remaining, timeStr)
                        binding.tvHrStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.state_orange))
                    } else {
                        // Within 30 minutes buffer
                        val remainingBufferMs = bufferMs + remainingMs
                        val totalSecs = (remainingBufferMs / 1000L).coerceAtLeast(0)
                        val mins = totalSecs / 60
                        val secs = totalSecs % 60
                        binding.tvHrStatus.text = String.format(Locale.US, "%02d:%02d", mins, secs)
                        binding.tvHrStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.state_blue))
                    }
                    binding.ivHrStatus.visibility = View.GONE
                } else {
                    binding.tvHrStatus.text = getString(R.string.hr_met)
                    binding.tvHrStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.state_green))
                    binding.ivHrStatus.visibility = View.VISIBLE
                    binding.ivHrStatus.setImageResource(R.drawable.ic_done)
                }
            } else {
                binding.tvHrStatus.text = getString(R.string.hr_tag)
                binding.tvHrStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                binding.ivHrStatus.visibility = View.GONE
            }
        } else {
            binding.tvHrStatus.text = getString(R.string.state_none)
            binding.tvHrStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            binding.ivHrStatus.visibility = View.GONE
        }

        // Update files
        torrent.files?.let { fileAdapter?.setFiles(it) }

        // Update Tracker Counts
        val stats = torrent.trackerStats?.firstOrNull()
        if (stats != null) {
            binding.tvPeers.text = getString(R.string.label_peers_full, stats.seederCount, stats.leecherCount, stats.downloadCount)
        } else {
            binding.tvPeers.text = getString(R.string.label_peers_full, 0, 0, 0)
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

        val service = TransmissionClient.getService(rpcUrl, user, pass)
        
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
                    
                    val trackerUrls = oldTrackers.joinToString("\n") { it.announce }
                    
                    DialogUtils.showEditTrackersDialog(
                        context = requireContext(),
                        currentUrls = trackerUrls,
                        onSave = { newText ->
                            val newUrls = newText.split("\n")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            updateTrackers(newUrls, oldTrackers)
                        }
                    )
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

        val service = TransmissionClient.getService(rpcUrl, user, pass)
        
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

    private fun showSetLocationDialog() {
        val activity = activity as? TorrentDetailActivity ?: return
        val intent = activity.intent
        val rpcUrl = intent.getStringExtra("rpcUrl") ?: ""
        val user = intent.getStringExtra("user") ?: ""
        val pass = intent.getStringExtra("pass") ?: ""

        val currentDir = binding.tvDownloadDir.text.toString()

        DialogUtils.showSetLocationDialog(
            context = requireContext(),
            rpcUrl = rpcUrl,
            user = user,
            pass = pass,
            torrentIds = listOf(torrentId),
            currentDir = currentDir,
            allTorrents = null, // 此处传入 null，DownloadDirManager 将使用已嗅探的历史记录
            onSuccess = {
                fetchTorrentDetails()
            }
        )
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
