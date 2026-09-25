package com.kuangru52.transsync

import com.google.gson.annotations.SerializedName

/**
 * qBittorrent Web API v2 实体与映射器
 */
@Suppress("PropertyName")
data class QbitTorrentInfo(
    @SerializedName("hash") val hash: String,
    @SerializedName("name") val name: String,
    @SerializedName("state") val state: String,
    @SerializedName("progress") val progress: Double = 0.0,
    @SerializedName("dlspeed") val dlspeed: Long = 0L,
    @SerializedName("upspeed") val upspeed: Long = 0L,
    @SerializedName("size") val size: Long = 0L,
    @SerializedName("completed") val completed: Long = 0L,
    @SerializedName("uploaded") val uploaded: Long = 0L,
    @SerializedName("ratio") val ratio: Double = 0.0,
    @SerializedName("save_path") val save_path: String = "",
    @SerializedName("tracker") val tracker: String? = null,
    @SerializedName("seeding_time") val seeding_time: Long? = 0L,
    @SerializedName("eta") val eta: Long = -1L,
    @SerializedName("category") val category: String? = null,
    @SerializedName("tags") val tags: String? = null,
    @SerializedName("added_on") val added_on: Long? = null,
    @SerializedName("completion_on") val completion_on: Long? = null,
)

@Suppress("PropertyName")
data class QbitTransferInfo(
    @SerializedName("dl_info_speed") val dl_info_speed: Long = 0L,
    @SerializedName("up_info_speed") val up_info_speed: Long = 0L,
)

@Suppress("PropertyName")
data class QbitPeerInfo(
    @SerializedName("client") val client: String? = "",
    @SerializedName("progress") val progress: Double? = 0.0,
    @SerializedName("dl_speed") val dl_speed: Int? = 0,
    @SerializedName("up_speed") val up_speed: Int? = 0,
    @SerializedName("ip") val ip: String? = "",
    @SerializedName("port") val port: Int? = 0,
    @SerializedName("flags") val flags: String? = "",
)

data class QbitPeersResponse(
    @SerializedName("peers") val peers: Map<String, QbitPeerInfo>? = null,
)

@Suppress("PropertyName")
data class QbitTrackerItem(
    @SerializedName("url") val url: String? = "",
    @SerializedName("status") val status: Int? = 0,
    @SerializedName("num_peers") val num_peers: Int? = 0,
    @SerializedName("num_seeds") val num_seeds: Int? = 0,
    @SerializedName("num_leeches") val num_leeches: Int? = 0,
    @SerializedName("num_downloaded") val num_downloaded: Int? = 0,
    @SerializedName("msg") val msg: String? = "",
)

data class QbitFileInfo(
    @SerializedName("name") val name: String? = "",
    @SerializedName("size") val size: Long? = 0L,
    @SerializedName("progress") val progress: Double? = 0.0,
    @SerializedName("priority") val priority: Int? = 1,
)

object QbitMapper {
    fun mapToTorrent(qbit: QbitTorrentInfo): Torrent {
        val mappedStatus = when (qbit.state) {
            "downloading", "stalledDL", "metaDL", "queuedDL", "allocating", "forcedDL" -> 4 // Transmission 4 = Downloading
            "uploading", "stalledUP", "queuedUP", "forcedUP" -> 6 // Transmission 6 = Seeding
            "pausedDL", "pausedUP" -> 0 // Transmission 0 = Stopped
            "checkingDL", "checkingUP", "checkingResumeData" -> 2 // Transmission 2 = Check
            else -> 0
        }

        // 用 hash 的 hashCode 作为 int id 保持组件通用
        val torrentId = qbit.hash.lowercase().hashCode()

        val cleanTracker = qbit.tracker?.takeIf {
            it.isNotBlank() && !it.startsWith("**") && !it.contains("[DHT]") && !it.contains("[PeX]") && !it.contains("[LSD]")
        }
        val trackersList = cleanTracker?.let {
            listOf(Tracker(announce = it, id = it.hashCode()))
        }

        return Torrent(
            id = torrentId,
            hash = qbit.hash,
            name = qbit.name,
            status = mappedStatus,
            percentDone = qbit.progress,
            rateDownload = qbit.dlspeed,
            rateUpload = qbit.upspeed,
            totalSize = qbit.size,
            downloadedEver = qbit.completed,
            uploadedEver = qbit.uploaded,
            uploadRatio = qbit.ratio,
            downloadDir = qbit.save_path,
            trackers = trackersList,
            eta = qbit.eta,
            secondsSeeding = qbit.seeding_time ?: 0L,
            addedDate = qbit.added_on ?: 0L,
            doneDate = qbit.completion_on ?: 0L,
            labels = buildList {
                qbit.category?.takeIf { it.isNotBlank() }?.let { add(it) }
                qbit.tags?.split(",")?.filter { it.isNotBlank() }?.forEach { add(it.trim()) }
            },
        )
    }

    fun mapPeer(ipPort: String, qbitPeer: QbitPeerInfo): Peer {
        val address = if (!qbitPeer.ip.isNullOrEmpty()) {
            if ((qbitPeer.port != null) && (qbitPeer.port > 0)) "${qbitPeer.ip}:${qbitPeer.port}" else qbitPeer.ip
        } else ipPort

        return Peer(
            address = address,
            clientName = qbitPeer.client ?: "",
            flagStr = qbitPeer.flags ?: "",
            rateToClient = qbitPeer.dl_speed ?: 0,
            rateToPeer = qbitPeer.up_speed ?: 0,
            progress = qbitPeer.progress ?: 0.0,
        )
    }

    fun mapTracker(qbitTracker: QbitTrackerItem): Tracker {
        return Tracker(
            announce = qbitTracker.url ?: "",
            id = (qbitTracker.url ?: "").hashCode(),
        )
    }

    fun mapTrackerStat(qbitTracker: QbitTrackerItem): TrackerStats {
        return TrackerStats(
            announce = qbitTracker.url ?: "",
            seederCount = qbitTracker.num_seeds ?: 0,
            leecherCount = qbitTracker.num_leeches ?: 0,
            downloadCount = qbitTracker.num_downloaded ?: 0,
            hasScraped = ((qbitTracker.num_seeds ?: 0) > 0) || ((qbitTracker.num_leeches ?: 0) > 0),
            lastScrapeResult = qbitTracker.msg ?: "",
        )
    }

    fun mapFile(qbitFile: QbitFileInfo): TorrentFile {
        val length = qbitFile.size ?: 0L
        val bytesCompleted = ((qbitFile.progress ?: 0.0) * length).toLong()
        return TorrentFile(
            name = qbitFile.name ?: "",
            length = length,
            bytesCompleted = bytesCompleted,
        )
    }
}
