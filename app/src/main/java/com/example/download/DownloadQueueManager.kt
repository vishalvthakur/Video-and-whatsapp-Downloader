package com.example.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class QueueStatus {
    PENDING,
    EXTRACTING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class QueueItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "Pending extraction...",
    val status: QueueStatus = QueueStatus.PENDING,
    val progress: Int = 0,
    val errorMessage: String? = null
)

object DownloadQueueManager {
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    fun addToQueue(urls: List<String>) {
        val newItems = urls.filter { it.isNotBlank() }.map { url ->
            QueueItem(url = url.trim())
        }
        _queue.value = _queue.value + newItems
    }

    fun updateItem(id: String, transform: (QueueItem) -> QueueItem) {
        _queue.value = _queue.value.map { item ->
            if (item.id == id) transform(item) else item
        }
    }

    fun updateItemStatus(id: String, status: QueueStatus) {
        updateItem(id) { it.copy(status = status) }
    }

    fun updateItemTitle(id: String, title: String) {
        updateItem(id) { it.copy(title = title) }
    }

    fun updateItemProgress(id: String, progress: Int) {
        updateItem(id) { it.copy(progress = progress) }
    }

    fun updateItemError(id: String, error: String) {
        updateItem(id) { it.copy(status = QueueStatus.FAILED, errorMessage = error) }
    }

    fun setProcessing(processing: Boolean) {
        _isProcessing.value = processing
    }

    fun clearQueue() {
        _queue.value = emptyList()
        _isProcessing.value = false
    }

    fun removeItem(id: String) {
        _queue.value = _queue.value.filter { it.id != id }
    }
}
