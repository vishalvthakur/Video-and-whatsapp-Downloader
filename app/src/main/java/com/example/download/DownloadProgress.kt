package com.example.download

data class DownloadProgress(
    val percent: Float,
    val downloadedBytes: Long?,
    val totalBytes: Long?,
    val speedBytesPerSecond: Long?,
    val etaSeconds: Long?
)

sealed class DownloadResult {
    data class Success(val filePath: String) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}
