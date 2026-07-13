package com.example.extractor

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit

object YtDlpManager {
    private const val TAG = "YtDlpManager"
    
    @Volatile
    var customCobaltUrl: String? = null

    @Volatile
    var customYoutubeCookie: String? = null
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    suspend fun fetchMetadata(url: String): VideoInfo {
        var title = "Video Downloader Content"
        var thumbnail: String? = null
        var uploader: String? = null
        var source: String? = null
        var description: String? = null
        var duration: Long? = null

        val isYouTube = url.contains("youtube.com") || url.contains("youtu.be")

        // 1. Try official YouTube oEmbed first for YouTube links
        if (isYouTube) {
            try {
                val ytOembedUrl = "https://www.youtube.com/oembed?url=${java.net.URLEncoder.encode(url, "UTF-8")}&format=json"
                val request = Request.Builder()
                    .url(ytOembedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrEmpty()) {
                            val json = JSONObject(bodyString)
                            if (json.has("title")) title = json.getString("title")
                            if (json.has("thumbnail_url")) thumbnail = json.getString("thumbnail_url")
                            if (json.has("author_name")) uploader = json.getString("author_name")
                            source = "YouTube"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Official YouTube oEmbed fetch failed", e)
            }
        }

        // 2. Try generic oEmbed (noembed) if still missing information
        if (title == "Video Downloader Content" || thumbnail == null) {
            try {
                val oEmbedUrl = "https://noembed.com/embed?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
                val request = Request.Builder().url(oEmbedUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrEmpty()) {
                            val json = JSONObject(bodyString)
                            if (json.has("title")) title = json.getString("title")
                            if (json.has("thumbnail_url")) thumbnail = json.getString("thumbnail_url")
                            if (json.has("author_name") && uploader == null) uploader = json.getString("author_name")
                            if (json.has("provider_name") && source == null) source = json.getString("provider_name")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "oEmbed fetch failed", e)
            }
        }

        // 3. Fallback to OpenGraph HTML Scraping with browser headers and user cookie if still missing
        if (title == "Video Downloader Content" || thumbnail == null) {
            try {
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Sec-Ch-Ua", "\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"")
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")

                customYoutubeCookie?.let {
                    if (it.isNotEmpty()) {
                        requestBuilder.header("Cookie", it)
                    }
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        
                        // Parse title
                        val titleRegex = "<title>(.*?)</title>".toRegex(RegexOption.IGNORE_CASE)
                        val titleMatch = titleRegex.find(html)
                        if (titleMatch != null && title == "Video Downloader Content") {
                            title = titleMatch.groupValues[1].trim()
                        }

                        // Parse og:image (thumbnail)
                        val ogImageRegex = "<meta\\s+property=\"og:image\"\\s+content=\"(.*?)\"".toRegex(RegexOption.IGNORE_CASE)
                        val ogImageMatch = ogImageRegex.find(html)
                        if (ogImageMatch != null && thumbnail == null) {
                            thumbnail = ogImageMatch.groupValues[1]
                        }

                        // Parse og:description
                        val ogDescRegex = "<meta\\s+property=\"og:description\"\\s+content=\"(.*?)\"".toRegex(RegexOption.IGNORE_CASE)
                        val ogDescMatch = ogDescRegex.find(html)
                        if (ogDescMatch != null) {
                            description = ogDescMatch.groupValues[1]
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "HTML scrap fallback failed", e)
            }
        }

        // Resolve source and ID from URL if still missing
        val uri = java.net.URI(url)
        val domain = uri.host?.replace("www.", "") ?: "Video Source"
        if (source == null) {
            source = domain.substringBefore(".")
        }
        val id = java.util.UUID.nameUUIDFromBytes(url.toByteArray()).toString().take(8)

        // Generate dynamic video formats based on platform
        val formats = mutableListOf<VideoFormat>()
        val resolutions = listOf(
            Triple("1080", 1920, 1080),
            Triple("720", 1280, 720),
            Triple("480", 854, 480),
            Triple("360", 640, 360)
        )

        for (res in resolutions) {
            formats.add(
                VideoFormat(
                    formatId = res.first,
                    formatName = "${res.first}p",
                    extension = "mp4",
                    width = res.second,
                    height = res.third,
                    resolution = "${res.second}x${res.third}",
                    fps = 30.0,
                    videoCodec = "h264",
                    audioCodec = "aac",
                    fileSize = null,
                    approximateFileSize = (res.second * res.third * 0.15).toLong(), // Estimate size
                    bitrate = res.first.toDouble() * 1000,
                    hasVideo = true,
                    hasAudio = true
                )
            )
        }

        // Add audio only format
        formats.add(
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
                approximateFileSize = 4_000_000L,
                bitrate = 128.0,
                hasVideo = false,
                hasAudio = true
            )
        )

        return VideoInfo(
            id = id,
            title = title,
            description = description,
            thumbnail = thumbnail,
            duration = duration,
            source = source,
            uploader = uploader,
            webpageUrl = url,
            formats = formats
        )
    }

    suspend fun getDownloadUrl(url: String, quality: String, isAudioOnly: Boolean): Pair<String, String> {
        val cobaltUrls = mutableListOf<String>()
        
        // Add custom Cobalt URL if configured
        val custom = customCobaltUrl?.trim()
        if (!custom.isNullOrEmpty()) {
            val formatted = if (custom.endsWith("/api/json")) custom else {
                if (custom.endsWith("/")) custom else "$custom/"
            }
            cobaltUrls.add(formatted)
        }
        
        // Add verified public Cobalt instances (v10+ and alternates)
        cobaltUrls.add("https://rue-cobalt.xenon.zone/")
        cobaltUrls.add("https://dog.kittycat.boo/")
        cobaltUrls.add("https://cobalt.fast-ref.xyz/")
        cobaltUrls.add("https://cobalt.pablomg.net/")
        cobaltUrls.add("https://co.wuk.sh/")
        cobaltUrls.add("https://api.cobalt.tools/")
        cobaltUrls.add("https://cobaltapi.cjs.nz/")
        cobaltUrls.add("https://cobalt.lucasl.dev/")
        cobaltUrls.add("https://cobalt.k6.vc/")
        cobaltUrls.add("https://co.v9.sh/")

        var bestError = ""
        var bestErrorPriority = 0 // 0 = none, 1 = low, 2 = medium, 3 = high

        val updateError = { newError: String, priority: Int ->
            if (priority > bestErrorPriority) {
                bestError = newError
                bestErrorPriority = priority
            } else if (priority == bestErrorPriority && bestError.isEmpty()) {
                bestError = newError
            }
        }
        for (baseApiUrl in cobaltUrls) {
            // First try Cobalt v10+ format, then fallback to Cobalt v7 style
            val attempts = if (baseApiUrl.endsWith("/api/json")) {
                // If it already ends with api/json, try v7 directly
                listOf(Pair(baseApiUrl, 7))
            } else {
                listOf(
                    Pair(baseApiUrl, 10),
                    Pair(if (baseApiUrl.endsWith("/")) "${baseApiUrl}api/json" else "$baseApiUrl/api/json", 7)
                )
            }

            var skipRemainingAttempts = false
            for ((requestUrl, apiVersion) in attempts) {
                if (skipRemainingAttempts) break
                try {
                    val jsonObject = JSONObject().apply {
                        put("url", url)
                        if (apiVersion == 10) {
                            val mappedQuality = when (quality) {
                                "1080" -> "1080p"
                                "720" -> "720p"
                                "480" -> "480p"
                                "360" -> "360p"
                                else -> if (quality.endsWith("p") || quality == "max") quality else "${quality}p"
                            }
                            put("videoQuality", mappedQuality)
                            put("downloadMode", if (isAudioOnly) "audio" else "video")
                            put("alwaysProxy", true) // Request Cobalt server to proxy/tunnel the download
                            put("youtubeVideoCodec", "h264") // highly compatible h264
                        } else {
                            put("videoQuality", quality)
                            put("isAudioOnly", isAudioOnly)
                            put("isTunnel", true) // Request Cobalt server to proxy/tunnel the download
                            put("youtubeVideoCodec", "h264") // highly compatible h264
                        }
                    }

                    val requestBody = jsonObject.toString().toRequestBody("application/json".toMediaType())
                    val requestBuilder = Request.Builder()
                        .url(requestUrl)
                        .post(requestBody)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .header("Origin", "https://cobalt.tools")
                        .header("Referer", "https://cobalt.tools/")
                        .header("Sec-Ch-Ua", "\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"")
                        .header("Sec-Ch-Ua-Mobile", "?0")
                        .header("Sec-Ch-Ua-Platform", "\"Windows\"")

                    customYoutubeCookie?.let {
                        if (it.isNotEmpty()) {
                            requestBuilder.header("Cookie", it)
                        }
                    }

                    val request = requestBuilder.build()

                    client.newCall(request).execute().use { response ->
                        val responseString = response.body?.string() ?: ""
                        val trimmedResponse = responseString.trim()
                        if (trimmedResponse.isNotEmpty()) {
                            if (trimmedResponse.startsWith("<!DOCTYPE") || trimmedResponse.startsWith("<html") || trimmedResponse.startsWith("<HTML")) {
                                val err = if (!custom.isNullOrEmpty() && requestUrl.contains(custom)) {
                                    "Your custom extraction server ($custom) returned an HTML web page instead of JSON API data. Please verify your custom URL is correct (e.g., includes the port like :9000 or ends with '/api/json' if required), and is not the front-end web UI."
                                } else {
                                    "The public extraction server ($requestUrl) returned an HTML error page. This usually means the server is blocked by YouTube's bot detection, rate-limited, or protected by a Cloudflare challenge."
                                }
                                updateError(err, 1)
                                Log.w(TAG, "Server $requestUrl returned HTML: ${trimmedResponse.take(200)}")
                            } else if (!trimmedResponse.startsWith("{") && !trimmedResponse.startsWith("[")) {
                                val err = "Server returned an invalid non-JSON response (HTTP ${response.code})."
                                updateError(err, 1)
                                Log.w(TAG, "Server $requestUrl returned non-JSON: ${trimmedResponse.take(200)}")
                            } else {
                                // Received JSON response - don't fall back to older API versions on this server
                                skipRemainingAttempts = true
                                val responseJson = JSONObject(responseString)
                                val status = responseJson.optString("status", "")
                                
                                if (response.isSuccessful && (status == "success" || status == "stream" || status == "redirect" || status == "tunnel" || responseJson.has("url"))) {
                                    val streamUrl = responseJson.optString("url", "")
                                    if (streamUrl.isNotEmpty()) {
                                        val filename = responseJson.optString("filename", "video.mp4")
                                        return Pair(streamUrl, filename)
                                    }
                                } else if (response.isSuccessful && status == "picker") {
                                    val pickerArray = responseJson.optJSONArray("picker")
                                    if (pickerArray != null && pickerArray.length() > 0) {
                                        var foundUrl = ""
                                        // Try to find the first item of type "video"
                                        for (i in 0 until pickerArray.length()) {
                                            val item = pickerArray.getJSONObject(i)
                                            val itemType = item.optString("type", "")
                                            if (itemType == "video") {
                                                val streamUrl = item.optString("url", "")
                                                if (streamUrl.isNotEmpty()) {
                                                    foundUrl = streamUrl
                                                    break
                                                }
                                            }
                                        }
                                        // If no video is found in the picker, fallback to the first item (might be a photo)
                                        if (foundUrl.isEmpty()) {
                                            val item = pickerArray.getJSONObject(0)
                                            foundUrl = item.optString("url", "")
                                        }

                                        if (foundUrl.isNotEmpty()) {
                                            val filename = responseJson.optString("filename", "video.mp4")
                                            return Pair(foundUrl, filename)
                                        }
                                    }
                                }
                                
                                // Extract detailed Cobalt API error code if unsuccessful or status is error
                                val errorObj = responseJson.optJSONObject("error")
                                val errorCode = errorObj?.optString("code") ?: responseJson.optString("error")
                                if (!errorCode.isNullOrEmpty()) {
                                    val mapped = mapCobaltError(errorCode, url)
                                    val priority = getCobaltErrorPriority(errorCode)
                                    updateError(mapped, priority)
                                } else {
                                    val text = responseJson.optString("text")
                                    if (text.isNotEmpty()) {
                                        val mapped = mapCobaltError(text, url)
                                        val priority = getCobaltErrorPriority(text)
                                        updateError(mapped, priority)
                                    } else {
                                        updateError("HTTP ${response.code}: ${response.message}", 1)
                                    }
                                }
                            }
                        } else {
                            updateError("Empty response (HTTP ${response.code}: ${response.message})", 1)
                        }
                    }
                } catch (e: Exception) {
                    val msg = e.message ?: "Network error"
                    updateError(msg, 1)
                    Log.e(TAG, "Cobalt request failed on $requestUrl (v$apiVersion)", e)
                }
            }
        }
        
        throw Exception(if (bestError.isNotEmpty()) bestError else "Failed to connect to extraction servers")
    }

    private fun getCobaltErrorPriority(code: String): Int {
        val lower = code.lowercase()
        return when {
            lower.contains("login") || lower.contains("cookie") ||
            lower.contains("decryption") || lower.contains("signature") || lower.contains("cipher") ||
            lower.contains("age_restricted") || lower.contains("restricted") -> 3
            lower.contains("rate_limit") || lower.contains("rate-limit") || lower.contains("429") ||
            lower.contains("unsupported") -> 2
            else -> 1
        }
    }

    private fun mapCobaltError(code: String, originalUrl: String): String {
        val isInstagram = originalUrl.contains("instagram.com") || originalUrl.contains("instagr.am")
        val isTiktok = originalUrl.contains("tiktok.com")
        val serviceName = when {
            isInstagram -> "Instagram"
            isTiktok -> "TikTok"
            else -> "YouTube"
        }
        return when {
            code.contains("login") || code.contains("cookie") -> {
                if (isInstagram) {
                    "Instagram requires account verification or session cookies to download this private/restricted video. Please make sure the post is public and accessible without logging in."
                } else {
                    "YouTube requires account verification or session cookies to download this video. Please go to Settings ⚙️ and click 'Sign in & Auto-Extract Cookie' to authenticate."
                }
            }
            code.contains("decryption") || code.contains("signature") || code.contains("cipher") -> {
                "$serviceName's decryption/signature failed. Try selecting an alternate Cobalt preset or updating your cookie in Settings ⚙️."
            }
            code.contains("rate_limit") || code.contains("rate-limit") || code.contains("429") -> {
                "This extraction server is rate-limited or blocked by $serviceName's bot protection. Please select a different Cobalt preset in Settings ⚙️."
            }
            code.contains("age_restricted") || code.contains("restricted") -> {
                if (isInstagram) {
                    "This Instagram video is restricted or age-gated. Please try another post or select an alternate Cobalt preset."
                } else {
                    "This video is restricted or age-gated. Please go to Settings ⚙️ and use 'Sign in & Auto-Extract Cookie' to authenticate."
                }
            }
            code.contains("unsupported") -> {
                "This video format or URL is not supported by the extraction server."
            }
            else -> code
        }
    }
}
