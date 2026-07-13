package com.example.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object FileStorageUtility {
    private const val TAG = "FileStorageUtility"
    private const val PREFS_NAME = "video_downloader_prefs"
    private const val KEY_PREFERRED_FOLDER = "preferred_download_folder"

    fun getPreferredSaveFolder(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PREFERRED_FOLDER, "Movies") ?: "Movies"
    }

    fun setPreferredSaveFolder(context: Context, folder: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PREFERRED_FOLDER, folder).apply()
    }

    suspend fun moveVideoToPublicFolder(
        context: Context,
        localFile: File,
        title: String,
        mimeType: String = "video/mp4"
    ): Uri? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!localFile.exists() || localFile.length() == 0L) {
            Log.e(TAG, "Source file is invalid or empty.")
            return@withContext null
        }

        val preferredFolder = getPreferredSaveFolder(context)
        val sanitizedTitle = title.replace(Regex("[/:*?\"<>|]"), "_")
        val displayName = if (sanitizedTitle.length > 100) sanitizedTitle.take(100) else sanitizedTitle
        
        // Determine file extension based on mimeType
        val extension = if (mimeType.contains("audio", ignoreCase = true) || mimeType.contains("mpeg", ignoreCase = true)) {
            "mp3"
        } else {
            "mp4"
        }
        val fileName = "$displayName [${System.currentTimeMillis().toString().takeLast(6)}].$extension"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ Scoped Storage (using MediaStore)
            val resolver = context.contentResolver
            
            val relativePath = if (preferredFolder.equals("Downloads", ignoreCase = true)) {
                "Download/VideoDownloader"
            } else {
                "Movies/VideoDownloader"
            }

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val collection = if (mimeType.contains("audio", ignoreCase = true)) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            var uri: Uri? = null
            try {
                uri = resolver.insert(collection, values)
                if (uri == null) {
                    Log.e(TAG, "Failed to insert MediaStore entry.")
                    return@withContext null
                }

                resolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream == null) {
                        Log.e(TAG, "Failed to open output stream.")
                        return@withContext null
                    }
                    FileInputStream(localFile).use { inputStream ->
                        val buffer = ByteArray(16384)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                    outputStream.flush()
                }

                values.clear()
                if (mimeType.contains("audio", ignoreCase = true)) {
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                } else {
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(uri, values, null, null)

                Log.d(TAG, "File successfully moved to public folder via MediaStore Q+: $uri ($relativePath)")
                
                // Safely clean up the source file
                try {
                    localFile.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Could not delete original source file", e)
                }

                return@withContext uri
            } catch (e: Exception) {
                Log.e(TAG, "Error saving video via MediaStore", e)
                if (uri != null) {
                    try {
                        resolver.delete(uri, null, null)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Failed to cleanup invalid MediaStore entry", ex)
                    }
                }
                return@withContext null
            }
        } else {
            // Pre-Android 10: Legacy File System API
            try {
                val publicDirName = if (preferredFolder.equals("Downloads", ignoreCase = true)) {
                    Environment.DIRECTORY_DOWNLOADS
                } else {
                    Environment.DIRECTORY_MOVIES
                }

                val publicDir = Environment.getExternalStoragePublicDirectory(publicDirName)
                val targetDir = File(publicDir, "VideoDownloader")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                val destFile = File(targetDir, fileName)
                
                FileInputStream(localFile).use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        val buffer = ByteArray(16384)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        outputStream.flush()
                    }
                }

                Log.d(TAG, "File successfully copied to public folder pre-Q: ${destFile.absolutePath}")

                // Trigger media scanner to make file visible in system and get its URI
                val scanUri = suspendCoroutine<Uri?> { continuation ->
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(destFile.absolutePath),
                        arrayOf(mimeType)
                    ) { _, uri ->
                        continuation.resume(uri)
                    }
                }

                // Delete source file
                try {
                    localFile.delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Could not delete original source file", e)
                }

                return@withContext scanUri ?: Uri.fromFile(destFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving video via legacy File API", e)
                return@withContext null
            }
        }
    }
}
