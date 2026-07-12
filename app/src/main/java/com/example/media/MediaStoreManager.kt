package com.example.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

object MediaStoreManager {
    private const val TAG = "MediaStoreManager"

    suspend fun saveVideoToGallery(
        context: Context,
        localFile: File,
        title: String,
        mimeType: String = "video/mp4"
    ): Uri? = withContext(Dispatchers.IO) {
        if (!localFile.exists() || localFile.length() == 0L) {
            Log.e(TAG, "Local source file is invalid.")
            return@withContext null
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        // Sanitize display name
        val sanitizedTitle = title.replace(Regex("[/:*?\"<>|]"), "_")
        val displayName = if (sanitizedTitle.length > 100) sanitizedTitle.take(100) else sanitizedTitle
        val fileName = "$displayName [${System.currentTimeMillis().toString().takeLast(6)}].mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoDownloader")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        var uri: Uri? = null
        try {
            uri = resolver.insert(collection, values)
            if (uri == null) {
                Log.e(TAG, "Failed to insert MediaStore record.")
                return@withContext null
            }

            resolver.openOutputStream(uri).use { outputStream ->
                if (outputStream == null) {
                    Log.e(TAG, "Failed to open MediaStore output stream.")
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            Log.d(TAG, "File successfully saved to MediaStore: $uri")
            
            // Delete original cache file safely
            try {
                localFile.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Could not delete cached source file", e)
            }

            return@withContext uri
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video to MediaStore", e)
            if (uri != null) {
                try {
                    resolver.delete(uri, null, null)
                } catch (ex: Exception) {
                    Log.e(TAG, "Clean up failed for broken MediaStore entry", ex)
                }
            }
            return@withContext null
        }
    }

    suspend fun saveMediaFromUri(
        context: Context,
        srcUri: Uri,
        title: String,
        isVideo: Boolean
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }

        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
        val folder = if (isVideo) "Movies/WhatsAppSaver" else "Pictures/WhatsAppSaver"
        val sanitizedTitle = title.replace(Regex("[/:*?\"<>|]"), "_")
        val suffix = if (isVideo) ".mp4" else ".jpg"
        val fileName = "$sanitizedTitle [${System.currentTimeMillis().toString().takeLast(6)}]$suffix"

        val values = ContentValues().apply {
            if (isVideo) {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, folder)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            } else {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, folder)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
        }

        var destUri: Uri? = null
        try {
            destUri = resolver.insert(collection, values)
            if (destUri == null) return@withContext null

            resolver.openOutputStream(destUri).use { outputStream ->
                if (outputStream == null) return@withContext null
                resolver.openInputStream(srcUri).use { inputStream ->
                    if (inputStream == null) return@withContext null
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
                outputStream.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                if (isVideo) {
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                } else {
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(destUri, values, null, null)
            }

            return@withContext destUri
        } catch (e: Exception) {
            Log.e(TAG, "Error saving media from URI", e)
            if (destUri != null) {
                try {
                    resolver.delete(destUri, null, null)
                } catch (ex: Exception) {}
            }
            return@withContext null
        }
    }

    suspend fun downloadAndSaveImage(
        context: Context,
        imageUrl: String,
        title: String
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val sanitizedTitle = title.replace(Regex("[/:*?\"<>|]"), "_")
        val fileName = "$sanitizedTitle [${System.currentTimeMillis().toString().takeLast(6)}].jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WhatsAppProfileDownloader")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        var destUri: Uri? = null
        try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(imageUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null

                destUri = resolver.insert(collection, values)
                if (destUri == null) return@withContext null

                resolver.openOutputStream(destUri).use { outputStream ->
                    if (outputStream == null) return@withContext null
                    body.source().inputStream().use { inputStream ->
                        val buffer = ByteArray(16384)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                    outputStream.flush()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(destUri, values, null, null)
                }
            }
            return@withContext destUri
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image", e)
            if (destUri != null) {
                try {
                    resolver.delete(destUri, null, null)
                } catch (ex: Exception) {}
            }
            return@withContext null
        }
    }
}
