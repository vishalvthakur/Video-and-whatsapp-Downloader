package com.example.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * MediaMergeManager handles merging separate video and audio tracks into a single MP4 file.
 * 
 * DESIGN DECISION: Native Android MediaMuxer & MediaExtractor are selected instead of external FFmpeg.
 * REASONS:
 * 1. Zero APK bloat (FFmpeg binaries add 30MB-80MB of native libraries).
 * 2. Perfect CPU architecture compatibility (native binaries can crash on specific device chipsets).
 * 3. Hardware-optimized stream copy (Muxer directly copies pre-encoded packets, resulting in 
 *    near-instantaneous merges and zero battery drainage compared to CPU-heavy transcoding).
 */
object MediaMergeManager {
    private const val TAG = "MediaMergeManager"

    suspend fun mergeVideoAndAudio(
        videoPath: String,
        audioPath: String,
        outputPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val videoFile = File(videoPath)
        val audioFile = File(audioPath)
        val outputFile = File(outputPath)

        if (!videoFile.exists() || videoFile.length() == 0L) {
            Log.e(TAG, "Video file is invalid or missing.")
            return@withContext false
        }
        if (!audioFile.exists() || audioFile.length() == 0L) {
            Log.e(TAG, "Audio file is invalid or missing.")
            return@withContext false
        }

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        var muxer: MediaMuxer? = null
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()

        try {
            videoExtractor.setDataSource(videoPath)
            audioExtractor.setDataSource(audioPath)

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Select video track
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    videoTrackIndex = muxer.addTrack(format)
                    videoFormat = format
                    break
                }
            }

            // Select audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    audioTrackIndex = muxer.addTrack(format)
                    audioFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1) {
                Log.e(TAG, "No video track found in source.")
                return@withContext false
            }

            // If audio track is missing, we just mux video or return failure
            if (audioTrackIndex == -1) {
                Log.w(TAG, "No audio track found in source.")
            }

            muxer.start()

            val bufferSize = 1024 * 1024 // 1MB buffer
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // Copy Video Track
            videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Copy Audio Track if exists
            if (audioTrackIndex != -1) {
                audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) {
                        break
                    }
                    bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    bufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                    audioExtractor.advance()
                }
            }

            muxer.stop()
            Log.d(TAG, "Merge successful. File created: $outputPath")
            
            // Clean up temporary files
            try {
                videoFile.delete()
                audioFile.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clean up temp source files", e)
            }

            return@withContext outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error merging tracks", e)
            return@withContext false
        } finally {
            try {
                videoExtractor.release()
                audioExtractor.release()
                muxer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing resources", e)
            }
        }
    }
}
