package com.kuangru52.transsync

import com.kuangru52.transsync.R
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

data class DrawerItemData(val count: Int, val totalSize: Long)

class TorrentListViewModel : ViewModel() {

    private val _torrents = MutableLiveData<List<Torrent>>()
    val torrents: LiveData<List<Torrent>> = _torrents

    private val _totalDownloadSpeed = MutableLiveData<String>()
    val totalDownloadSpeed: LiveData<String> = _totalDownloadSpeed

    private val _totalUploadSpeed = MutableLiveData<String>()
    val totalUploadSpeed: LiveData<String> = _totalUploadSpeed

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _totalSize = MutableLiveData<Long>()
    val totalSize: LiveData<Long> = _totalSize

    private val _drawerData = MutableLiveData<Map<String, DrawerItemData>>()
    val drawerData: LiveData<Map<String, DrawerItemData>> = _drawerData

    private val _trackerData = MutableLiveData<Map<String, Int>>()
    val trackerData: LiveData<Map<String, Int>> = _trackerData

    private val _freeSpace = MutableLiveData<String>()
    val freeSpace: LiveData<String> = _freeSpace

    private val _altSpeedEnabled = MutableLiveData<Boolean>()
    val altSpeedEnabled: LiveData<Boolean> = _altSpeedEnabled

    private var allTorrentsRaw: List<Torrent> = emptyList()
    private val activeTorrentsLastSeen = mutableMapOf<Int, Long>()
    private val gracePeriodMs = 30000L

    private var currentFilter: String = "All"
    private var searchQuery: String = ""

    fun setFilter(filter: String) {
        currentFilter = filter
        applyFilterAndSearch()
    }

    fun setSearchQuery(query: String) {
        searchQuery = query
        applyFilterAndSearch()
    }

    fun refreshTorrents(context: Context?, rpcUrl: String, user: String, pass: String) {
        if (rpcUrl.isEmpty()) return
        
        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        val fields = listOf(
            "id", "name", "status", "percentDone", "rateDownload", "rateUpload",
            "totalSize", "sizeWhenDone", "leftUntilDone", "error", "errorString",
            "addedDate", "doneDate", "activityDate", "secondsSeeding",
            "downloadedEver", "uploadedEver", "uploadRatio", "eta", "downloadDir",
            "trackers", "trackerStats", "labels", "recheckProgress"
        )
        val request = RpcRequest("torrent-get", mapOf("fields" to fields))

        service.rpc(rpcUrl, null, request).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                _isLoading.postValue(false)
                if (response.isSuccessful) {
                    val arguments = response.body()?.arguments
                    val torrentsJson = Gson().toJson(arguments?.get("torrents"))
                    val type = object : TypeToken<List<Torrent>>() {}.type
                    val fetchedTorrents: List<Torrent> = Gson().fromJson(torrentsJson, type)
                    
                    processTorrents(context, fetchedTorrents)
                    updateFreeSpace(rpcUrl, user, pass)
                    checkAltSpeedStatus(rpcUrl, user, pass)
                }
            }

            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                _isLoading.postValue(false)
            }
        })
    }

    private fun processTorrents(context: Context?, list: List<Torrent>) {
        val currentTime = System.currentTimeMillis()
        var totalDown = 0.0
        var totalUp = 0.0
        
        list.forEach { torrent ->
            totalDown += torrent.rateDownload
            totalUp += torrent.rateUpload
            
            if (torrent.rateDownload > 0 || torrent.rateUpload > 0) {
                activeTorrentsLastSeen[torrent.id] = currentTime
            }

            // 预计算 UI 字段
            torrent.displayDownloadSpeed = "${formatSpeed(torrent.rateDownload.toDouble())} ↓"
            torrent.displayUploadSpeed = "${formatSpeed(torrent.rateUpload.toDouble())} ↑"
            
            val color = when {
                torrent.error != 0 || (torrent.errorString.isNotEmpty() && !torrent.errorString.contains("none", ignoreCase = true)) -> 
                    context?.let { ContextCompat.getColor(it, R.color.state_red) } ?: 0xFFFF0000.toInt()
                torrent.status == 1 || torrent.status == 2 -> "#FFF9A825".toColorInt()
                torrent.status == 0 -> context?.let { ContextCompat.getColor(it, R.color.state_gray) } ?: 0xFF808080.toInt()
                torrent.percentDone >= 1.0 -> context?.let { ContextCompat.getColor(it, R.color.state_green) } ?: 0xFF00FF00.toInt()
                else -> context?.let { ContextCompat.getColor(it, R.color.state_blue) } ?: 0xFF0000FF.toInt()
            }
            torrent.displayColor = color
            
            torrent.displayProgress = if (torrent.status == 1 || torrent.status == 2) 
                (torrent.recheckProgress * 1000).toInt() else (torrent.percentDone * 1000).toInt()
            
            torrent.displayStatusText = if (torrent.status == 1 || torrent.status == 2) 
                "校验中 (${String.format(Locale.US, "%.1f%%", torrent.recheckProgress * 100)})" else ""

            val sizeStr = formatSize(torrent.totalSize)
            torrent.displaySize = if (torrent.percentDone >= 1.0) sizeStr else "${formatSize(torrent.downloadedEver)} / $sizeStr"
            torrent.displayStats = "${formatSize(torrent.uploadedEver)} (分享率 ${String.format(Locale.US, "%.2f", torrent.uploadRatio)})"
        }
        
        allTorrentsRaw = list
        _totalDownloadSpeed.postValue(formatSpeed(totalDown))
        _totalUploadSpeed.postValue(formatSpeed(totalUp))
        
        updateDrawerData(list)
        updateTrackerData(list)
        applyFilterAndSearch()
    }

    private fun updateTrackerData(list: List<Torrent>) {
        val trackerMap = mutableMapOf<String, Int>()
        for (t in list) {
            val firstTrackerUrl = t.trackers?.firstOrNull()?.announce
            if (firstTrackerUrl != null) {
                val name = TrackerUtils.getTrackerNameFromUrl(firstTrackerUrl)
                if (name != null) {
                    trackerMap[name] = (trackerMap[name] ?: 0) + 1
                }
            }
        }
        _trackerData.postValue(trackerMap)
    }

    private fun applyFilterAndSearch() {
        val currentTime = System.currentTimeMillis()
        var filtered = allTorrentsRaw.filter { t ->
            when (currentFilter) {
                "Downloading" -> t.status == 3 || t.status == 4
                "Seeding" -> t.status == 6
                "Paused" -> t.status == 0
                "Active" -> {
                    val lastSeen = activeTorrentsLastSeen[t.id] ?: 0L
                    (t.status == 1 || t.status == 2) || (t.rateDownload > 0 || t.rateUpload > 0) || (currentTime - lastSeen < gracePeriodMs)
                }
                "Inactive" -> {
                    val lastSeen = activeTorrentsLastSeen[t.id] ?: 0L
                    (t.status != 1 && t.status != 2) && (t.rateDownload <= 0 && t.rateUpload <= 0) && (currentTime - lastSeen >= gracePeriodMs)
                }
                "Error" -> t.error != 0 || (t.errorString.isNotEmpty() && !t.errorString.contains("none", ignoreCase = true))
                else -> if (currentFilter.startsWith("tracker:")) {
                    val trackerName = currentFilter.substringAfter("tracker:")
                    t.trackers?.any { TrackerUtils.getTrackerNameFromUrl(it.announce) == trackerName } == true
                } else true
            }
        }

        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        val sorted = filtered.sortedByDescending { it.addedDate }
        _torrents.postValue(sorted)
        _totalSize.postValue(filtered.sumOf { it.totalSize })
    }

    private fun updateDrawerData(list: List<Torrent>) {
        val currentTime = System.currentTimeMillis()
        val data = mutableMapOf<String, DrawerItemData>()
        
        fun getCategoryData(predicate: (Torrent) -> Boolean) = list.filter(predicate).let { 
            DrawerItemData(it.size, it.sumOf { t -> t.totalSize }) 
        }

        data["All"] = DrawerItemData(list.size, list.sumOf { it.totalSize })
        data["Downloading"] = getCategoryData { it.status == 3 || it.status == 4 }
        data["Seeding"] = getCategoryData { it.status == 6 }
        data["Paused"] = getCategoryData { it.status == 0 }
        data["Active"] = getCategoryData { t ->
            val lastSeen = activeTorrentsLastSeen[t.id] ?: 0L
            (t.status == 1 || t.status == 2) || (t.rateDownload > 0 || t.rateUpload > 0) || (currentTime - lastSeen < gracePeriodMs)
        }
        data["Inactive"] = getCategoryData { t ->
            val lastSeen = activeTorrentsLastSeen[t.id] ?: 0L
            (t.status != 1 && t.status != 2) && (t.rateDownload <= 0 && t.rateUpload <= 0) && (currentTime - lastSeen >= gracePeriodMs)
        }
        data["Error"] = getCategoryData { it.error != 0 || it.errorString.isNotEmpty() && !it.errorString.contains("none", ignoreCase = true) }
        
        _drawerData.postValue(data)
    }

    fun updateFreeSpace(rpcUrl: String, user: String, pass: String) {
        val path = allTorrentsRaw.firstOrNull()?.downloadDir ?: return
        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        service.rpc(rpcUrl, null, RpcRequest("free-space", mapOf("path" to path))).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (response.isSuccessful) {
                    val size = (response.body()?.arguments?.get("size-bytes") as? Number)?.toLong() ?: 0L
                    _freeSpace.postValue(formatSize(size))
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
        })
    }

    fun checkAltSpeedStatus(rpcUrl: String, user: String, pass: String) {
        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        service.rpc(rpcUrl, null, RpcRequest("session-get")).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (response.isSuccessful) {
                    val enabled = response.body()?.arguments?.get("alt-speed-enabled") as? Boolean ?: false
                    _altSpeedEnabled.postValue(enabled)
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
        })
    }

    fun toggleAltSpeedLimits(rpcUrl: String, user: String, pass: String) {
        val currentlyEnabled = _altSpeedEnabled.value ?: false
        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        service.rpc(rpcUrl, null, RpcRequest("session-set", mapOf("alt-speed-enabled" to !currentlyEnabled)))
            .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful) {
                        _altSpeedEnabled.postValue(!currentlyEnabled)
                    }
                }
                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
            })
    }

    fun performBatchAction(rpcUrl: String, user: String, pass: String, method: String, ids: List<Int>, onSuccess: () -> Unit = {}) {
        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        service.rpc(rpcUrl, null, RpcRequest(method, mapOf("ids" to ids))).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (response.isSuccessful) {
                    onSuccess()
                    refreshTorrents(null, rpcUrl, user, pass)
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
        })
    }

    fun setLabels(rpcUrl: String, user: String, pass: String, ids: List<Int>, labels: List<String>, onSuccess: () -> Unit = {}) {
        val service = TransmissionClient.getService(rpcUrl.substringBefore("/transmission/rpc") + "/", user, pass)
        service.rpc(rpcUrl, null, RpcRequest("torrent-set", mapOf("ids" to ids, "labels" to labels)))
            .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful) {
                        onSuccess()
                        refreshTorrents(null, rpcUrl, user, pass)
                    }
                }
                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
            })
    }

    fun formatSpeed(rate: Double): String {
        val kbs = rate / 1024.0
        return if (kbs < 1024) String.format(Locale.US, "%.1f KB/s", kbs)
        else String.format(Locale.US, "%.1f MB/s", kbs / 1024.0)
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }
}
