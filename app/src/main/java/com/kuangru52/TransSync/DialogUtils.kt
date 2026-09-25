package com.kuangru52.transsync

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * 100% 清洁提炼的网络 RPC 服务传输助手 (双向兼容 Transmission 与 qBittorrent)
 */
object DialogUtils {

    fun getEffectiveCredentials(context: Context, rpcUrl: String, user: String, pass: String): Triple<String, String, String> {
        val activeServer = ServerManager.getActiveServer(context)
        val url = rpcUrl.ifEmpty { activeServer?.rpcUrl ?: "" }
        val u = user.ifEmpty { activeServer?.user ?: "" }
        val p = pass.ifEmpty { activeServer?.pass ?: "" }
        return Triple(url, u, p)
    }

    fun performRename(
        context: Context,
        rpcUrl: String,
        user: String,
        pass: String,
        torrentId: Int,
        torrentHash: String = "",
        currentName: String,
        newName: String,
        onSuccess: () -> Unit,
    ) {
        val (effUrl, effUser, effPass) = getEffectiveCredentials(context, rpcUrl, user, pass)
        val activeServer = ServerManager.getActiveServer(context)

        if (activeServer?.clientType == ServerConfig.CLIENT_QBITTORRENT) {
            val qbitService = QBittorrentClient.getService(effUrl)
            qbitService.renameTorrent(torrentHash, newName).enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {
                    if (response.isSuccessful) onSuccess() else Toast.makeText(context, context.getString(R.string.msg_update_failed, response.code()), Toast.LENGTH_SHORT).show()
                }
                override fun onFailure(call: Call<String>, t: Throwable) {
                    Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                }
            })
            return
        }

        val service = TransmissionClient.getService(effUrl, effUser, effPass)
        service.rpc(effUrl, null, RpcRequest("torrent-rename-path", mapOf("ids" to listOf(torrentId), "path" to currentName, "name" to newName)))
            .enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        Toast.makeText(context, context.getString(R.string.msg_update_failed, response.code()), Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                    Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                }
            })
    }

    fun performDelete(
        context: Context,
        rpcUrl: String,
        user: String,
        pass: String,
        torrentIds: List<Int>,
        torrentHashes: List<String> = emptyList(),
        deleteData: Boolean,
        onSuccess: () -> Unit
    ) {
        val (effUrl, effUser, effPass) = getEffectiveCredentials(context, rpcUrl, user, pass)
        val activeServer = ServerManager.getActiveServer(context)

        if (activeServer?.clientType == ServerConfig.CLIENT_QBITTORRENT) {
            val qbitService = QBittorrentClient.getService(effUrl)
            val hashesStr = torrentHashes.filter { it.isNotEmpty() }.joinToString("|")
            qbitService.deleteTorrents(hashesStr, deleteData).enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {
                    if (response.isSuccessful) onSuccess() else Toast.makeText(context, context.getString(R.string.msg_update_failed, response.code()), Toast.LENGTH_SHORT).show()
                }
                override fun onFailure(call: Call<String>, t: Throwable) {
                    Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                }
            })
            return
        }

        val service = TransmissionClient.getService(effUrl, effUser, effPass)
        val args = mapOf("ids" to torrentIds, "delete-local-data" to deleteData)
        service.rpc(effUrl, null, RpcRequest("torrent-remove", args)).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    Toast.makeText(context, context.getString(R.string.msg_update_failed, response.code()), Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun performSetLocation(
        context: Context,
        rpcUrl: String,
        user: String,
        pass: String,
        torrentIds: List<Int>,
        torrentHashes: List<String> = emptyList(),
        newLocation: String,
        moveData: Boolean,
        onSuccess: () -> Unit
    ) {
        val (effUrl, effUser, effPass) = getEffectiveCredentials(context, rpcUrl, user, pass)
        val activeServer = ServerManager.getActiveServer(context)

        if (activeServer?.clientType == ServerConfig.CLIENT_QBITTORRENT) {
            val qbitService = QBittorrentClient.getService(effUrl)
            val hashesStr = torrentHashes.filter { it.isNotEmpty() }.joinToString("|")
            qbitService.setLocation(hashesStr, newLocation).enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, R.string.msg_location_updated, Toast.LENGTH_SHORT).show()
                        DownloadDirManager.saveDirToHistory(context, newLocation)
                        onSuccess()
                    } else {
                        Toast.makeText(context, context.getString(R.string.msg_update_failed, response.code()), Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<String>, t: Throwable) {
                    Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                }
            })
            return
        }
        val service = TransmissionClient.getService(effUrl, effUser, effPass)
        val args = mapOf(
            "ids" to torrentIds,
            "location" to newLocation,
            "move" to moveData,
        )
        service.rpc(effUrl, null, RpcRequest("torrent-set-location", args)).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (response.isSuccessful) {
                    Toast.makeText(context, R.string.msg_location_updated, Toast.LENGTH_SHORT).show()
                    DownloadDirManager.saveDirToHistory(context, newLocation)
                    onSuccess()
                } else {
                    Toast.makeText(context, context.getString(R.string.msg_update_failed, response.code()), Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun performSetHr(
        context: Context,
        rpcUrl: String,
        user: String,
        pass: String,
        torrentIds: List<Int>,
        torrentHashes: List<String> = emptyList(),
        days: Double,
        onSuccess: () -> Unit
    ) {
        val (effUrl, effUser, effPass) = getEffectiveCredentials(context, rpcUrl, user, pass)
        val activeServer = ServerManager.getActiveServer(context)

        if (activeServer?.clientType == ServerConfig.CLIENT_QBITTORRENT) {
            val qbitService = QBittorrentClient.getService(effUrl)
            val hashesStr = torrentHashes.filter { it.isNotEmpty() }.joinToString("|")
            val hours = days * 24
            val hrTag = if (hours > 0) "HR:$hours" else ""

            val doSetTag = {
                if (hrTag.isNotEmpty()) {
                    qbitService.addTags(hashesStr, hrTag).enqueue(object : Callback<String> {
                        override fun onResponse(call: Call<String>, response: Response<String>) {
                            if (response.isSuccessful || response.code() == 200) {
                                Toast.makeText(context, R.string.msg_hr_set_success, Toast.LENGTH_SHORT).show()
                                onSuccess()
                            } else {
                                Toast.makeText(context, context.getString(R.string.msg_update_failed, response.code()), Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onFailure(call: Call<String>, t: Throwable) {
                            Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                        }
                    })
                } else {
                    onSuccess()
                }
            }

            if (effUser.isNotEmpty() || effPass.isNotEmpty()) {
                qbitService.login(effUser, effPass).enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) { doSetTag() }
                    override fun onFailure(call: Call<String>, t: Throwable) { doSetTag() }
                })
            } else {
                doSetTag()
            }
            return
        }

        val hours = days * 24
        val service = TransmissionClient.getService(effUrl, effUser, effPass)
        val hrLabel = if (hours > 0) "HR:$hours" else null
        val args = mutableMapOf<String, Any>("ids" to torrentIds)
        args["labels"] = if (hrLabel != null) listOf(hrLabel) else emptyList()

        service.rpc(effUrl, null, RpcRequest("torrent-set", args)).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
            override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                if (response.isSuccessful && response.body()?.result == "success") {
                    Toast.makeText(context, R.string.msg_hr_set_success, Toast.LENGTH_SHORT).show()
                    onSuccess()
                } else {
                    val msg = response.body()?.result ?: response.code().toString()
                    Toast.makeText(context, context.getString(R.string.msg_update_failed_with_msg, msg), Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun performAddTorrent(
        context: Context,
        rpcUrl: String,
        user: String,
        pass: String,
        url: String,
        downloadDir: String,
        hrDays: Double,
        fileUri: Uri?,
        onSuccess: () -> Unit
    ) {
        val (effUrl, effUser, effPass) = getEffectiveCredentials(context, rpcUrl, user, pass)
        val hours = hrDays * 24
        val hrLabel = if (hours > 0) "HR:$hours" else null

        if (downloadDir.isNotEmpty()) {
            DownloadDirManager.saveDirToHistory(context, downloadDir)
        }

        val activeServer = ServerManager.getActiveServer(context)
        if (activeServer?.clientType == ServerConfig.CLIENT_QBITTORRENT) {
            val qbitService = QBittorrentClient.getService(effUrl)

            val doAdd = {
                val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                if (url.isNotEmpty()) builder.addFormDataPart("urls", url)
                if (downloadDir.isNotEmpty()) builder.addFormDataPart("savepath", downloadDir)
                if (hrLabel != null) builder.addFormDataPart("tags", hrLabel)
                builder.addFormDataPart("autoTMM", "false")

                if (fileUri != null) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(fileUri)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()
                        if (bytes != null) {
                            val reqBody = bytes.toRequestBody("application/x-bittorrent".toMediaTypeOrNull())
                            builder.addFormDataPart("torrents", "torrent_file.torrent", reqBody)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                qbitService.addTorrent(builder.build()).enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) {
                        if (response.isSuccessful || response.code() == 200) {
                            Toast.makeText(context, R.string.msg_torrent_added_success, Toast.LENGTH_SHORT).show()
                            onSuccess()
                        } else {
                            Toast.makeText(context, context.getString(R.string.msg_update_failed, response.code()), Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<String>, t: Throwable) {
                        Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                    }
                })
            }

            if (effUser.isNotEmpty() || effPass.isNotEmpty()) {
                qbitService.login(effUser, effPass).enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) { doAdd() }
                    override fun onFailure(call: Call<String>, t: Throwable) { doAdd() }
                })
            } else {
                doAdd()
            }
            return
        }

        val service = TransmissionClient.getService(effUrl, effUser, effPass)

        if (fileUri != null) {
            val inputStream = context.contentResolver.openInputStream(fileUri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val args = mutableMapOf<String, Any>("metainfo" to base64)
                if (downloadDir.isNotEmpty()) args["download-dir"] = downloadDir
                hrLabel?.let { args["labels"] = listOf(it) }

                service.rpc(effUrl, null, RpcRequest("torrent-add", args)).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                    override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                        if (response.isSuccessful) {
                            Toast.makeText(context, R.string.msg_torrent_added_success, Toast.LENGTH_SHORT).show()
                            onSuccess()
                        }
                    }
                    override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                        Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                    }
                })
            }
        } else if (url.isNotEmpty()) {
            val args = mutableMapOf<String, Any>("filename" to url)
            if (downloadDir.isNotEmpty()) args["download-dir"] = downloadDir
            hrLabel?.let { args["labels"] = listOf(it) }

            service.rpc(effUrl, null, RpcRequest("torrent-add", args)).enqueue(object : Callback<RpcResponse<Map<String, Any>>> {
                override fun onResponse(call: Call<RpcResponse<Map<String, Any>>>, response: Response<RpcResponse<Map<String, Any>>>) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, R.string.msg_torrent_added_success, Toast.LENGTH_SHORT).show()
                        onSuccess()
                    }
                }
                override fun onFailure(call: Call<RpcResponse<Map<String, Any>>>, t: Throwable) {
                    Toast.makeText(context, R.string.msg_network_error, Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}
