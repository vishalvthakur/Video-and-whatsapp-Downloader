package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_history ORDER BY downloadDate DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long

    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun deleteDownloadById(id: Int)

    @Query("SELECT * FROM download_history WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getDownloadByMediaId(mediaId: String): DownloadEntity?
}
