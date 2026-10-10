package com.ghost.tv.domain.repository

import com.ghost.tv.domain.model.*
import kotlinx.coroutines.flow.Flow

interface DownloadManager {
    fun observeAllDownloads(): Flow<List<DownloadItem>>
    fun observeDownload(id: String): Flow<DownloadItem?>
    fun observeStorageState(): Flow<DownloadStorageConfig>
    suspend fun enqueueDownload(request: DownloadRequest): Result<DownloadItem>
    suspend fun resumeDownload(id: String): Result<Unit>
    suspend fun cancelDownload(id: String): Result<Unit>
    fun onPlaybackStarted()
    fun onPlaybackStopped()
    suspend fun deleteDownload(id: String): Result<Unit>
    suspend fun updateStorageConfig(treeUri: String?, displayName: String?): Result<DownloadStorageConfig>
}
