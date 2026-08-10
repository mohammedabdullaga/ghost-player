package com.eliteprime.app.data.local.entity

import com.eliteprime.app.domain.model.DownloadContentType
import com.eliteprime.app.domain.model.DownloadItem
import com.eliteprime.app.domain.model.DownloadStatus
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class DownloadEntity(
    @Id var objectBoxId: Long = 0,
    @Index var downloadId: String = "",
    var providerId: Long = 0,
    var contentType: String = "", // DownloadContentType
    var contentId: Long = 0,
    var contentName: String = "",
    var streamUrl: String = "",
    var sourceStreamUrl: String? = null,
    var sourceStreamId: Long? = null,
    var containerExtension: String? = null,
    var posterUrl: String? = null,
    var outputUri: String? = null,
    var outputDisplayPath: String? = null,
    var status: String = "PENDING", // DownloadStatus
    var bytesWritten: Long = 0,
    var totalBytes: Long? = null,
    var supportsResume: Boolean = false,
    var retryCount: Int = 0,
    var createdAt: Long = System.currentTimeMillis(),
    var completedAt: Long? = null,
    var failureReason: String? = null,
    var seriesId: Long? = null,
    var seasonNumber: Int? = null,
    var episodeNumber: Int? = null
) {
    fun toDomain(): DownloadItem = DownloadItem(
        id = downloadId,
        providerId = providerId,
        contentType = DownloadContentType.valueOf(contentType),
        contentId = contentId,
        contentName = contentName,
        streamUrl = streamUrl,
        sourceStreamUrl = sourceStreamUrl,
        sourceStreamId = sourceStreamId,
        containerExtension = containerExtension,
        posterUrl = posterUrl,
        outputUri = outputUri,
        outputDisplayPath = outputDisplayPath,
        status = DownloadStatus.valueOf(status),
        bytesWritten = bytesWritten,
        totalBytes = totalBytes,
        supportsResume = supportsResume,
        retryCount = retryCount,
        createdAt = createdAt,
        completedAt = completedAt,
        failureReason = failureReason,
        seriesId = seriesId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber
    )

    companion object {
        fun fromDomain(item: DownloadItem): DownloadEntity = DownloadEntity(
            downloadId = item.id,
            providerId = item.providerId,
            contentType = item.contentType.name,
            contentId = item.contentId,
            contentName = item.contentName,
            streamUrl = item.streamUrl,
            sourceStreamUrl = item.sourceStreamUrl,
            sourceStreamId = item.sourceStreamId,
            containerExtension = item.containerExtension,
            posterUrl = item.posterUrl,
            outputUri = item.outputUri,
            outputDisplayPath = item.outputDisplayPath,
            status = item.status.name,
            bytesWritten = item.bytesWritten,
            totalBytes = item.totalBytes,
            supportsResume = item.supportsResume,
            retryCount = item.retryCount,
            createdAt = item.createdAt,
            completedAt = item.completedAt,
            failureReason = item.failureReason,
            seriesId = item.seriesId,
            seasonNumber = item.seasonNumber,
            episodeNumber = item.episodeNumber
        )
    }
}
