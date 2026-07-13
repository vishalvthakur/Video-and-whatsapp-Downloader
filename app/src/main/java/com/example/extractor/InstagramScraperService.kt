package com.example.extractor

import android.util.Log
import com.example.download.DownloadProgress
import com.example.download.DownloadResult
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object InstagramScraperService {
    private const val TAG = "InstagramScraper"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private class SimpleCookieJar : CookieJar {
        private val cookieStore = HashMap<String, MutableList<Cookie>>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            val list = cookieStore[host] ?: ArrayList()
            for (cookie in cookies) {
                list.removeAll { it.name == cookie.name }
                list.add(cookie)
            }
            cookieStore[host] = list
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: ArrayList()
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cookieJar(SimpleCookieJar())
        .build()

    private val streamUrlCache = HashMap<String, String>()

    @Volatile
    private var activeCall: okhttp3.Call? = null

    fun cancel() {
        synchronized(this) {
            activeCall?.cancel()
            activeCall = null
        }
    }

    fun extractShortcode(url: String): String? {
        val patterns = listOf(
            "instagram\\.com/(?:p|reel|tv)/([a-zA-Z0-9_-]+)",
            "instagr\\.am/(?:p|reel|tv)/([a-zA-Z0-9_-]+)"
        )
        for (pattern in patterns) {
            val regex = pattern.toRegex(RegexOption.IGNORE_CASE)
            val match = regex.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun unescapeJsonString(str: String): String {
        var result = str
        result = result.replace("\\u0026", "&")
        result = result.replace("\\u003d", "=")
        result = result.replace("\\/", "/")
        val unicodeRegex = "\\\\u([0-9a-fA-F]{4})".toRegex()
        result = unicodeRegex.replace(result) { matchResult ->
            val hexVal = matchResult.groupValues[1]
            try {
                hexVal.toInt(16).toChar().toString()
            } catch (e: Exception) {
                matchResult.value
            }
        }
        return result
    }

    private fun buildBrowserRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Sec-Ch-Ua", "\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"")
            .header("Sec-Ch-Ua-Mobile", "?0")
            .header("Sec-Ch-Ua-Platform", "\"Windows\"")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Upgrade-Insecure-Requests", "1")
            .build()
    }

    suspend fun extract(url: String): VideoInfo {
        val shortcode = extractShortcode(url) ?: throw Exception("Invalid Instagram URL. No shortcode found.")

        Log.d(TAG, "Extracting Instagram media for shortcode: $shortcode")

        // Strategy A: Try scraping Embed URL first (Public, highly reliable, does not require login)
        val embedInfo = extractFromEmbed(shortcode)
        if (embedInfo != null) {
            Log.d(TAG, "Strategy A (Embed scraping) succeeded for $shortcode")
            return embedInfo
        }

        // Strategy B: Try public JSON/GraphQL API endpoint (?__a=1&__d=dis)
        val jsonInfo = extractFromJson(shortcode)
        if (jsonInfo != null) {
            Log.d(TAG, "Strategy B (Public JSON API) succeeded for $shortcode")
            return jsonInfo
        }

        // Strategy C: Fallback to Cobalt Instance pool if direct scraping fails
        try {
            return extractFromCobalt(url, shortcode)
        } catch (e: Exception) {
            Log.e(TAG, "Strategy C (Cobalt fallback) failed for $shortcode", e)
            throw Exception("Failed to extract Instagram stream. " +
                    "Both direct scraping and Cobalt fallback server methods failed. " +
                    "The post may be private, deleted, or age-restricted.")
        }
    }

    private fun extractFromEmbed(shortcode: String): VideoInfo? {
        val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
        val request = buildBrowserRequest(embedUrl)

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: ""

                // Parse video URL
                var videoUrl: String? = null
                val videoRegexes = listOf(
                    "\"video_url\"\\s*:\\s*\"([^\"]+)\"".toRegex(),
                    "video_url\\s*=\\s*'([^']+)'".toRegex()
                )
                for (regex in videoRegexes) {
                    val match = regex.find(html)
                    if (match != null) {
                        videoUrl = unescapeJsonString(match.groupValues[1])
                        break
                    }
                }

                // Parse thumbnail URL
                var displayUrl: String? = null
                val displayRegexes = listOf(
                    "\"display_url\"\\s*:\\s*\"([^\"]+)\"".toRegex(),
                    "\"thumbnail_src\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                )
                for (regex in displayRegexes) {
                    val match = regex.find(html)
                    if (match != null) {
                        displayUrl = unescapeJsonString(match.groupValues[1])
                        break
                    }
                }

                // Parse caption
                var caption = "Instagram Video"
                val captionRegex = "<div class=\"Caption\">.*?<span[^>]*>(.*?)</span>".toRegex(RegexOption.DOT_MATCHES_ALL)
                val captionMatch = captionRegex.find(html)
                if (captionMatch != null) {
                    val cleanCaption = captionMatch.groupValues[1]
                        .replace("<[^>]*>".toRegex(), "") // Strip HTML tags
                        .trim()
                    if (cleanCaption.isNotEmpty()) {
                        caption = cleanCaption
                    }
                }

                // Parse author username
                var username = "instagram_user"
                val usernameRegex = "\"owner_username\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val usernameMatch = usernameRegex.find(html)
                if (usernameMatch != null) {
                    username = usernameMatch.groupValues[1]
                }

                if (videoUrl != null) {
                    return buildVideoInfo(
                        id = shortcode,
                        title = if (caption.length > 50) caption.take(50) + "..." else caption,
                        description = caption,
                        thumbnail = displayUrl,
                        videoUrl = videoUrl,
                        uploader = username,
                        webpageUrl = "https://www.instagram.com/p/$shortcode/"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed Strategy A (Embed scraping) for shortcode $shortcode", e)
        }
        return null
    }

    private fun extractFromJson(shortcode: String): VideoInfo? {
        val apiUrl = "https://www.instagram.com/p/$shortcode/?__a=1&__d=dis"
        val request = buildBrowserRequest(apiUrl)

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val jsonStr = response.body?.string() ?: ""
                if (jsonStr.startsWith("<!DOCTYPE") || jsonStr.startsWith("<html")) return null

                val json = JSONObject(jsonStr)
                val items = json.optJSONArray("items")
                if (items != null && items.length() > 0) {
                    val item = items.getJSONObject(0)

                    val videoVersions = item.optJSONArray("video_versions")
                    val videoUrl = if (videoVersions != null && videoVersions.length() > 0) {
                        videoVersions.getJSONObject(0).optString("url")
                    } else null

                    if (videoUrl != null) {
                        val imageVersions = item.optJSONObject("image_versions2")
                        val candidates = imageVersions?.optJSONArray("candidates")
                        val displayUrl = if (candidates != null && candidates.length() > 0) {
                            candidates.getJSONObject(0).optString("url")
                        } else null

                        val userObj = item.optJSONObject("user")
                        val username = userObj?.optString("username") ?: "instagram_user"

                        val captionObj = item.optJSONObject("caption")
                        val captionText = captionObj?.optString("text") ?: "Instagram Video"

                        return buildVideoInfo(
                            id = shortcode,
                            title = if (captionText.length > 50) captionText.take(50) + "..." else captionText,
                            description = captionText,
                            thumbnail = displayUrl,
                            videoUrl = videoUrl,
                            uploader = username,
                            webpageUrl = "https://www.instagram.com/p/$shortcode/"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed Strategy B (JSON api) for shortcode $shortcode", e)
        }
        return null
    }

    private suspend fun extractFromCobalt(url: String, shortcode: String): VideoInfo {
        Log.d(TAG, "Direct scraping failed. Falling back to Cobalt for $url")
        val (streamUrl, _) = YtDlpManager.getDownloadUrl(url, "1080", false)

        val baseInfo = try {
            YtDlpManager.fetchMetadata(url)
        } catch (e: Exception) {
            null
        }

        val title = baseInfo?.title ?: "Instagram Video"
        val thumbnail = baseInfo?.thumbnail
        val description = baseInfo?.description ?: "Instagram Reel / Post"
        val uploader = baseInfo?.uploader ?: "instagram_user"

        return buildVideoInfo(
            id = shortcode,
            title = title,
            description = description,
            thumbnail = thumbnail,
            videoUrl = streamUrl,
            uploader = uploader,
            webpageUrl = url
        )
    }

    private fun buildVideoInfo(
        id: String,
        title: String,
        description: String?,
        thumbnail: String?,
        videoUrl: String,
        uploader: String?,
        webpageUrl: String
    ): VideoInfo {
        val formats = listOf(
            VideoFormat(
                formatId = "1080",
                formatName = "HD 1080p",
                extension = "mp4",
                width = 1080,
                height = 1920,
                resolution = "1080x1920",
                fps = 30.0,
                videoCodec = "h264",
                audioCodec = "aac",
                fileSize = null,
                approximateFileSize = 15_000_000L,
                bitrate = 2500.0,
                hasVideo = true,
                hasAudio = true
            ),
            VideoFormat(
                formatId = "720",
                formatName = "720p",
                extension = "mp4",
                width = 720,
                height = 1280,
                resolution = "720x1280",
                fps = 30.0,
                videoCodec = "h264",
                audioCodec = "aac",
                fileSize = null,
                approximateFileSize = 8_000_000L,
                bitrate = 1200.0,
                hasVideo = true,
                hasAudio = true
            ),
            VideoFormat(
                formatId = "audio",
                formatName = "Audio Only",
                extension = "mp3",
                width = null,
                height = null,
                resolution = null,
                fps = null,
                videoCodec = null,
                audioCodec = "mp3",
                fileSize = null,
                approximateFileSize = 2_000_000L,
                bitrate = 128.0,
                hasVideo = false,
                hasAudio = true
            )
        )

        synchronized(streamUrlCache) {
            streamUrlCache[id] = videoUrl
        }

        return VideoInfo(
            id = id,
            title = title,
            description = description,
            thumbnail = thumbnail,
            duration = null,
            source = "Instagram",
            uploader = uploader,
            webpageUrl = webpageUrl,
            formats = formats
        )
    }

    suspend fun download(
        originalUrl: String,
        formatSelector: String,
        outputPath: String,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadResult {
        val shortcode = extractShortcode(originalUrl) ?: return DownloadResult.Error("Invalid Instagram URL")

        var streamUrl = synchronized(streamUrlCache) { streamUrlCache[shortcode] }

        if (streamUrl.isNullOrEmpty()) {
            try {
                extract(originalUrl)
                streamUrl = synchronized(streamUrlCache) { streamUrlCache[shortcode] }
            } catch (e: Exception) {
                return DownloadResult.Error("Failed to extract video stream: ${e.message}")
            }
        }

        if (streamUrl.isNullOrEmpty()) {
            return DownloadResult.Error("Could not resolve Instagram video stream URL")
        }

        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()

        val startTime = System.currentTimeMillis()
        var lastProgressTime = 0L

        // Prepare the request using identical client settings to prevent IP/User-Agent CDN blocks
        val requestBuilder = Request.Builder()
            .url(streamUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "https://www.instagram.com/")
            .header("Sec-Fetch-Dest", "video")
            .header("Sec-Fetch-Mode", "no-cors")
            .header("Sec-Fetch-Site", "cross-site")

        val request = requestBuilder.build()
        val call = client.newCall(request)
        synchronized(this) {
            activeCall = call
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 403) {
                        Log.w(TAG, "Direct CDN URL returned 403. Clearing cache, re-extracting fresh link...")
                        synchronized(streamUrlCache) {
                            streamUrlCache.remove(shortcode)
                        }
                        // Re-try download which will trigger fresh extraction of expiring CDN links
                        return download(originalUrl, formatSelector, outputPath, onProgress)
                    }
                    throw Exception("Server returned HTTP ${response.code}")
                }

                val body = response.body ?: throw Exception("Empty response body from media host")
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(outputFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloadedBytes = 0L

                try {
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val currentTime = System.currentTimeMillis()
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
                    return DownloadResult.Success(outputFile.absolutePath)
                } finally {
                    try { outputStream.close() } catch (ignored: Exception) {}
                    try { inputStream.close() } catch (ignored: Exception) {}
                }
            }
        } catch (e: Exception) {
            val isCanceled = call.isCanceled()
            if (isCanceled) {
                return DownloadResult.Error("Download cancelled")
            }
            return DownloadResult.Error("Download failed: ${e.message}")
        } finally {
            synchronized(this) {
                if (activeCall == call) {
                    activeCall = null
                }
            }
        }
    }
}
