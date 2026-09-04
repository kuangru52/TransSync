package com.kuangru52.transsync

import com.kuangru52.transsync.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import retrofit2.awaitResponse

class TorrentCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TorrentCheckWorker"
        private const val CHANNEL_ID = "torrent_notifications"
        private const val PREFS_NOTIFIED = "notified_torrents"
        private const val KEY_FINISHED_IDS = "finished_ids"
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
        val rpcUrl = prefs.getString("rpcUrl", null) ?: return Result.success()
        val user = prefs.getString("user", null) ?: ""
        val pass = prefs.getString("pass", null) ?: ""

        val baseUrl = rpcUrl.substringBefore("/transmission/rpc") + "/"
        val service = TransmissionClient.getService(baseUrl, user, pass)

        try {
            val fields = listOf("id", "name", "percentDone", "status")
            val request = RpcRequest("torrent-get", mapOf("fields" to fields))
            
            val response = service.rpc(rpcUrl, null, request).awaitResponse()
            if (!response.isSuccessful) return Result.retry()

            val body = response.body()
            @Suppress("UNCHECKED_CAST")
            val torrents = body?.arguments?.get("torrents") as? List<Map<String, Any>> ?: emptyList()
            
            val notifiedPrefs = applicationContext.getSharedPreferences(PREFS_NOTIFIED, Context.MODE_PRIVATE)
            val alreadyNotifiedIds = notifiedPrefs.getStringSet(KEY_FINISHED_IDS, emptySet())
                ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            
            val currentFinishedIds = mutableSetOf<Int>()
            val newlyFinishedNames = mutableListOf<String>()

            for (t in torrents) {
                val id = (t["id"] as? Number)?.toInt() ?: continue
                val percentDone = (t["percentDone"] as? Number)?.toDouble() ?: 0.0
                val name = (t["name"] as? String) ?: "Unknown"

                if (percentDone >= 1.0) {
                    currentFinishedIds.add(id)
                    if (!alreadyNotifiedIds.contains(id)) {
                        newlyFinishedNames.add(name)
                    }
                }
            }

            // Update the notified set to current finished state
            notifiedPrefs.edit {
                putStringSet(KEY_FINISHED_IDS, currentFinishedIds.map { it.toString() }.toSet())
            }

            if (newlyFinishedNames.isNotEmpty()) {
                sendNotification(newlyFinishedNames)
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking torrents", e)
            return Result.retry()
        }
    }

    private fun sendNotification(names: List<String>) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Torrent Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val title = if (names.size == 1) "Download Finished" else "${names.size} Downloads Finished"
        val content = names.joinToString(", ")

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
