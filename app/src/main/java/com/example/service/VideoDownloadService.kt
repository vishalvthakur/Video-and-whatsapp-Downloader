package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.download.AutoDownloadUseCase
import com.example.download.MediaDownloadManager
import com.example.download.DownloadQueueManager
import com.example.download.QueueStatus
import kotlinx.coroutines.*

class VideoDownloadService : Service() {
    private lateinit var notificationManager: DownloadNotificationManager
    private var activeTitle = "Video"

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        notificationManager = DownloadNotificationManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            "START_QUEUE_DOWNLOAD" -> {
                activeTitle = "Queue Downloader"
                val notification = notificationManager.buildDownloadNotification(
                    videoTitle = activeTitle,
                    progressText = "Starting queue...",
                    progressPercent = 0
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        DownloadNotificationManager.NOTIFICATION_ID,
                        notification,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        } else {
                            0
                        }
                    )
                } else {
                    startForeground(DownloadNotificationManager.NOTIFICATION_ID, notification)
                }

                serviceScope.launch {
                    DownloadQueueManager.setProcessing(true)
                    val database = com.example.data.database.DownloadDatabase.getDatabase(applicationContext)
                    val repository = com.example.data.repository.DownloadRepository(database.downloadDao())
                    val extractor = com.example.extractor.YtDlpVideoExtractor()

                    while (true) {
                        val queueList = DownloadQueueManager.queue.value
                        val currentItem = queueList.find { it.status == QueueStatus.PENDING } ?: break

                        val itemId = currentItem.id
                        val itemUrl = currentItem.url

                        try {
                            DownloadQueueManager.updateItemStatus(itemId, QueueStatus.EXTRACTING)
                            
                            activeTitle = "Queue: Extracting info..."
                            val statusNotification = notificationManager.buildDownloadNotification(
                                videoTitle = activeTitle,
                                progressText = "Extracting details for queued video...",
                                progressPercent = 0
                            )
                            notificationManager.updateNotification(statusNotification)

                            val videoInfo = extractor.extract(itemUrl)
                            DownloadQueueManager.updateItemTitle(itemId, videoInfo.title)
                            DownloadQueueManager.updateItemStatus(itemId, QueueStatus.DOWNLOADING)

                            activeTitle = videoInfo.title
                            val startNotification = notificationManager.buildDownloadNotification(
                                videoTitle = activeTitle,
                                progressText = "Downloading...",
                                progressPercent = 0
                            )
                            notificationManager.updateNotification(startNotification)

                            val availableFormats = com.example.extractor.FormatSelector.getAvailableQualities(videoInfo.formats)
                            val bestFormat = availableFormats.firstOrNull { (it.height ?: 0) <= 1080 }
                                ?: availableFormats.firstOrNull()
                                ?: videoInfo.formats.firstOrNull()

                            val formatId = bestFormat?.formatId ?: "1080"

                            MediaDownloadManager.downloadSuspend(
                                context = applicationContext,
                                url = videoInfo.webpageUrl,
                                formatId = formatId,
                                videoInfo = videoInfo,
                                repository = repository,
                                onServiceAction = { actionStr, value ->
                                    when (actionStr) {
                                        "UPDATE_PROGRESS" -> {
                                            val percent = value.replace("%", "").toIntOrNull() ?: 0
                                            DownloadQueueManager.updateItemProgress(itemId, percent)
                                            val progressNotification = notificationManager.buildDownloadNotification(
                                                videoTitle = activeTitle,
                                                progressText = "$percent% completed",
                                                progressPercent = percent
                                            )
                                            notificationManager.updateNotification(progressNotification)
                                        }
                                        "UPDATE_STATUS" -> {
                                            val progressNotification = notificationManager.buildDownloadNotification(
                                                videoTitle = activeTitle,
                                                progressText = value,
                                                progressPercent = 0
                                            )
                                            notificationManager.updateNotification(progressNotification)
                                        }
                                    }
                                }
                            )

                            DownloadQueueManager.updateItemStatus(itemId, QueueStatus.COMPLETED)
                            DownloadQueueManager.updateItemProgress(itemId, 100)

                        } catch (e: Exception) {
                            Log.e("VideoDownloadService", "Queue item download failed for url: $itemUrl", e)
                            DownloadQueueManager.updateItemError(itemId, e.message ?: "Extraction or download failed")
                            
                            val errorNotification = notificationManager.buildErrorNotification(
                                videoTitle = currentItem.title.takeIf { it != "Pending extraction..." } ?: "Video",
                                errorMsg = e.message ?: "Analysis failed",
                                retryUrl = itemUrl
                            )
                            val uniqueErrorId = DownloadNotificationManager.NOTIFICATION_ID + 100 + itemId.hashCode()
                            val systemNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                            systemNotificationManager.notify(uniqueErrorId, errorNotification)
                        }
                    }

                    DownloadQueueManager.setProcessing(false)

                    val finishedNotification = notificationManager.buildDownloadNotification(
                        videoTitle = "Queue Download Finished",
                        progressText = "Processed all queued URLs.",
                        progressPercent = 100,
                        isFinished = true
                    )
                    notificationManager.updateNotification(finishedNotification)

                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }

            "START_SHARE_FLOW" -> {
                val url = intent.getStringExtra("SHARED_URL")
                if (url.isNullOrEmpty()) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                activeTitle = "Analyzing..."
                val notification = notificationManager.buildDownloadNotification(
                    videoTitle = activeTitle,
                    progressText = "Analyzing shared link...",
                    progressPercent = 0
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        DownloadNotificationManager.NOTIFICATION_ID,
                        notification,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        } else {
                            0
                        }
                    )
                } else {
                    startForeground(DownloadNotificationManager.NOTIFICATION_ID, notification)
                }

                serviceScope.launch {
                    val serviceActionCallback: (String, String) -> Unit = { actionStr, value ->
                        try {
                            val callbackIntent = Intent(applicationContext, VideoDownloadService::class.java).apply {
                                this.action = actionStr
                                when (actionStr) {
                                    "UPDATE_TITLE" -> putExtra("TITLE", value)
                                    "START" -> putExtra("TITLE", value)
                                    "UPDATE_PROGRESS" -> {
                                        val percent = value.replace("%", "").toIntOrNull() ?: 0
                                        putExtra("PERCENT", percent)
                                    }
                                    "UPDATE_STATUS" -> putExtra("STATUS", value)
                                    "COMPLETED" -> putExtra("TITLE", value)
                                    "FAILED" -> {
                                        putExtra("ERROR", value)
                                        putExtra("URL", url)
                                    }
                                }
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(callbackIntent)
                            } else {
                                startService(callbackIntent)
                            }
                        } catch (e: Exception) {
                            Log.e("VideoDownloadService", "Error posting service action: $actionStr", e)
                        }
                    }

                    val autoDownloadUseCase = AutoDownloadUseCase(applicationContext)
                    autoDownloadUseCase.execute(url, serviceActionCallback)
                }
            }

            "UPDATE_TITLE" -> {
                activeTitle = intent.getStringExtra("TITLE") ?: "Video"
                val notification = notificationManager.buildDownloadNotification(
                    videoTitle = activeTitle,
                    progressText = "Preparing...",
                    progressPercent = 0
                )
                notificationManager.updateNotification(notification)
            }

            "START" -> {
                activeTitle = intent.getStringExtra("TITLE") ?: "Video"
                val notification = notificationManager.buildDownloadNotification(
                    videoTitle = activeTitle,
                    progressText = "Preparing...",
                    progressPercent = 0
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        DownloadNotificationManager.NOTIFICATION_ID,
                        notification,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        } else {
                            0
                        }
                    )
                } else {
                    startForeground(DownloadNotificationManager.NOTIFICATION_ID, notification)
                }
            }

            "UPDATE_PROGRESS" -> {
                val progressPercent = intent.getIntExtra("PERCENT", 0)
                val notification = notificationManager.buildDownloadNotification(
                    videoTitle = activeTitle,
                    progressText = "$progressPercent% completed",
                    progressPercent = progressPercent
                )
                notificationManager.updateNotification(notification)
            }

            "UPDATE_STATUS" -> {
                val statusText = intent.getStringExtra("STATUS") ?: "Processing..."
                val notification = notificationManager.buildDownloadNotification(
                    videoTitle = activeTitle,
                    progressText = statusText,
                    progressPercent = 0
                )
                notificationManager.updateNotification(notification)
            }

            "COMPLETED" -> {
                val completedTitle = intent.getStringExtra("TITLE") ?: activeTitle
                val notification = notificationManager.buildDownloadNotification(
                    videoTitle = completedTitle,
                    progressText = "Download completed successfully!",
                    progressPercent = 100,
                    isFinished = true
                )
                notificationManager.updateNotification(notification)
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }

            "FAILED" -> {
                val errorMsg = intent.getStringExtra("ERROR") ?: "Download failed"
                val failedUrl = intent.getStringExtra("URL")
                val notification = notificationManager.buildErrorNotification(
                    videoTitle = activeTitle,
                    errorMsg = errorMsg,
                    retryUrl = failedUrl
                )
                notificationManager.updateNotification(notification)
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }

            DownloadNotificationManager.ACTION_CANCEL -> {
                MediaDownloadManager.cancelActiveJob()
                notificationManager.cancelNotification()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
