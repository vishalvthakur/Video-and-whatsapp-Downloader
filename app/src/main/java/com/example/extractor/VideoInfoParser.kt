package com.example.extractor

import java.util.Locale

object VideoInfoParser {
    fun formatDuration(durationInSeconds: Long?): String {
        if (durationInSeconds == null || durationInSeconds <= 0) return "--:--"
        val hours = durationInSeconds / 3600
        val minutes = (durationInSeconds % 3600) / 60
        val seconds = durationInSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun formatFileSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0) return "Unknown size"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.getDefault(), "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
            else -> String.format(Locale.getDefault(), "%.0f KB", kb)
        }
    }

    fun formatBitrate(bitrate: Double?): String {
        if (bitrate == null || bitrate <= 0) return ""
        val kbps = bitrate / 1000.0
        val mbps = kbps / 1000.0
        return if (mbps >= 1.0) {
            String.format(Locale.getDefault(), "%.1f Mbps", mbps)
        } else {
            String.format(Locale.getDefault(), "%.0f kbps", kbps)
        }
    }
}
