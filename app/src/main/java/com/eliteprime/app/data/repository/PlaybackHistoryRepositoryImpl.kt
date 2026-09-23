package com.eliteprime.app.data.repository

import android.util.Log
import com.eliteprime.app.data.local.entity.PlaybackHistory
import com.eliteprime.app.data.local.entity.PlaybackHistory_
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Episode
import com.eliteprime.app.domain.repository.PlaybackHistoryRepository
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import io.objectbox.kotlin.query
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackHistoryRepositoryImpl @Inject constructor(
    private val boxStore: BoxStore
) : PlaybackHistoryRepository {
    
    private val historyBox = boxStore.boxFor(PlaybackHistory::class.java)
    private val movieBox = boxStore.boxFor(Movie::class.java)
    private val episodeBox = boxStore.boxFor(Episode::class.java)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val pendingUpdates = ConcurrentHashMap<String, PlaybackHistory>()
    private val _pendingUpdatesFlow = MutableStateFlow<Map<String, PlaybackHistory>>(emptyMap())

    init {
        repositoryScope.launch {
            // Cleanup legacy/invalid records from previous bugs
            runCatching { historyBox.query().equal(PlaybackHistory_.providerId, 0L).build().use { it.remove() } }
            
            while (true) {
                delay(30000)
                runCatching { flushPendingUpdates() }
            }
        }
    }

    private fun generateKey(streamId: Long, type: String, providerId: Long): String {
        return "${providerId}_${type}_$streamId"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getRecentlyWatched(limit: Int): Flow<List<PlaybackHistory>> {
        val dbFlow = historyBox.query().orderDesc(PlaybackHistory_.lastPlayedAt).build().flow()

        return combine(dbFlow, _pendingUpdatesFlow) { dbList, pending ->
            val allItems = mutableListOf<PlaybackHistory>()
            allItems.addAll(dbList)
            
            val pendingItems = runCatching {
                synchronized(pendingUpdates) { ArrayList(pending.values) }
            }.getOrDefault(emptyList())

            pendingItems.forEach { pendingItem ->
                val existingIndex = allItems.indexOfFirst { it.streamId == pendingItem.streamId && it.type == pendingItem.type }
                if (existingIndex >= 0) {
                    allItems[existingIndex] = pendingItem
                } else {
                    allItems.add(pendingItem)
                }
            }

            // Deduplicate: If multiple items belong to the same series, only keep the latest one
            val groupedBySeriesOrMovie = mutableMapOf<String, PlaybackHistory>()
            allItems.sortedByDescending { it.lastPlayedAt }.forEach { item ->
                // Filter out invalid/incomplete records from previous bugs
                if (item.providerId == 0L) return@forEach
                if (item.type == "series" && (item.seriesId == null || item.seriesId == 0L)) return@forEach

                val groupKey = if (item.seriesId != null && item.seriesId != 0L) {
                    "series_${item.seriesId}"
                } else {
                    "${item.type}_${item.streamId}"
                }
                
                if (!groupedBySeriesOrMovie.containsKey(groupKey)) {
                    groupedBySeriesOrMovie[groupKey] = item
                }
            }

            groupedBySeriesOrMovie.values
                .sortedByDescending { it.lastPlayedAt }
                .take(limit)
        }
    }

    override suspend fun getPlaybackHistory(streamId: Long, type: String, providerId: Long): PlaybackHistory? {
        val key = generateKey(streamId, type, providerId)
        synchronized(pendingUpdates) {
            pendingUpdates[key]?.let { return it }
        }
        
        return runCatching {
            historyBox.query()
                .equal(PlaybackHistory_.streamId, streamId)
                .build().use { q ->
                    q.find().firstOrNull { it.type == type && it.providerId == providerId }
                }
        }.getOrNull()
    }

    override suspend fun recordPlayback(history: PlaybackHistory) = withContext(Dispatchers.IO) {
        val key = generateKey(history.streamId, history.type, history.providerId)
        val existing = getPlaybackHistory(history.streamId, history.type, history.providerId)
        
        if (existing != null) {
            history.id = existing.id
            // Preserve metadata if missing in new record
            if (history.posterUrl == null) history.posterUrl = existing.posterUrl
            if (history.backdropUrl == null) history.backdropUrl = existing.backdropUrl
            if (history.lastPositionMs == 0L) history.lastPositionMs = existing.lastPositionMs
            if (history.durationMs == 0L) history.durationMs = existing.durationMs
        }
        
        historyBox.put(history)
        syncProgressToContent(history)
        synchronized(pendingUpdates) {
            pendingUpdates.remove(key)
            _pendingUpdatesFlow.value = pendingUpdates.toMap()
        }
    }

    override suspend fun updateResumePosition(
        streamId: Long,
        type: String,
        providerId: Long,
        positionMs: Long,
        durationMs: Long
    ) {
        val key = generateKey(streamId, type, providerId)
        val existing = getPlaybackHistory(streamId, type, providerId)
        
        val item = if (existing != null) {
            existing.copy(lastPositionMs = positionMs, durationMs = durationMs, lastPlayedAt = System.currentTimeMillis())
        } else {
            PlaybackHistory(
                streamId = streamId,
                type = type,
                providerId = providerId,
                lastPositionMs = positionMs,
                durationMs = durationMs,
                lastPlayedAt = System.currentTimeMillis()
            )
        }
        
        syncProgressToContent(item)
        synchronized(pendingUpdates) {
            pendingUpdates[key] = item
            _pendingUpdatesFlow.value = pendingUpdates.toMap()
        }
    }

    private fun syncProgressToContent(history: PlaybackHistory) {
        when (history.type) {
            "movie" -> {
                movieBox.get(history.streamId)?.let { movie ->
                    movie.watchProgress = history.lastPositionMs
                    movie.totalDuration = history.durationMs
                    movie.lastWatchedAt = history.lastPlayedAt
                    movieBox.put(movie)
                }
            }
            "series", "series_episode" -> {
                episodeBox.get(history.streamId)?.let { ep ->
                    ep.watchProgress = history.lastPositionMs
                    ep.totalDuration = history.durationMs
                    ep.lastWatchedAt = history.lastPlayedAt
                    episodeBox.put(ep)
                }
            }
        }
    }

    override suspend fun deleteHistory(streamId: Long, type: String, providerId: Long) = withContext(Dispatchers.IO) {
        val key = generateKey(streamId, type, providerId)
        synchronized(pendingUpdates) {
            pendingUpdates.remove(key)
            _pendingUpdatesFlow.value = pendingUpdates.toMap()
        }
        
        val entity = runCatching {
            historyBox.query()
                .equal(PlaybackHistory_.streamId, streamId)
                .build().use { q ->
                    q.find().firstOrNull { it.type == type && it.providerId == providerId }
                }
        }.getOrNull()
            
        if (entity != null) {
            historyBox.remove(entity.id)
        }
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        synchronized(pendingUpdates) {
            pendingUpdates.clear()
            _pendingUpdatesFlow.value = emptyMap()
        }
        historyBox.removeAll()
    }

    private fun flushPendingUpdates() {
        if (pendingUpdates.isEmpty()) return
        
        runCatching {
            val items = synchronized(pendingUpdates) {
                ArrayList(pendingUpdates.values)
            }
            if (items.isEmpty()) return
            
            historyBox.put(items)
            items.forEach { item ->
                syncProgressToContent(item)
                val key = generateKey(item.streamId, item.type, item.providerId)
                synchronized(pendingUpdates) {
                    val current = pendingUpdates[key]
                    if (current != null && current.lastPlayedAt <= item.lastPlayedAt) {
                        pendingUpdates.remove(key)
                    }
                }
            }
            _pendingUpdatesFlow.value = synchronized(pendingUpdates) { pendingUpdates.toMap() }
        }.onFailure { e ->
            Log.e("PlaybackHistoryRepo", "Error flushing pending updates", e)
        }
    }
}
