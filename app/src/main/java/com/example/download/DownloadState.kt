package com.example.download

import com.example.extractor.VideoInfo

sealed interface DownloadState {
    object Idle : DownloadState
    object Extracting : DownloadState
    data class MetadataLoaded(val videoInfo: VideoInfo) : DownloadState
    object PreparingDownload : DownloadState
    data class Downloading(val progress: DownloadProgress, val message: String = "") : DownloadState
    object Merging : DownloadState
    object Saving : DownloadState
    data class Completed(val filePath: String, val title: String, val uri: String) : DownloadState
    data class Failed(val error: String) : DownloadState
    object Cancelled : DownloadState
}
