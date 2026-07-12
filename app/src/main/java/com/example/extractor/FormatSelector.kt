package com.example.extractor

object FormatSelector {
    fun getAvailableQualities(formats: List<VideoFormat>): List<VideoFormat> {
        return formats
            .filter { it.hasVideo }
            .groupBy { it.height }
            .mapNotNull { (_, formatList) ->
                // Prefer MP4 container and highest bitrate
                formatList.sortedWith(
                    compareByDescending<VideoFormat> {
                        it.extension.equals("mp4", ignoreCase = true)
                    }.thenByDescending {
                        it.bitrate ?: 0.0
                    }
                ).firstOrNull()
            }
            .sortedByDescending { it.height ?: 0 }
    }

    fun getBestAudioFormat(formats: List<VideoFormat>): VideoFormat? {
        return formats
            .filter { it.hasAudio && !it.hasVideo }
            .maxByOrNull { it.bitrate ?: 0.0 }
            ?: formats.filter { it.hasAudio }.maxByOrNull { it.bitrate ?: 0.0 }
    }
}
