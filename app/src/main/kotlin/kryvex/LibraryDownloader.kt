package kryvex.loader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

data class LibraryDownloadOutcome(val success: Boolean, val reason: String = "")

class LibraryDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val NOTIFICATION_ID = 1696900
        private const val CHANNEL_ID = "download_channel"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 650L

        fun cancelNotification(context: Context) {
            runCatching {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                Handler(Looper.getMainLooper()).post { manager.cancel(NOTIFICATION_ID) }
            }
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                        val channel = NotificationChannel(
                            CHANNEL_ID,
                            "Download Notifications",
                            NotificationManager.IMPORTANCE_LOW
                        )
                        channel.description = "Shows download progress"
                        manager.createNotificationChannel(channel)
                    }
                }
            }
        }
    }

    init {
        createNotificationChannel(context)
    }

    fun download(url: String, destinationName: String, onProgress: (Int) -> Unit = {}): Boolean {
        return downloadWithStatus(url, destinationName, onProgress).success
    }

    fun downloadWithStatus(
        url: String,
        destinationName: String,
        onProgress: (Int) -> Unit = {},
        sessionToken: String = ""
    ): LibraryDownloadOutcome {
        if (!sessionToken.matches(Regex("[a-fA-F0-9]{64}"))) {
            return LibraryDownloadOutcome(false, "License session unavailable. Log in again")
        }
        val destination = File(context.cacheDir, destinationName)
        val temporary = File(context.cacheDir, "$destinationName.part")
        val request = runCatching {
            Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream, */*")
                .header("Cache-Control", "no-cache")
                .header("X-Kryvex-Session", sessionToken)
                .build()
        }.getOrElse { return LibraryDownloadOutcome(false, "Invalid library download address") }

        safeNotify(buildProgressNotification(0, 0, 0))

        try {
            for (attempt in 1..MAX_RETRIES) {
                temporary.delete()
                try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val reason = when (response.code) {
                                401 -> "Library session expired or unauthorized. Log in again"
                                403 -> "Library not assigned to this license"
                                404 -> "Library file not found (HTTP 404)"
                                else -> "Library server error (HTTP ${response.code})"
                            }
                            if (response.code >= 500 && attempt < MAX_RETRIES) {
                                TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS)
                                return@use
                            }
                            return LibraryDownloadOutcome(false, reason)
                        }

                        val body = response.body
                            ?: return LibraryDownloadOutcome(false, "Library server returned an empty response")
                        val totalBytes = body.contentLength()
                        var receivedBytes = 0L

                        body.byteStream().use { input ->
                            temporary.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val size = input.read(buffer)
                                    if (size < 0) break
                                    output.write(buffer, 0, size)
                                    receivedBytes += size
                                    val progress = if (totalBytes > 0) {
                                        (receivedBytes * 100L / totalBytes).toInt().coerceIn(0, 100)
                                    } else 0
                                    handler.post {
                                        safeNotify(buildProgressNotification(progress, receivedBytes, totalBytes))
                                        onProgress(progress)
                                    }
                                }
                            }
                        }

                        if (totalBytes > 0 && receivedBytes != totalBytes) {
                            if (attempt < MAX_RETRIES) {
                                TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS)
                                return@use
                            }
                            return LibraryDownloadOutcome(false, "Download interrupted. Received $receivedBytes of $totalBytes bytes")
                        }

                        if (receivedBytes < 64L) {
                            return LibraryDownloadOutcome(false, "Library response is incomplete ($receivedBytes bytes)")
                        }

                        val header = ByteArray(4)
                        val isElf = temporary.inputStream().use { input ->
                            input.read(header) == 4 && header.contentEquals(byteArrayOf(0x7F, 0x45, 0x4C, 0x46))
                        }

                        if (!isElf) {
                            val sample = temporary.inputStream().use { input ->
                                ByteArray(16).also { input.read(it) }
                            }
                            val webpage = sample.firstOrNull()?.toInt() in listOf(60, 33)
                            return LibraryDownloadOutcome(false, if (webpage) {
                                "Library link returned a webpage, not a .so file"
                            } else {
                                "Downloaded file is not a valid Android .so library"
                            })
                        }

                        destination.delete()
                        if (!temporary.renameTo(destination)) {
                            temporary.copyTo(destination, overwrite = true)
                            temporary.delete()
                        }
                        if (!destination.isFile || destination.length() != receivedBytes) {
                            return LibraryDownloadOutcome(false, "Unable to save downloaded library")
                        }

                        handler.post {
                            safeNotify(buildProgressNotification(100, receivedBytes, receivedBytes))
                            onProgress(100)
                            handler.postDelayed({ safeCancel() }, 1500)
                        }
                        return LibraryDownloadOutcome(true)
                    }
                } catch (error: IOException) {
                    if (attempt == MAX_RETRIES) {
                        return LibraryDownloadOutcome(false, "Network transfer failed: ${error.message ?: "connection closed"}")
                    }
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS)
                } catch (error: Exception) {
                    return LibraryDownloadOutcome(false, "Library download failed: ${error.message ?: error.javaClass.simpleName}")
                }
            }
            return LibraryDownloadOutcome(false, "Library server unavailable")
        } finally {
            temporary.delete()
            safeCancel()
        }
    }

    private fun safeNotify(notification: Notification) {
        runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
    }

    private fun safeCancel() {
        handler.post { runCatching { notificationManager.cancel(NOTIFICATION_ID) } }
    }

    private fun buildProgressNotification(progress: Int, downloadedBytes: Long, totalBytes: Long): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading library")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)

        fun formatSize(bytes: Long): String {
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1 -> String.format(Locale.US, "%.2f MB", mb)
                kb >= 1 -> String.format(Locale.US, "%.2f KB", kb)
                else -> "$bytes B"
            }
        }

        val downloaded = formatSize(downloadedBytes)
        val total = formatSize(totalBytes.coerceAtLeast(0))
        if (totalBytes > 0) {
            builder.setContentText("$progress% ($downloaded / $total)")
                .setProgress(100, progress, false)
        } else {
            builder.setContentText("Downloaded $downloaded")
                .setProgress(0, 0, true)
        }
        return builder.build()
    }
}
