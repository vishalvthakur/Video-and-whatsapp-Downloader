package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_history")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mediaId: String,
    val title: String,
    val source: String?,
    val thumbnailUrl: String?,
    val localUri: String,
    val fileSize: Long?,
    val resolution: String?,
    val extension: String,
    val downloadDate: Long = System.currentTimeMillis(),
    val status: String
)
