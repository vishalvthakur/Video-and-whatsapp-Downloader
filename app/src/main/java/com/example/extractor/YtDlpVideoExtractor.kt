package com.example.extractor

import com.example.download.DownloadProgress
import com.example.download.DownloadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class YtDlpVideoExtractor : VideoExtractor {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var activeCall: okhttp3.Call? = null

    override suspend fun extract(url: String): VideoInfo = withContext(Dispatchers.IO) {
        YtDlpManager.fetchMetadata(url)
    }

    override suspend fun download(
        url: String,
        formatSelector: String,
        outputPath: String,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val isAudioOnly = formatSelector.equals("audio", ignoreCase = true)
            val quality = if (isAudioOnly) "max" else formatSelector // e.g. "1080", "720" etc.
            
            // Get streaming URL from Cobalt
            val (streamUrl, _) = YtDlpManager.getDownloadUrl(url, quality, isAudioOnly)

            val request = Request.Builder().url(streamUrl).build()
            val call = client.newCall(request)
            activeCall = call

            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DownloadResult.Error("Failed to connect to media host: HTTP ${response.code}")
                }

                val body = response.body ?: return@withContext DownloadResult.Error("Empty response body from media host")
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                
                val outputFile = File(outputPath)
                outputFile.parentFile?.mkdirs()
                val outputStream = FileOutputStream(outputFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloadedBytes: Long = 0
                val startTime = System.currentTimeMillis()
                var lastProgressTime = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val currentTime = System.currentTimeMillis()
                    // Report progress every 200ms or when complete
                    if (currentTime - lastProgressTime > 200L || downloadedBytes == totalBytes) {
                        lastProgressTime = currentTime
                        
                        val percent = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()) * 100f else 0f
                        val durationSec = (currentTime - startTime) / 1000.0
                        val speed = if (durationSec > 0) (downloadedBytes / durationSec).toLong() else 0L
                        val eta = if (speed > 0 && totalBytes > 0) (totalBytes - downloadedBytes) / speed else null

                        onProgress(
                            DownloadProgress(
                                percent = percent,
                                downloadedBytes = downloadedBytes,
                                totalBytes = if (totalBytes > 0) totalBytes else null,
                                speedBytesPerSecond = speed,
                                etaSeconds = eta
                            )
                        )
                    }
                }
                
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                
                DownloadResult.Success(outputFile.absolutePath)
            }
        } catch (e: Exception) {
            val isCancelled = activeCall?.isCanceled() == true
            if (isCancelled) {
                DownloadResult.Error("Download cancelled")
            } else {
                DownloadResult.Error(e.message ?: "Unknown download error")
            }
        } finally {
            activeCall = null
        }
    }

    override fun cancel() {
        activeCall?.cancel()
        activeCall = null
    }
}
