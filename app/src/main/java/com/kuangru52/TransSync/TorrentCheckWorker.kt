package com.kuangru52.transsync

import android.app.PendingIntent
import android.content.Intent
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
// ... (middle code omitted for brevity but I will use full content in actual call if needed, but replace_file_content needs targetContent)

    companion object {
        private const val TAG = "TorrentCheckWorker"
        private const val CHANNEL_ID = "torrent_notifications"
        private const val PREFS_NOTIFIED = "notified_torrents"
        private const val KEY_FINISHED_IDS = "finished_ids"
        private const val KEY_HR_FINISHED_IDS = "hr_finished_ids"
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
        val rpcUrl = prefs.getString("rpcUrl", null) ?: return Result.success()
        val user = prefs.getString("user", null) ?: ""
        val pass = prefs.getString("pass", null) ?: ""

        val baseUrl = rpcUrl.substringBefore("/transmission/rpc") + "/"
        val service = TransmissionClient.getService(baseUrl, user, pass)

        try {
            val fields = listOf("id", "name", "percentDone", "status", "labels", "doneDate", "addedDate")
            val request = RpcRequest("torrent-get", mapOf("fields" to fields))
            
            val response = service.rpc(rpcUrl, null, request).awaitResponse()
            if (!response.isSuccessful) return Result.retry()

            val body = response.body()
            @Suppress("UNCHECKED_CAST")
            val torrents = body?.arguments?.get("torrents") as? List<Map<String, Any>> ?: emptyList()
            
            val notifiedPrefs = applicationContext.getSharedPreferences(PREFS_NOTIFIED, Context.MODE_PRIVATE)
            val alreadyNotifiedIds = notifiedPrefs.getStringSet(KEY_FINISHED_IDS, emptySet())
                ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            val alreadyNotifiedHrIds = notifiedPrefs.getStringSet(KEY_HR_FINISHED_IDS, emptySet())
                ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            
            val currentFinishedIds = mutableSetOf<Int>()
            val currentHrFinishedIds = mutableSetOf<Int>()
            val newlyFinishedNames = mutableListOf<String>()
            val newlyHrFinishedNames = mutableListOf<String>()

            for (t in torrents) {
                val id = (t["id"] as? Number)?.toInt() ?: continue
                val percentDone = (t["percentDone"] as? Number)?.toDouble() ?: 0.0
                val name = (t["name"] as? String) ?: "Unknown"
                @Suppress("UNCHECKED_CAST")
                val labels = t["labels"] as? List<String>
                val doneDate = (t["doneDate"] as? Number)?.toLong() ?: 0L
                val addedDate = (t["addedDate"] as? Number)?.toLong() ?: 0L

                if (percentDone >= 1.0) {
                    currentFinishedIds.add(id)
                    if (!alreadyNotifiedIds.contains(id)) {
                        newlyFinishedNames.add(name)
                    }

                    // 检查 H&R 是否完成
                    val hrLabel = labels?.find { it.startsWith("HR:") }
                    if (hrLabel != null) {
                        val hours = hrLabel.substringAfter("HR:").toDoubleOrNull() ?: 0.0
                        if (hours > 0) {
                            val completionTime = if (doneDate > 0) doneDate else addedDate
                            val doneDateMs = completionTime * 1000L
                            val requiredMs = (hours * 3600 * 1000L).toLong()
                            val elapsedMs = System.currentTimeMillis() - doneDateMs
                            if (elapsedMs >= requiredMs) {
                                currentHrFinishedIds.add(id)
                                if (!alreadyNotifiedHrIds.contains(id)) {
                                    newlyHrFinishedNames.add(name)
                                }
                            }
                        }
                    }
                }
            }

            // Update the notified sets
            notifiedPrefs.edit {
                putStringSet(KEY_FINISHED_IDS, currentFinishedIds.map { it.toString() }.toSet())
                putStringSet(KEY_HR_FINISHED_IDS, currentHrFinishedIds.map { it.toString() }.toSet())
            }

            if (newlyFinishedNames.isNotEmpty()) {
                sendNotification(
                    applicationContext.getString(R.string.notif_download_finished_title, newlyFinishedNames.size),
                    newlyFinishedNames.joinToString(", ")
                )
            }
            if (newlyHrFinishedNames.isNotEmpty()) {
                sendNotification(
                    applicationContext.getString(R.string.notif_hr_finished_title, newlyHrFinishedNames.size),
                    newlyHrFinishedNames.joinToString(", ")
                )
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking torrents", e)
            return Result.retry()
        }
    }

    private fun sendNotification(title: String, content: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Torrent Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
