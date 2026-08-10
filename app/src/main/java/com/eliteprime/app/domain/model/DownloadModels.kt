package com.eliteprime.app.domain.model

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class DownloadContentType {
    MOVIE,
    SERIES_EPISODE
}

data class DownloadItem(
    val id: String,
    val providerId: Long,
    val contentType: DownloadContentType,
    val contentId: Long,
    val contentName: String,
    val streamUrl: String,
    val sourceStreamUrl: String? = null,
    val sourceStreamId: Long? = null,
    val containerExtension: String? = null,
    val posterUrl: String? = null,
    val outputUri: String? = null,
    val outputDisplayPath: String? = null,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val bytesWritten: Long = 0L,
    val totalBytes: Long? = null,
    val supportsResume: Boolean = false,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val failureReason: String? = null,
    val seriesId: Long? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    
    // Speed and ETA
    val downloadSpeedBps: Long = 0L,
    val estimatedTimeRemainingMs: Long? = null
)

data class DownloadRequest(
    val providerId: Long,
    val contentType: DownloadContentType,
    val contentId: Long,
    val contentName: String,
    val streamUrl: String,
    val sourceStreamUrl: String? = null,
    val sourceStreamId: Long? = null,
    val containerExtension: String? = null,
    val posterUrl: String? = null,
    val seriesId: Long? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)

data class DownloadStorageConfig(
    val treeUri: String? = null,
    val displayName: String? = null,
    val outputDirectory: String? = null,
    val availableBytes: Long? = null,
    val isWritable: Boolean = false
)
