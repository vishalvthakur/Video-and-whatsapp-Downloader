package com.example.extractor

import android.util.Log
import com.example.download.DownloadProgress
import com.example.download.DownloadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
        val isAudioOnly = formatSelector.equals("audio", ignoreCase = true)
        val quality = if (isAudioOnly) "max" else formatSelector // e.g. "1080", "720" etc.
        
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        
        var downloadedBytes = 0L
        var totalBytes = -1L
        var currentStreamUrl = ""
        val maxAttempts = 3
        var currentAttempt = 0
        
        var lastProgressTime = 0L
        val startTime = System.currentTimeMillis()
        
        while (currentAttempt < maxAttempts) {
            currentAttempt++
            try {
                // If stream URL is empty, fetch a fresh one
                if (currentStreamUrl.isEmpty()) {
                    Log.d("YtDlpVideoExtractor", "Fetching streaming URL from Cobalt...")
                    val (newStreamUrl, _) = YtDlpManager.getDownloadUrl(url, quality, isAudioOnly)
                    currentStreamUrl = newStreamUrl
                }
                
                val requestBuilder = Request.Builder().url(currentStreamUrl)
                
                // Add robust headers to mimic a real web browser and bypass CDN anti-bot blocks
                requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                requestBuilder.header("Accept", "*/*")
                requestBuilder.header("Accept-Language", "en-US,en;q=0.9")
                
                if (currentStreamUrl.contains("instagram.com") || currentStreamUrl.contains("cdninstagram.com")) {
                    requestBuilder.header("Referer", "https://www.instagram.com/")
                    requestBuilder.header("Origin", "https://www.instagram.com")
                } else if (currentStreamUrl.contains("googlevideo.com")) {
                    requestBuilder.header("Referer", "https://www.youtube.com/")
                } else {
                    // If it is a Cobalt tunnel URL, we MUST use Cobalt's standard web UI headers to avoid CORS/origin blocks
                    requestBuilder.header("Referer", "https://cobalt.tools/")
                    requestBuilder.header("Origin", "https://cobalt.tools")
                }

                // If some bytes were already successfully downloaded, request to resume
                if (downloadedBytes > 0) {
                    requestBuilder.header("Range", "bytes=$downloadedBytes-")
                    Log.d("YtDlpVideoExtractor", "Requesting range resume from byte: $downloadedBytes")
                }

                val request = requestBuilder.build()
                val call = client.newCall(request)
                activeCall = call

                call.execute().use { response ->
                    val isRangeSuccess = response.code == 206
                    
                    if (!response.isSuccessful) {
                        val code = response.code
                        // If we get 403 (Forbidden), 410 (Gone), or 416 (Requested Range Not Satisfiable),
                        // the URL has likely expired or IP mismatch occurred. We clear the stream URL to force a re-fetch.
                        if (code == 403 || code == 410 || code == 416) {
                            Log.w("YtDlpVideoExtractor", "Stream URL expired or invalid (HTTP $code). Clearing and retrying...")
                            currentStreamUrl = ""
                            if (code == 416) {
                                // Range mismatch: reset download index and start from scratch
                                downloadedBytes = 0L
                                if (outputFile.exists()) outputFile.delete()
                            }
                        }
                        throw Exception("Failed to connect to media host: HTTP ${response.code}")
                    }

                    val contentType = response.header("Content-Type") ?: ""
                    val body = response.body ?: throw Exception("Empty response body from media host")
                    val contentLength = body.contentLength()
                    
                    // If we requested range resume but server responded with 200 instead of 206,
                    // it means the server doesn't support range requests, so we must start over.
                    if (downloadedBytes > 0 && !isRangeSuccess) {
                        Log.w("YtDlpVideoExtractor", "Server does not support partial content. Restarting download from beginning...")
                        downloadedBytes = 0L
                        if (outputFile.exists()) outputFile.delete()
                    }

                    // Prevent silent failures where CDN returns HTML error pages or JSON challenge walls under HTTP 200
                    val isHtml = contentType.contains("text/html", ignoreCase = true)
                    val isJson = contentType.contains("application/json", ignoreCase = true)
                    val isPlainError = contentType.contains("text/plain", ignoreCase = true) && contentLength > 0L && contentLength < 50_000L

                    if (isHtml || isJson || isPlainError) {
                        val bodyString = body.string()
                        val errorSnippet = bodyString.take(1000)
                        
                        val cleanError = if (isJson) {
                            try {
                                val json = JSONObject(bodyString)
                                json.optString("error", json.optString("message", errorSnippet))
                            } catch (e: Exception) {
                                errorSnippet
                            }
                        } else {
                            errorSnippet
                        }

                        throw Exception("The media host returned an invalid page or error instead of a video stream (Content-Type: $contentType). Details: ${if (cleanError.length > 250) cleanError.take(250) + "..." else cleanError}")
                    }

                    // Update total expected bytes
                    if (downloadedBytes == 0L) {
                        totalBytes = contentLength
                    } else if (isRangeSuccess && totalBytes > 0) {
                        // Total bytes remains the same; do not overwrite with content length of HTTP 206 part
                    } else {
                        totalBytes = downloadedBytes + contentLength
                    }

                    val inputStream = body.byteStream()
                    // Open outputStream in append mode if we are resuming
                    val outputStream = FileOutputStream(outputFile, downloadedBytes > 0)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    try {
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
                        
                        // Download completed successfully!
                        return@withContext DownloadResult.Success(outputFile.absolutePath)
                    } catch (e: Exception) {
                        try { outputStream.flush() } catch (ignored: Exception) {}
                        try { outputStream.close() } catch (ignored: Exception) {}
                        try { inputStream.close() } catch (ignored: Exception) {}
                        throw e // Rethrow to catch block for retry/resume
                    }
                }
            } catch (e: Exception) {
                Log.e("YtDlpVideoExtractor", "Download attempt $currentAttempt failed: ${e.message}", e)
                val isCancelled = activeCall?.isCanceled() == true
                if (isCancelled) {
                    return@withContext DownloadResult.Error("Download cancelled")
                }
                
                if (currentAttempt >= maxAttempts) {
                    return@withContext DownloadResult.Error("Download failed after $maxAttempts attempts. Details: ${e.message}")
                }
                
                // Exponential backoff before next attempt
                delay(1000L * currentAttempt)
            } finally {
                activeCall = null
            }
        }
        
        DownloadResult.Error("Download failed after maximum retry attempts")
    }

    override fun cancel() {
        activeCall?.cancel()
        activeCall = null
    }
}
