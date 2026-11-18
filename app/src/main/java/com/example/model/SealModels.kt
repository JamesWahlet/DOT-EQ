package com.example.model

import java.util.UUID

enum class SealAudioFormat(val label: String, val extension: String, val mimeType: String) {
    M4A("M4A / AAC", "m4a", "audio/mp4"),
    MP3("MP3", "mp3", "audio/mpeg"),
    FLAC("FLAC", "flac", "audio/flac"),
    OGG("OPUS / OGG", "ogg", "audio/ogg"),
    ORIGINAL("ORIGINAL", "bin", "application/octet-stream")
}

enum class SealAudioQuality(
    val label: String,
    val description: String,
    val targetKbps: Int
) {
    AUTO("AUTO", "Automatisch beste Qualität über den Link", 0),
    LOW("LOW", "128 kbps · Kompakt", 128),
    MEDIUM("MEDIUM", "256 kbps · Ausgewogen", 256),
    LOSSLESS("LOSSLESS", "Verlustfrei / maximale Qualität", 1411)
}

enum class SealSaveLocation(val label: String, val relativeDir: String) {
    MUSIC("Music/DotEQ", "Music/DotEQ"),
    DOWNLOADS("Downloads/DotEQ", "Download/DotEQ"),
    CUSTOM("Eigener Ordner", "Music/DotEQ")
}

data class SealSettings(
    val quality: SealAudioQuality = SealAudioQuality.AUTO,
    val saveLocation: SealSaveLocation = SealSaveLocation.MUSIC,
    val customFolderUri: String? = null,
    val customFolderLabel: String = "",
    val format: SealAudioFormat = SealAudioFormat.M4A,
    val autoConvertAudioOnly: Boolean = true,
    val filenameTemplate: String = "%(artist)s - %(title)s"
) {
    val effectiveLocationLabel: String
        get() = if (saveLocation == SealSaveLocation.CUSTOM && customFolderLabel.isNotBlank()) {
            customFolderLabel
        } else {
            saveLocation.label
        }
}

enum class SealDownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class SealTrackSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String,
    val album: String = "",
    val sourceLabel: String,
    val shareUrl: String,
    val downloadUrl: String,
    val durationLabel: String = "",
    val suggestedFilename: String
)

data class SealDownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String = "",
    val sourceUrl: String,
    val resolvedMediaUrl: String,
    val fileName: String,
    val formatLabel: String,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val status: SealDownloadStatus = SealDownloadStatus.QUEUED,
    val savedUriString: String? = null,
    val errorMessage: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
