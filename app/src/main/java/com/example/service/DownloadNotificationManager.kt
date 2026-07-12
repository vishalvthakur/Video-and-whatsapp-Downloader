package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class DownloadNotificationManager(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "video_download_channel"
        const val NOTIFICATION_ID = 4001
        const val ACTION_CANCEL = "com.example.downloader.ACTION_CANCEL"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Video Downloads"
            val descriptionText = "Shows active media downloads and progress"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildDownloadNotification(
        videoTitle: String,
        progressText: String,
        progressPercent: Int,
        isFinished: Boolean = false
    ): Notification {
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val clickPendingIntent = PendingIntent.getActivity(
            context,
            0,
            clickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = Intent(context, VideoDownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            context,
            1,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (isFinished) "Download Finished" else videoTitle)
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(clickPendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(isFinished)

        if (!isFinished) {
            val isIndeterminate = progressPercent == 0
            builder.setProgress(100, progressPercent, isIndeterminate)
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPendingIntent
            )
        } else {
            builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
        }

        return builder.build()
    }

    fun buildErrorNotification(
        videoTitle: String,
        errorMsg: String,
        retryUrl: String?
    ): Notification {
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "com.example.downloader.ACTION_ERROR_RETRY"
            putExtra("ERROR_MESSAGE", errorMsg)
            putExtra("RETRY_URL", retryUrl)
        }
        val clickPendingIntent = PendingIntent.getActivity(
            context,
            2,
            clickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Failed: $videoTitle")
            .setContentText(errorMsg)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(clickPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)

        return builder.build()
    }

    fun updateNotification(notification: Notification) {
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
