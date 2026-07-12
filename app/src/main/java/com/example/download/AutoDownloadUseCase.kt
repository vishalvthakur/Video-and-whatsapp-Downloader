package com.example.download

import android.content.Context
import android.util.Log
import com.example.data.database.DownloadDatabase
import com.example.data.repository.DownloadRepository
import com.example.extractor.FormatSelector
import com.example.extractor.YtDlpVideoExtractor

class AutoDownloadUseCase(private val context: Context) {
    private val TAG = "AutoDownloadUseCase"

    suspend fun execute(url: String, onServiceAction: (action: String, value: String) -> Unit) {
        try {
            val extractor = YtDlpVideoExtractor()
            val videoInfo = extractor.extract(url)

            // Emit the video title as soon as it's extracted
            onServiceAction("UPDATE_TITLE", videoInfo.title)

            val availableFormats = FormatSelector.getAvailableQualities(videoInfo.formats)
            val bestFormat = availableFormats.firstOrNull { (it.height ?: 0) <= 1080 }
                ?: availableFormats.firstOrNull()
                ?: videoInfo.formats.firstOrNull()

            val formatId = bestFormat?.formatId ?: "1080"

            val database = DownloadDatabase.getDatabase(context)
            val repository = DownloadRepository(database.downloadDao())

            MediaDownloadManager.startDownload(
                context = context,
                url = videoInfo.webpageUrl,
                formatId = formatId,
                videoInfo = videoInfo,
                repository = repository,
                onServiceAction = onServiceAction
            )
        } catch (e: Exception) {
            Log.e(TAG, "Extraction or download failed during AutoDownloadUseCase execution", e)
            onServiceAction("FAILED", e.message ?: "Analysis failed")
        }
    }
}
