package com.eliteprime.app.domain.repository

import com.eliteprime.app.data.local.entity.PlaybackHistory
import kotlinx.coroutines.flow.Flow

interface PlaybackHistoryRepository {
    fun getRecentlyWatched(limit: Int): Flow<List<PlaybackHistory>>
    
    suspend fun getPlaybackHistory(
        streamId: Long,
        type: String,
        providerId: Long
    ): PlaybackHistory?

    suspend fun recordPlayback(history: PlaybackHistory)
    suspend fun updateResumePosition(streamId: Long, type: String, providerId: Long, positionMs: Long, durationMs: Long)
    suspend fun deleteHistory(streamId: Long, type: String, providerId: Long)
    suspend fun clearAll()
}
