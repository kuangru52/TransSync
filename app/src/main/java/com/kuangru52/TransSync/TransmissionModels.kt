package com.kuangru52.transsync

import com.google.gson.annotations.SerializedName

data class RpcRequest(
    val method: String,
    val arguments: Map<String, Any>? = null,
    val tag: Int? = null
)

data class RpcResponse<T>(
    val result: String,
    val arguments: T,
    val tag: Int?
)

data class TorrentListArguments(
    val torrents: List<Torrent>
)

data class Torrent(
    val id: Int = 0,
    val name: String = "",
    val status: Int = 0,
    val percentDone: Double = 0.0,
    val rateDownload: Long = 0,
    val rateUpload: Long = 0,
    val totalSize: Long = 0,
    val sizeWhenDone: Long = 0,
    val leftUntilDone: Long = 0,
    val error: Int = 0,
    val errorString: String = "",
    val addedDate: Long = 0,
    val doneDate: Long = 0,
    val activityDate: Long = 0,
    val secondsSeeding: Long = 0,
    val downloadedEver: Long = 0,
    val uploadedEver: Long = 0,
    val uploadRatio: Double = 0.0,
    val recheckProgress: Double = 0.0,
    val eta: Long? = null,
    val downloadDir: String? = null,
    val trackers: List<Tracker>? = null,
    val trackerStats: List<TrackerStats>? = null,
    val peers: List<Peer>? = null,
    val files: List<TorrentFile>? = null,
    val labels: List<String>? = null,

    // 预计算字段，用于 UI 极速渲染
    var displaySize: String = "",
    var displayStatusText: String = "",
    var displayDownloadSpeed: String = "",
    var displayUploadSpeed: String = "",
    var displayStats: String = "",
    var displayProgress: Int = 0,
    var displayColor: Int = 0
)

data class TorrentFile(
    val name: String = "",
    val length: Long = 0,
    val bytesCompleted: Long = 0
)

data class TrackerStats(
    val announce: String = "",
    val seederCount: Int = 0,
    val leecherCount: Int = 0,
    val downloadCount: Int = 0,
    val hasScraped: Boolean = false,
    val lastScrapeResult: String = ""
)

data class Peer(
    val address: String = "",
    val clientName: String = "",
    val flagStr: String = "",
    val isUTP: Boolean = false,
    val rateToClient: Int = 0,
    val rateToPeer: Int = 0,
    val progress: Double = 0.0
)

data class Tracker(
    val announce: String = "",
    val id: Int = 0,
    val scrape: String = "",
    val tier: Int = 0
)

data class FreeSpaceArguments(
    val path: String,
    @SerializedName("size-bytes")
    val sizeBytes: Long
)
