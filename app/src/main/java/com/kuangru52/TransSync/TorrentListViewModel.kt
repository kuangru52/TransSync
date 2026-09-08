package com.kuangru52.transsync

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
    
    val isTrackerBlurEnabled = MutableLiveData<Boolean>(false)
    private val _revealedTrackerNames = MutableLiveData<Set<String>>(emptySet())
    val revealedTrackerNames: LiveData<Set<String>> = _revealedTrackerNames

    private val _freeSpace = MutableLiveData<String>()
    val freeSpace: LiveData<String> = _freeSpace

    private val _altSpeedEnabled = MutableLiveData<Boolean>()
    val altSpeedEnabled: LiveData<Boolean> = _altSpeedEnabled

    @Volatile private var allTorrentsRaw: List<Torrent> = emptyList()
    private val activeTorrentsLastSeen = ConcurrentHashMap<Int, Long>()
    private val gracePeriodMs = 30000L

    @Volatile private var currentFilter: String = "All"
    @Volatile private var searchQuery: String = ""
    private val _searchQuery = MutableLiveData<String>("")
    val searchResultCount = MutableLiveData<Int>()
    
    private var processingJob: Job? = null

    fun setFilter(filter: String) {
        currentFilter = filter
        applyFilterAndSearch()
    }

    private var searchJob: Job? = null

    fun setSearchQuery(query: String) {
        if (searchQuery == query) return
        
        searchQuery = query
        _searchQuery.value = query
        
        searchJob?.cancel()
        if (query.isEmpty()) {
            applyFilterAndSearch()
        } else {
            searchJob = viewModelScope.launch {
                delay(300) // Debounce search
                if (isActive) {
                    applyFilterAndSearch()
                }
            }
        }
    }

    fun toggleTrackerBlur() {
        val currentValue = isTrackerBlurEnabled.value ?: false
        isTrackerBlurEnabled.value = !currentValue
        if (isTrackerBlurEnabled.value == true) {
            _revealedTrackerNames.value = emptySet()
        }
    }

    fun revealTracker(name: String) {
        val current = _revealedTrackerNames.value ?: emptySet()
        _revealedTrackerNames.value = current + name
    }

    fun clearRevealedTrackers() {
        _revealedTrackerNames.value = emptySet()
    }

    fun refreshTorrents(rpcUrl: String, user: String, pass: String) {
        if (rpcUrl.isEmpty()) return
        
        val service = TransmissionClient.getService(rpcUrl, user, pass)
        val fields = listOf(
            "id", "name", "status", "percentDone", "rateDownload", "rateUpload",
            "totalSize", "sizeWhenDone", "leftUntilDone", "error", "errorString",
            "addedDate", "doneDate", "activityDate", "secondsSeeding",
            "downloadedEver", "uploadedEver", "uploadRatio", "eta", "downloadDir",
            "trackers", "trackerStats", "labels", "recheckProgress"
        )
        val request = RpcRequest("torrent-get", mapOf("fields" to fields))

        service.getTorrents(rpcUrl, null, request).enqueue(object : Callback<RpcResponse<TorrentListArguments>> {
            override fun onResponse(call: Call<RpcResponse<TorrentListArguments>>, response: Response<RpcResponse<TorrentListArguments>>) {
                _isLoading.value = false
                if (response.isSuccessful) {
                    processTorrentsAsync(response.body()?.arguments?.torrents)
                    updateFreeSpace(rpcUrl, user, pass)
                    checkAltSpeedStatus(rpcUrl, user, pass)
                }
            }

            override fun onFailure(call: Call<RpcResponse<TorrentListArguments>>, t: Throwable) {
                _isLoading.value = false
            }
        })
    }

    private fun processTorrentsAsync(list: List<Torrent>?) {
        processingJob?.cancel()
        processingJob = viewModelScope.launch(Dispatchers.Default) {
            if (list == null) return@launch

            val currentTime = System.currentTimeMillis()
            var totalDown = 0.0
            var totalUp = 0.0
            
            val trackerMap = mutableMapOf<String, Int>()
            var allCount = 0; var allSize = 0L
            var downCount = 0; var downSize = 0L
            var seedCount = 0; var seedSize = 0L
            var pausedCount = 0; var pausedSize = 0L
            var activeCount = 0; var activeSize = 0L
            var inactiveCount = 0; var inactiveSize = 0L
            var errorCount = 0; var errorSize = 0L

            list.forEach { torrent ->
                totalDown += torrent.rateDownload
                totalUp += torrent.rateUpload
                
                if (torrent.rateDownload > 0 || torrent.rateUpload > 0) {
                    activeTorrentsLastSeen[torrent.id] = currentTime
                }

                // 预计算 UI 字段
                torrent.displayDownloadSpeed = "${FormatUtils.formatSpeed(torrent.rateDownload.toDouble())}\u00A0↓"
                torrent.displayUploadSpeed = "${FormatUtils.formatSpeed(torrent.rateUpload.toDouble())}\u00A0↑"
                
                torrent.displayProgress = if (torrent.status == 1 || torrent.status == 2) 
                    (torrent.recheckProgress * 1000).toInt() else (torrent.percentDone * 1000).toInt()
                
                torrent.displayStatusText = if (torrent.status == 1 || torrent.status == 2) 
                    "校验中 (${String.format(Locale.US, "%.1f%%", torrent.recheckProgress * 100)})" else ""

                val sizeStr = FormatUtils.formatSize(torrent.totalSize)
                torrent.displaySize = if (torrent.percentDone >= 1.0) sizeStr else "${FormatUtils.formatSize(torrent.downloadedEver)} / $sizeStr"
                torrent.displayStats = "${FormatUtils.formatSize(torrent.uploadedEver)} (分享率 ${String.format(Locale.US, "%.2f", torrent.uploadRatio)})"

                // Tracker 数据统计
                val firstTrackerUrl = torrent.trackers?.firstOrNull()?.announce
                val tName = if (firstTrackerUrl != null) TrackerUtils.getTrackerNameFromUrl(firstTrackerUrl) ?: "" else ""
                torrent.trackerName = tName
                if (tName.isNotEmpty()) {
                    trackerMap[tName] = (trackerMap[tName] ?: 0) + 1
                }

                // Drawer 类别统计
                val size = torrent.totalSize
                allCount++; allSize += size
                when {
                    torrent.status == 3 || torrent.status == 4 -> { downCount++; downSize += size }
                    torrent.status == 6 -> { seedCount++; seedSize += size }
                    torrent.status == 0 -> { pausedCount++; pausedSize += size }
                }

                val lastSeen = activeTorrentsLastSeen[torrent.id] ?: 0L
                val isActive = (torrent.status == 1 || torrent.status == 2) || 
                               (torrent.rateDownload > 0 || torrent.rateUpload > 0) || 
                               (currentTime - lastSeen < gracePeriodMs)
                
                if (isActive) {
                    activeCount++; activeSize += size
                } else {
                    inactiveCount++; inactiveSize += size
                }

                if (torrent.error != 0 || (torrent.errorString.isNotEmpty() && !torrent.errorString.contains("none", ignoreCase = true))) {
                    errorCount++; errorSize += size
                }
            }
            
            allTorrentsRaw = list
            
            // 批量更新 LiveData
            _totalDownloadSpeed.postValue(FormatUtils.formatSpeed(totalDown))
            _totalUploadSpeed.postValue(FormatUtils.formatSpeed(totalUp))
            _trackerData.postValue(trackerMap)
            _drawerData.postValue(mapOf(
                "All" to DrawerItemData(allCount, allSize),
                "Downloading" to DrawerItemData(downCount, downSize),
                "Seeding" to DrawerItemData(seedCount, seedSize),
                "Paused" to DrawerItemData(pausedCount, pausedSize),
                "Active" to DrawerItemData(activeCount, activeSize),
                "Inactive" to DrawerItemData(inactiveCount, inactiveSize),
                "Error" to DrawerItemData(errorCount, errorSize)
            ))
            
            applyFilterAndSearch()
        }
    }


    private var filterJob: Job? = null

    private fun applyFilterAndSearch() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            val list = allTorrentsRaw
            if (list.isEmpty() && searchQuery.isEmpty()) {
                _torrents.postValue(emptyList())
                _totalSize.postValue(0L)
                searchResultCount.postValue(0)
                return@launch
            }

            val currentTime = System.currentTimeMillis()
            val query = searchQuery
            val filter = currentFilter
            
            if (!isActive) return@launch

            val filtered = list.filter { t ->
                val matchesFilter = when (filter) {
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
                    else -> if (filter.startsWith("tracker:")) {
                        val trackerName = filter.substringAfter("tracker:")
                        t.trackerName == trackerName
                    } else true
                }
                
                val matchesQuery = if (query.isEmpty()) true else t.name.contains(query, ignoreCase = true)
                
                matchesFilter && matchesQuery
            }

            if (!isActive) return@launch

            val sorted = filtered.sortedByDescending { t -> t.addedDate }
            
            _torrents.postValue(sorted)
            _totalSize.postValue(filtered.sumOf { it.totalSize })
            searchResultCount.postValue(filtered.size)
        }
    }


    fun updateFreeSpace(rpcUrl: String, user: String, pass: String) {
        val path = allTorrentsRaw.firstOrNull()?.downloadDir ?: return
        val service = TransmissionClient.getService(rpcUrl, user, pass)
        service.rpc(rpcUrl, null, RpcRequest("free-space", mapOf("path" to path))).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (response.isSuccessful) {
                    val size = (response.body()?.arguments?.get("size-bytes") as? Number)?.toLong() ?: 0L
                    _freeSpace.postValue(FormatUtils.formatSize(size))
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
        })
    }

    fun checkAltSpeedStatus(rpcUrl: String, user: String, pass: String) {
        val service = TransmissionClient.getService(rpcUrl, user, pass)
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
        val service = TransmissionClient.getService(rpcUrl, user, pass)
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
        val service = TransmissionClient.getService(rpcUrl, user, pass)
        service.rpc(rpcUrl, null, RpcRequest(method, mapOf("ids" to ids))).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (response.isSuccessful && response.body()?.result == "success") {
                    onSuccess()
                    refreshTorrents(rpcUrl, user, pass)
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
        })
    }

    fun setLabels(rpcUrl: String, user: String, pass: String, ids: List<Int>, labels: List<String>, onSuccess: () -> Unit = {}) {
        val service = TransmissionClient.getService(rpcUrl, user, pass)
        service.rpc(rpcUrl, null, RpcRequest("torrent-set", mapOf("ids" to ids, "labels" to labels)))
            .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful && response.body()?.result == "success") {
                        onSuccess()
                        refreshTorrents(rpcUrl, user, pass)
                    }
                }
                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
            })
    }

    private var lastActionTime = 0L
    private val ACTION_DEBOUNCE_MS = 500L

    fun reannounceTorrents(rpcUrl: String, user: String, pass: String, ids: List<Int>, onSuccess: () -> Unit = {}) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActionTime < ACTION_DEBOUNCE_MS) return
        lastActionTime = currentTime

        val service = TransmissionClient.getService(rpcUrl, user, pass)
        service.rpc(rpcUrl, null, RpcRequest("torrent-reannounce", mapOf("ids" to ids)))
            .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful && response.body()?.result == "success") {
                        onSuccess()
                        refreshTorrents(rpcUrl, user, pass)
                    }
                }
                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
            })
    }

}
