package com.example.extractor

import com.example.download.DownloadProgress
import com.example.download.DownloadResult

data class VideoInfo(
    val id: String,
    val title: String,
    val description: String?,
    val thumbnail: String?,
    val duration: Long?, // in seconds
    val source: String?,
    val uploader: String?,
    val webpageUrl: String,
    val formats: List<VideoFormat>
)

data class VideoFormat(
    val formatId: String,
    val formatName: String?,
    val extension: String,
    val width: Int?,
    val height: Int?,
    val resolution: String?,
    val fps: Double?,
    val videoCodec: String?,
    val audioCodec: String?,
    val fileSize: Long?,
    val approximateFileSize: Long?,
    val bitrate: Double?,
    val hasVideo: Boolean,
    val hasAudio: Boolean
)

interface VideoExtractor {
    suspend fun extract(url: String): VideoInfo
    suspend fun download(
        url: String,
        formatSelector: String,
        outputPath: String,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadResult
    fun cancel()
}
