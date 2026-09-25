package com.kuangru52.transsync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class TorrentListViewModel(application: Application) : AndroidViewModel(application) {

    private val _torrents = MutableLiveData<List<Torrent>>(emptyList())
    val torrents: LiveData<List<Torrent>> = _torrents

    private val _drawerData = MutableLiveData<Map<String, DrawerItemData>>(emptyMap())
    val drawerData: LiveData<Map<String, DrawerItemData>> = _drawerData

    private val _trackerData = MutableLiveData<Map<String, Int>>(emptyMap())
    val trackerData: LiveData<Map<String, Int>> = _trackerData

    private val _freeSpace = MutableLiveData("--")
    val freeSpace: LiveData<String> = _freeSpace

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _totalDownloadSpeed = MutableLiveData("0 B/s")
    val totalDownloadSpeed: LiveData<String> = _totalDownloadSpeed

    private val _totalUploadSpeed = MutableLiveData("0 B/s")
    val totalUploadSpeed: LiveData<String> = _totalUploadSpeed

    private val _totalSize = MutableLiveData(0L)
    val totalSize: LiveData<Long> = _totalSize

    private val pendingStatusLocks = ConcurrentHashMap<Int, Pair<Int, Long>>()

    private val _altSpeedEnabled = MutableLiveData(false)
    val altSpeedEnabled: LiveData<Boolean> = _altSpeedEnabled

    private val _isTrackerBlurEnabled = MutableLiveData(false)
    val isTrackerBlurEnabled: LiveData<Boolean> = _isTrackerBlurEnabled

    private val _revealedTrackerNames = MutableLiveData<Set<String>>(emptySet())
    val revealedTrackerNames: LiveData<Set<String>> = _revealedTrackerNames

    private var allTorrentsRaw: List<Torrent> = emptyList()
    private var currentFilter: String = "All"
    private var searchQuery: String = ""

    private val activeTorrentsLastSeen = mutableMapOf<Int, Long>()
    private var processingJob: Job? = null

    @Volatile private var savedRpcUrl: String = ""
    @Volatile private var savedUser: String = ""
    @Volatile private var savedPass: String = ""

    @Suppress("unused")
    fun toggleTrackerBlur() {
        val current = _isTrackerBlurEnabled.value ?: false
        val newValue = !current
        _isTrackerBlurEnabled.value = newValue
        SettingsManager.setPrivacyMode(getApplication(), newValue)
    }

    fun revealTracker(trackerName: String) {
        val currentSet = _revealedTrackerNames.value ?: emptySet()
        _revealedTrackerNames.value = currentSet + trackerName
    }

    @Suppress("unused")
    fun clearRevealedTrackers() {
        _revealedTrackerNames.value = emptySet()
    }

    fun setFilter(filter: String) {
        currentFilter = filter
        applyFilterAndSearch()
    }

    fun setSearchQuery(query: String) {
        searchQuery = query
        applyFilterAndSearch()
    }

    fun switchServer(server: ServerConfig) {
        pendingStatusLocks.clear()
        activeTorrentsLastSeen.clear()
        allTorrentsRaw = emptyList()
        _torrents.value = emptyList()
        _isLoading.value = true
        TransmissionClient.clearCache()
        QBittorrentClient.clearCache()
        refreshTorrents(server.rpcUrl, server.user, server.pass)
    }

    @Suppress("unused")
    fun clearAndRefreshTorrents(rpcUrl: String, user: String, pass: String) {
        allTorrentsRaw = emptyList()
        _torrents.value = emptyList()
        _isLoading.value = true
        refreshTorrents(rpcUrl, user, pass)
    }

    fun refreshTorrents(rpcUrl: String = "", user: String = "", pass: String = "") {
        _isTrackerBlurEnabled.value = SettingsManager.isPrivacyMode(getApplication())
        val activeServer = ServerManager.getActiveServer(getApplication()) ?: return
        val effectiveUrl = rpcUrl.ifEmpty { activeServer.rpcUrl }
        val effectiveUser = user.ifEmpty { activeServer.user }
        val effectivePass = pass.ifEmpty { activeServer.pass }
        if (effectiveUrl.isEmpty()) return

        // 兼容 qBittorrent Web API v2
        if (activeServer.clientType == ServerConfig.CLIENT_QBITTORRENT) {
            _isLoading.value = true
            val qbitService = QBittorrentClient.getService(effectiveUrl)

            val fetchQbitData = {
                qbitService.getTorrentsInfo("all").enqueue(object : Callback<List<QbitTorrentInfo>> {
                    override fun onResponse(call: Call<List<QbitTorrentInfo>>, response: Response<List<QbitTorrentInfo>>) {
                        _isLoading.value = false
                        if (response.isSuccessful) {
                            val qbitList = response.body() ?: emptyList()
                            val mappedTorrents = qbitList.map { QbitMapper.mapToTorrent(it) }
                            processTorrentsAsync(mappedTorrents)
                        }
                    }

                    override fun onFailure(call: Call<List<QbitTorrentInfo>>, t: Throwable) {
                        _isLoading.value = false
                    }
                })

                qbitService.getTransferInfo().enqueue(object : Callback<QbitTransferInfo> {
                    override fun onResponse(call: Call<QbitTransferInfo>, response: Response<QbitTransferInfo>) {
                        if (response.isSuccessful) {
                            val transfer = response.body()
                            if (transfer != null) {
                                _totalDownloadSpeed.postValue(FormatUtils.formatSpeed(transfer.dl_info_speed.toDouble()))
                                _totalUploadSpeed.postValue(FormatUtils.formatSpeed(transfer.up_info_speed.toDouble()))
                            }
                        }
                    }

                    override fun onFailure(call: Call<QbitTransferInfo>, t: Throwable) {}
                })
            }

            if (effectiveUser.isNotEmpty() || effectivePass.isNotEmpty()) {
                qbitService.login(effectiveUser, effectivePass).enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) {
                        fetchQbitData()
                    }

                    override fun onFailure(call: Call<String>, t: Throwable) {
                        fetchQbitData()
                    }
                })
            } else {
                fetchQbitData()
            }
            return
        }

        _isLoading.value = true
        val service = TransmissionClient.getService(effectiveUrl, effectiveUser, effectivePass)
        val fields = listOf(
            "id", "name", "status", "percentDone", "rateDownload", "rateUpload",
            "totalSize", "sizeWhenDone", "leftUntilDone", "error", "errorString",
            "addedDate", "doneDate", "activityDate", "secondsSeeding",
            "downloadedEver", "uploadedEver", "uploadRatio", "recheckProgress",
            "eta", "downloadDir", "trackers", "trackerStats", "labels"
        )

        service.getTorrents(effectiveUrl, null, RpcRequest("torrent-get", mapOf("fields" to fields))).enqueue(object : Callback<RpcResponse<TorrentListArguments>> {
            override fun onResponse(call: Call<RpcResponse<TorrentListArguments>>, response: Response<RpcResponse<TorrentListArguments>>) {
                _isLoading.value = false
                if (response.isSuccessful) {
                    processTorrentsAsync(response.body()?.arguments?.torrents)
                    updateFreeSpace(effectiveUrl, effectiveUser, effectivePass)
                    checkAltSpeedStatus(effectiveUrl, effectiveUser, effectivePass)
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

            val customTrackerMappings = SettingsManager.getCustomTrackerMappings(getApplication())

            list.forEach { torrent ->
                totalDown += torrent.rateDownload
                totalUp += torrent.rateUpload

                if ((torrent.rateDownload > 0) || (torrent.rateUpload > 0)) {
                    activeTorrentsLastSeen[torrent.id] = currentTime
                }

                torrent.displayDownloadSpeed = "${FormatUtils.formatSpeed(torrent.rateDownload.toDouble())}\u00A0↓"
                torrent.displayUploadSpeed = "${FormatUtils.formatSpeed(torrent.rateUpload.toDouble())}\u00A0↑"

                torrent.displayProgress = if ((torrent.status == 1) || (torrent.status == 2))
                    (torrent.recheckProgress * 1000).toInt() else (torrent.percentDone * 1000).toInt()

                torrent.displayStatusText = if ((torrent.status == 1) || (torrent.status == 2))
                    "校验中 (${String.format(Locale.US, "%.1f%%", torrent.recheckProgress * 100)})" else ""

                val sizeStr = FormatUtils.formatSize(torrent.totalSize)
                torrent.displaySize = if (torrent.percentDone >= 1.0) sizeStr else "${FormatUtils.formatSize(torrent.downloadedEver)} / $sizeStr"
                torrent.displayStats = "${FormatUtils.formatSize(torrent.uploadedEver)} (分享率 ${String.format(Locale.US, "%.2f", torrent.uploadRatio)})"

                val firstTrackerUrl = torrent.trackers?.firstOrNull()?.announce
                val mappedTrackerName = if (!firstTrackerUrl.isNullOrEmpty()) {
                    TrackerUtils.getTrackerNameFromUrl(firstTrackerUrl, customTrackerMappings) ?: ""
                } else ""
                torrent.trackerName = mappedTrackerName

                if (mappedTrackerName.isNotEmpty()) {
                    trackerMap[mappedTrackerName] = (trackerMap[mappedTrackerName] ?: 0) + 1
                }

                // 极速无缝体验：判定 4.5 秒状态锁，仅锁定播放/暂停按钮图标状态，进度条与数据全量保留真实实时计算结果！
                val lock = pendingStatusLocks[torrent.id]
                if (lock != null) {
                    val (lockedStatus, expireTime) = lock
                    if (currentTime < expireTime) {
                        val isServerStatusMatching = when (lockedStatus) {
                            0 -> torrent.status == 0
                            4 -> (torrent.status == 3 || torrent.status == 4 || torrent.status == 5 || torrent.status == 6)
                            else -> torrent.status == lockedStatus
                        }
                        if (isServerStatusMatching) {
                            pendingStatusLocks.remove(torrent.id)
                        } else {
                            torrent.status = lockedStatus
                        }
                    } else {
                        pendingStatusLocks.remove(torrent.id)
                    }
                }

                allCount++; allSize += torrent.totalSize
                val isError = torrent.error != 0 || (torrent.errorString.isNotEmpty() && !torrent.errorString.contains("none", ignoreCase = true))
                val isDownloading = torrent.status == 3 || torrent.status == 4
                val isSeeding = torrent.status == 5 || torrent.status == 6
                val isPaused = torrent.status == 0

                if (isDownloading) { downCount++; downSize += torrent.totalSize }
                if (isSeeding) { seedCount++; seedSize += torrent.totalSize }
                if (isPaused) { pausedCount++; pausedSize += torrent.totalSize }

                val lastSeen = activeTorrentsLastSeen[torrent.id] ?: 0L
                val isActive = (currentTime - lastSeen) <= 10000L
                if (isActive) { activeCount++; activeSize += torrent.totalSize }
                else { inactiveCount++; inactiveSize += torrent.totalSize }

                if (isError) { errorCount++; errorSize += torrent.totalSize }
            }

            allTorrentsRaw = list

            val newDrawerData = mapOf(
                "All" to DrawerItemData(allCount, allSize),
                "Downloading" to DrawerItemData(downCount, downSize),
                "Seeding" to DrawerItemData(seedCount, seedSize),
                "Paused" to DrawerItemData(pausedCount, pausedSize),
                "Active" to DrawerItemData(activeCount, activeSize),
                "Inactive" to DrawerItemData(inactiveCount, inactiveSize),
                "Error" to DrawerItemData(errorCount, errorSize)
            )

            _totalDownloadSpeed.postValue(FormatUtils.formatSpeed(totalDown))
            _totalUploadSpeed.postValue(FormatUtils.formatSpeed(totalUp))
            _drawerData.postValue(newDrawerData)
            _trackerData.postValue(trackerMap)

            applyFilterAndSearch()
        }
    }

    private fun applyFilterAndSearch() {
        val currentTime = System.currentTimeMillis()
        var filtered = when (currentFilter) {
            "Downloading" -> allTorrentsRaw.filter { it.status == 3 || it.status == 4 }
            "Seeding" -> allTorrentsRaw.filter { it.status == 5 || it.status == 6 }
            "Paused" -> allTorrentsRaw.filter { it.status == 0 }
            "Active" -> allTorrentsRaw.filter { (currentTime - (activeTorrentsLastSeen[it.id] ?: 0L)) <= 10000L }
            "Inactive" -> allTorrentsRaw.filter { (currentTime - (activeTorrentsLastSeen[it.id] ?: 0L)) > 10000L }
            "Error" -> allTorrentsRaw.filter { it.error != 0 || (it.errorString.isNotEmpty() && !it.errorString.contains("none", ignoreCase = true)) }
            else -> {
                if (currentFilter.startsWith("tracker:")) {
                    val trackerName = currentFilter.substringAfter("tracker:")
                    allTorrentsRaw.filter { it.trackerName == trackerName }
                } else {
                    allTorrentsRaw
                }
            }
        }

        if (searchQuery.isNotEmpty()) {
            val q = searchQuery.lowercase()
            filtered = filtered.filter {
                it.name.lowercase().contains(q) ||
                it.trackerName.lowercase().contains(q) ||
                it.displaySize.lowercase().contains(q)
            }
        }

        // 默认按最近添加时间降序排序 (最近添加的种子排在最顶部)
        filtered = filtered.sortedWith(
            compareByDescending<Torrent> { it.addedDate }
                .thenByDescending { it.id }
        )

        var filterTotalSize = 0L
        filtered.forEach { filterTotalSize += it.totalSize }
        _totalSize.postValue(filterTotalSize)

        _torrents.postValue(filtered)
    }

    private fun updateFreeSpace(rpcUrl: String, user: String, pass: String) {
        val service = TransmissionClient.getService(rpcUrl, user, pass)
        val downloadDir = allTorrentsRaw.firstOrNull()?.downloadDir ?: "/downloads"
        service.rpc(rpcUrl, null, RpcRequest("free-space", mapOf("path" to downloadDir)))
            .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful) {
                        val bytes = (response.body()?.arguments?.get("size-bytes") as? Double)?.toLong() ?: 0L
                        _freeSpace.postValue(FormatUtils.formatSize(bytes))
                    }
                }
                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
            })
    }

    private fun checkAltSpeedStatus(rpcUrl: String, user: String, pass: String) {
        val activeServer = ServerManager.getActiveServer(getApplication())
        val effectiveUrl = rpcUrl.ifEmpty { activeServer?.rpcUrl ?: "" }
        val effectiveUser = user.ifEmpty { activeServer?.user ?: "" }
        val effectivePass = pass.ifEmpty { activeServer?.pass ?: "" }
        if (effectiveUrl.isEmpty()) return

        if (activeServer?.clientType == ServerConfig.CLIENT_QBITTORRENT) {
            val qbitService = QBittorrentClient.getService(effectiveUrl)
            val fetchSpeedMode = {
                qbitService.getSpeedLimitsMode().enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) {
                        if (response.isSuccessful) {
                            val body = response.body()?.trim() ?: "0"
                            _altSpeedEnabled.postValue(body == "1")
                        }
                    }
                    override fun onFailure(call: Call<String>, t: Throwable) {}
                })
            }

            if (effectiveUser.isNotEmpty() || effectivePass.isNotEmpty()) {
                qbitService.login(effectiveUser, effectivePass).enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) { fetchSpeedMode() }
                    override fun onFailure(call: Call<String>, t: Throwable) { fetchSpeedMode() }
                })
            } else {
                fetchSpeedMode()
            }
            return
        }

        val service = TransmissionClient.getService(effectiveUrl, effectiveUser, effectivePass)
        service.rpc(effectiveUrl, null, RpcRequest("session-get", null))
            .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
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
        val activeServer = ServerManager.getActiveServer(getApplication())
        val effectiveUrl = rpcUrl.ifEmpty { activeServer?.rpcUrl ?: "" }
        val effectiveUser = user.ifEmpty { activeServer?.user ?: "" }
        val effectivePass = pass.ifEmpty { activeServer?.pass ?: "" }
        if (effectiveUrl.isEmpty()) return

        val currentlyEnabled = _altSpeedEnabled.value ?: false

        if (activeServer?.clientType == ServerConfig.CLIENT_QBITTORRENT) {
            val qbitService = QBittorrentClient.getService(effectiveUrl)
            val doToggle = {
                qbitService.toggleSpeedLimitsMode().enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) {
                        if (response.isSuccessful || response.code() == 200) {
                            _altSpeedEnabled.postValue(!currentlyEnabled)
                        } else {
                            checkAltSpeedStatus(effectiveUrl, effectiveUser, effectivePass)
                        }
                    }
                    override fun onFailure(call: Call<String>, t: Throwable) {
                        checkAltSpeedStatus(effectiveUrl, effectiveUser, effectivePass)
                    }
                })
            }

            if (effectiveUser.isNotEmpty() || effectivePass.isNotEmpty()) {
                qbitService.login(effectiveUser, effectivePass).enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) { doToggle() }
                    override fun onFailure(call: Call<String>, t: Throwable) { doToggle() }
                })
            } else {
                doToggle()
            }
            return
        }

        val service = TransmissionClient.getService(effectiveUrl, effectiveUser, effectivePass)
        service.rpc(effectiveUrl, null, RpcRequest("session-set", mapOf("alt-speed-enabled" to !currentlyEnabled)))
            .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful) {
                        _altSpeedEnabled.postValue(!currentlyEnabled)
                    }
                }
                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
            })
    }

    fun toggleTorrentStatus(rpcUrl: String, user: String, pass: String, torrent: Torrent) {
        val isCurrentlyPaused = torrent.status == 0
        if (isCurrentlyPaused) {
            startTorrents(rpcUrl, user, pass, listOf(torrent.id))
        } else {
            stopTorrents(rpcUrl, user, pass, listOf(torrent.id))
        }
    }

    fun startTorrents(rpcUrl: String, user: String, pass: String, ids: List<Int>) {
        val activeServer = ServerManager.getActiveServer(getApplication())
        val effectiveUrl = rpcUrl.ifEmpty { activeServer?.rpcUrl ?: "" }
        val effectiveUser = user.ifEmpty { activeServer?.user ?: "" }
        val effectivePass = pass.ifEmpty { activeServer?.pass ?: "" }

        val currentTime = System.currentTimeMillis()
        val lockExpireTime = currentTime + 4500L // 锁定 4.5 秒，保护用户开始操作不受服务器延迟冲刷
        ids.forEach { id ->
            pendingStatusLocks[id] = Pair(4, lockExpireTime)
        }

        val currentList = _torrents.value ?: emptyList()
        val updatedList = currentList.map {
            if (ids.contains(it.id)) it.copy(status = 4) else it
        }
        _torrents.value = updatedList

        performBatchAction(effectiveUrl, effectiveUser, effectivePass, "torrent-start", ids)
    }

    fun stopTorrents(rpcUrl: String, user: String, pass: String, ids: List<Int>) {
        val activeServer = ServerManager.getActiveServer(getApplication())
        val effectiveUrl = rpcUrl.ifEmpty { activeServer?.rpcUrl ?: "" }
        val effectiveUser = user.ifEmpty { activeServer?.user ?: "" }
        val effectivePass = pass.ifEmpty { activeServer?.pass ?: "" }

        val currentTime = System.currentTimeMillis()
        val lockExpireTime = currentTime + 4500L // 锁定 4.5 秒，保护用户暂停操作不受服务器延迟冲刷
        ids.forEach { id ->
            pendingStatusLocks[id] = Pair(0, lockExpireTime)
        }

        val currentList = _torrents.value ?: emptyList()
        val updatedList = currentList.map {
            if (ids.contains(it.id)) it.copy(status = 0) else it
        }
        _torrents.value = updatedList

        performBatchAction(effectiveUrl, effectiveUser, effectivePass, "torrent-stop", ids)
    }

    fun verifyTorrents(rpcUrl: String, user: String, pass: String, ids: List<Int>) {
        val activeServer = ServerManager.getActiveServer(getApplication())
        val effectiveUrl = rpcUrl.ifEmpty { activeServer?.rpcUrl ?: "" }
        val effectiveUser = user.ifEmpty { activeServer?.user ?: "" }
        val effectivePass = pass.ifEmpty { activeServer?.pass ?: "" }

        val currentList = _torrents.value ?: emptyList()
        val updatedList = currentList.map {
            if (ids.contains(it.id)) it.copy(status = 2) else it
        }
        _torrents.value = updatedList

        performBatchAction(effectiveUrl, effectiveUser, effectivePass, "torrent-verify", ids)
    }

    fun performBatchAction(rpcUrl: String, user: String, pass: String, method: String, ids: List<Int>, onSuccess: () -> Unit = {}) {
        val activeServer = ServerManager.getActiveServer(getApplication())
        val effectiveUrl = rpcUrl.ifEmpty { activeServer?.rpcUrl ?: "" }
        val effectiveUser = user.ifEmpty { activeServer?.user ?: "" }
        val effectivePass = pass.ifEmpty { activeServer?.pass ?: "" }
        if (effectiveUrl.isEmpty()) return

        if (activeServer?.clientType == ServerConfig.CLIENT_QBITTORRENT) {
            val qbitService = QBittorrentClient.getService(effectiveUrl)
            val selectedHashes = allTorrentsRaw.asSequence().filter { ids.contains(it.id) }.map { it.hash }.filter { it.isNotEmpty() }.toList()
            val hashesStr = if (selectedHashes.isNotEmpty()) selectedHashes.joinToString("|") else "all"

            val doBatch = {
                when (method) {
                    "torrent-start", "torrent-start-now" -> {
                        qbitService.resumeTorrents(hashesStr).enqueue(object : Callback<String> {
                            override fun onResponse(call: Call<String>, response: Response<String>) {
                                if (!response.isSuccessful) {
                                    qbitService.startTorrents(hashesStr).enqueue(object : Callback<String> {
                                        override fun onResponse(c: Call<String>, r: Response<String>) {
                                            onSuccess()
                                            refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                                        }
                                        override fun onFailure(c: Call<String>, t: Throwable) {
                                            refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                                        }
                                    })
                                } else {
                                    onSuccess()
                                    refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                                }
                            }
                            override fun onFailure(call: Call<String>, t: Throwable) {
                                qbitService.startTorrents(hashesStr).enqueue(object : Callback<String> {
                                    override fun onResponse(c: Call<String>, r: Response<String>) {
                                        onSuccess()
                                        refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                                    }
                                    override fun onFailure(c: Call<String>, t: Throwable) {
                                        refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                                    }
                                })
                            }
                        })
                    }
                    "torrent-stop" -> {
                        qbitService.pauseTorrents(hashesStr).enqueue(object : Callback<String> {
                            override fun onResponse(call: Call<String>, response: Response<String>) {
                                if (!response.isSuccessful) {
                                    qbitService.stopTorrents(hashesStr).enqueue(object : Callback<String> {
                                        override fun onResponse(c: Call<String>, r: Response<String>) {
                                            onSuccess()
                                            refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                                        }
                                        override fun onFailure(c: Call<String>, t: Throwable) {
                                            refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                                        }
                                    })
                                } else {
                                    onSuccess()
                                    refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                                }
                            }
                            override fun onFailure(call: Call<String>, t: Throwable) {
                                qbitService.stopTorrents(hashesStr).enqueue(object : Callback<String> {
                                    override fun onResponse(c: Call<String>, r: Response<String>) {
                                        onSuccess()
                                        refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                                    }
                                    override fun onFailure(c: Call<String>, t: Throwable) {
                                        refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                                    }
                                })
                            }
                        })
                    }
                    "torrent-verify" -> {
                        qbitService.recheckTorrents(hashesStr).enqueue(object : Callback<String> {
                            override fun onResponse(call: Call<String>, response: Response<String>) {
                                onSuccess()
                                refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                            }
                            override fun onFailure(call: Call<String>, t: Throwable) {
                                refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                            }
                        })
                    }
                    else -> {
                        qbitService.resumeTorrents(hashesStr).enqueue(object : Callback<String> {
                            override fun onResponse(call: Call<String>, response: Response<String>) {
                                onSuccess()
                                refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                            }
                            override fun onFailure(call: Call<String>, t: Throwable) {
                                refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                            }
                        })
                    }
                }
            }

            if (effectiveUser.isNotEmpty() || effectivePass.isNotEmpty()) {
                qbitService.login(effectiveUser, effectivePass).enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) { doBatch() }
                    override fun onFailure(call: Call<String>, t: Throwable) { doBatch() }
                })
            } else {
                doBatch()
            }
            return
        }

        val service = TransmissionClient.getService(effectiveUrl, effectiveUser, effectivePass)
        service.rpc(effectiveUrl, null, RpcRequest(method, mapOf("ids" to ids))).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (response.isSuccessful && response.body()?.result == "success") {
                    onSuccess()
                    refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                } else {
                    refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
            }
        })
    }

    @Suppress("unused")
    fun setLabels(rpcUrl: String, user: String, pass: String, ids: List<Int>, labels: List<String>, onSuccess: () -> Unit = {}) {
        val effectiveUrl = rpcUrl.ifEmpty { savedRpcUrl }
        val effectiveUser = user.ifEmpty { savedUser }
        val effectivePass = pass.ifEmpty { savedPass }
        if (effectiveUrl.isEmpty()) return

        val service = TransmissionClient.getService(effectiveUrl, effectiveUser, effectivePass)
        service.rpc(effectiveUrl, null, RpcRequest("torrent-set", mapOf("ids" to ids, "labels" to labels)))
            .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful && response.body()?.result == "success") {
                        onSuccess()
                        refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                    }
                }
                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
            })
    }

    private var lastActionTime = 0L
    private val actionDebounceMs = 500L

    fun reannounceTorrents(rpcUrl: String, user: String, pass: String, ids: List<Int>, onSuccess: () -> Unit = {}) {
        val effectiveUrl = rpcUrl.ifEmpty { savedRpcUrl }
        val effectiveUser = user.ifEmpty { savedUser }
        val effectivePass = pass.ifEmpty { savedPass }
        if (effectiveUrl.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActionTime < actionDebounceMs) return
        lastActionTime = currentTime

        val activeServer = ServerManager.getActiveServer(getApplication())
        if (activeServer?.clientType == ServerConfig.CLIENT_QBITTORRENT) {
            val qbitService = QBittorrentClient.getService(effectiveUrl)
            val selectedHashes = allTorrentsRaw.asSequence().filter { ids.contains(it.id) }.map { it.hash }.filter { it.isNotEmpty() }.toList()
            val hashesStr = if (selectedHashes.isNotEmpty()) selectedHashes.joinToString("|") else "all"

            val doReannounce = {
                qbitService.reannounceTorrents(hashesStr).enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) {
                        onSuccess()
                        refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                    }
                    override fun onFailure(call: Call<String>, t: Throwable) {
                        refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                    }
                })
            }

            if (effectiveUser.isNotEmpty() || effectivePass.isNotEmpty()) {
                qbitService.login(effectiveUser, effectivePass).enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) { doReannounce() }
                    override fun onFailure(call: Call<String>, t: Throwable) { doReannounce() }
                })
            } else {
                doReannounce()
            }
            return
        }

        val service = TransmissionClient.getService(effectiveUrl, effectiveUser, effectivePass)
        service.rpc(effectiveUrl, null, RpcRequest("torrent-reannounce", mapOf("ids" to ids)))
            .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful && response.body()?.result == "success") {
                        onSuccess()
                        refreshTorrents(effectiveUrl, effectiveUser, effectivePass)
                    }
                }
                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {}
            })
    }

}
