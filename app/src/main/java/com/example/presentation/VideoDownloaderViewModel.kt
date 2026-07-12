package com.example.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.DownloadEntity
import com.example.data.repository.DownloadRepository
import com.example.download.DownloadState
import com.example.download.MediaDownloadManager
import com.example.extractor.VideoExtractor
import com.example.extractor.VideoInfo
import com.example.extractor.YtDlpVideoExtractor
import com.example.service.VideoDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ExtractionState {
    object Idle : ExtractionState
    object Loading : ExtractionState
    data class Success(val info: VideoInfo) : ExtractionState
    data class Error(val message: String) : ExtractionState
}

class VideoDownloaderViewModel(private val repository: DownloadRepository) : ViewModel() {
    private val TAG = "DownloaderViewModel"

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _extractionState = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val extractionState: StateFlow<ExtractionState> = _extractionState.asStateFlow()

    val downloadState: StateFlow<DownloadState> = MediaDownloadManager.downloadState

    val downloadHistory = repository.allDownloads

    fun onUrlChange(newUrl: String) {
        _urlInput.value = newUrl
    }

    fun analyzeUrl() {
        val url = _urlInput.value.trim()
        if (url.isEmpty()) {
            _extractionState.value = ExtractionState.Error("Please enter a URL")
            return
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _extractionState.value = ExtractionState.Error("Please enter a valid URL (starting with http:// or https://)")
            return
        }

        viewModelScope.launch {
            _extractionState.value = ExtractionState.Loading
            try {
                val extractor: VideoExtractor = YtDlpVideoExtractor()
                val metadata = withContext(Dispatchers.IO) {
                    extractor.extract(url)
                }
                _extractionState.value = ExtractionState.Success(metadata)
            } catch (e: Exception) {
                Log.e(TAG, "Extraction failed", e)
                _extractionState.value = ExtractionState.Error(
                    e.message ?: "Failed to extract video information. Please verify the URL and try again."
                )
            }
        }
    }

    fun resetExtraction() {
        _extractionState.value = ExtractionState.Idle
    }

    fun triggerDownload(context: Context, formatId: String, videoInfo: VideoInfo) {
        MediaDownloadManager.startDownload(
            context = context,
            url = videoInfo.webpageUrl,
            formatId = formatId,
            videoInfo = videoInfo,
            repository = repository,
            onServiceAction = { action, value ->
                try {
                    val intent = Intent(context, VideoDownloadService::class.java).apply {
                        this.action = action
                        when (action) {
                            "START" -> putExtra("TITLE", value)
                            "UPDATE_PROGRESS" -> {
                                val percent = value.replace("%", "").toIntOrNull() ?: 0
                                putExtra("PERCENT", percent)
                            }
                            "UPDATE_STATUS" -> putExtra("STATUS", value)
                            "COMPLETED" -> putExtra("TITLE", value)
                            "FAILED" -> putExtra("ERROR", value)
                        }
                    }
                    if (BuildVersionHelper.isAtLeastO()) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to communicate with VideoDownloadService", e)
                }
            }
        )
    }

    fun cancelDownload() {
        MediaDownloadManager.cancelActiveJob()
    }

    fun deleteHistoryItem(context: Context, item: DownloadEntity, deleteFile: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDownloadById(item.id)
            if (deleteFile) {
                try {
                    val uri = Uri.parse(item.localUri)
                    context.contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to physically delete media file", e)
                }
            }
        }
    }
}

object BuildVersionHelper {
    fun isAtLeastO(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
    }
}

class VideoDownloaderViewModelFactory(private val repository: DownloadRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VideoDownloaderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VideoDownloaderViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
