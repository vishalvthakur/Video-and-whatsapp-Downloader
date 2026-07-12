package com.example.data.repository

import com.example.data.dao.DownloadDao
import com.example.data.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    suspend fun insertDownload(download: DownloadEntity): Long {
        return downloadDao.insertDownload(download)
    }

    suspend fun deleteDownloadById(id: Int) {
        downloadDao.deleteDownloadById(id)
    }

    suspend fun getDownloadByMediaId(mediaId: String): DownloadEntity? {
        return downloadDao.getDownloadByMediaId(mediaId)
    }
}
