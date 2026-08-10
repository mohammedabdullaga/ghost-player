package com.eliteprime.app.data.repository

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
            while (true) {
                delay(30000)
                flushPendingUpdates()
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
            val mergedByKey = mutableMapOf<String, PlaybackHistory>()
            dbList.forEach { 
                mergedByKey[generateKey(it.streamId, it.type, it.providerId)] = it 
            }
            pending.values.forEach { pendingItem ->
                mergedByKey[generateKey(pendingItem.streamId, pendingItem.type, pendingItem.providerId)] = pendingItem
            }
            mergedByKey.values.sortedByDescending { it.lastPlayedAt }.take(limit)
        }
    }

    override suspend fun getPlaybackHistory(streamId: Long, type: String, providerId: Long): PlaybackHistory? {
        val key = generateKey(streamId, type, providerId)
        pendingUpdates[key]?.let { return it }
        
        return historyBox.query().build().find().firstOrNull { 
            it.streamId == streamId && it.type == type && it.providerId == providerId 
        }
    }

    override suspend fun recordPlayback(history: PlaybackHistory) = withContext(Dispatchers.IO) {
        val key = generateKey(history.streamId, history.type, history.providerId)
        val existing = getPlaybackHistory(history.streamId, history.type, history.providerId)
        
        if (existing != null) {
            history.id = existing.id
        }
        
        historyBox.put(history)
        syncProgressToContent(history)
        pendingUpdates.remove(key)
        _pendingUpdatesFlow.value = pendingUpdates.toMap()
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
        pendingUpdates[key] = item
        _pendingUpdatesFlow.value = pendingUpdates.toMap()
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
        pendingUpdates.remove(key)
        _pendingUpdatesFlow.value = pendingUpdates.toMap()
        
        val entity = historyBox.query().build().find().firstOrNull { 
            it.streamId == streamId && it.type == type && it.providerId == providerId 
        }
            
        if (entity != null) {
            historyBox.remove(entity.id)
        }
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        pendingUpdates.clear()
        _pendingUpdatesFlow.value = emptyMap()
        historyBox.removeAll()
    }

    private fun flushPendingUpdates() {
        if (pendingUpdates.isEmpty()) return
        
        val items = pendingUpdates.values.toList()
        historyBox.put(items)
        items.forEach { 
            syncProgressToContent(it)
            val key = generateKey(it.streamId, it.type, it.providerId)
            pendingUpdates.remove(key) 
        }
        _pendingUpdatesFlow.value = pendingUpdates.toMap()
    }
}
