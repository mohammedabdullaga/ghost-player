package com.eliteprime.app.data.manager

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.repository.PreferenceRepository
import com.eliteprime.app.data.remote.XtreamUrlFactory
import com.eliteprime.app.domain.model.*
import com.eliteprime.app.domain.repository.DownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val boxStore: BoxStore,
    private val preferenceRepository: PreferenceRepository,
    private val okHttpClient: OkHttpClient,
    private val applicationScope: CoroutineScope
) : DownloadManager {

    private val downloadBox = boxStore.boxFor(DownloadEntity::class.java)
    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)
    
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val playbackPausedIds = ConcurrentHashMap.newKeySet<String>()
    private val schedulerMutex = Mutex()
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    @Volatile private var playbackActive = false

    private val MAX_RETRIES = 5
    private val DEFAULT_BUFFER_SIZE = 64 * 1024
    private val PROGRESS_UPDATE_BYTES = 512 * 1024

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                applicationScope.launch(Dispatchers.IO) {
                    startNextQueued()
                }
            }
        })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAllDownloads(): Flow<List<DownloadItem>> {
        return downloadBox.query().build().flow().map { downloads -> downloads.map { it.toDomain() } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDownload(id: String): Flow<DownloadItem?> {
        return downloadBox.query().build().flow().map { list ->
            list.firstOrNull { it.downloadId == id }?.toDomain()
        }
    }

    override fun observeStorageState(): Flow<DownloadStorageConfig> {
        return preferenceRepository.downloadTreeUri.map { treeUri ->
            DownloadStorageConfig(
                treeUri = treeUri,
                displayName = treeUri?.substringAfterLast('/')?.ifBlank { treeUri },
                outputDirectory = null,
                availableBytes = null,
                isWritable = !treeUri.isNullOrBlank()
            )
        }
    }

    override suspend fun enqueueDownload(request: DownloadRequest): Result<DownloadItem> = withContext(Dispatchers.IO) {
        try {
            val id = "${request.contentType.name}_${request.contentId}_${System.currentTimeMillis()}"
            val item = DownloadItem(
                id = id,
                providerId = request.providerId,
                contentType = request.contentType,
                contentId = request.contentId,
                contentName = request.contentName,
                streamUrl = "", 
                sourceStreamUrl = request.sourceStreamUrl,
                sourceStreamId = request.sourceStreamId,
                containerExtension = request.containerExtension,
                posterUrl = request.posterUrl,
                seriesId = request.seriesId,
                seasonNumber = request.seasonNumber,
                episodeNumber = request.episodeNumber,
                status = DownloadStatus.PENDING
            )
            downloadBox.put(DownloadEntity.fromDomain(item))
            startNextQueued()
            Result.success(item)
        } catch (e: Exception) {
            Result.error("Failed to enqueue download", e)
        }
    }

    override suspend fun resumeDownload(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val all = downloadBox.all
            val entity = all.firstOrNull { it.downloadId == id }
            if (entity != null) {
                if (entity.status != DownloadStatus.COMPLETED.name && entity.status != DownloadStatus.CANCELLED.name) {
                    deleteOutput(entity)
                    entity.outputUri = null
                    entity.outputDisplayPath = null
                    entity.status = DownloadStatus.PENDING.name
                    entity.bytesWritten = 0
                    entity.totalBytes = null
                    entity.supportsResume = false
                    entity.retryCount = 0
                    entity.failureReason = null
                    downloadBox.put(entity)
                }
            }
            startNextQueued()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error("Failed to resume download", e)
        }
    }

    override suspend fun cancelDownload(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            activeCalls.remove(id)?.cancel()
            activeJobs.remove(id)?.cancelAndJoin()
            val all = downloadBox.all
            val entity = all.firstOrNull { it.downloadId == id }
            if (entity != null) {
                entity.status = DownloadStatus.CANCELLED.name
                downloadBox.put(entity)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error("Failed to cancel download", e)
        }
    }

    override fun onPlaybackStarted() {
        playbackActive = true
        applicationScope.launch(Dispatchers.IO) {
            activeJobs.forEach { (id, job) ->
                playbackPausedIds.add(id)
                activeCalls.remove(id)?.cancel()
                job.cancel()
            }
        }
    }

    override fun onPlaybackStopped() {
        playbackActive = false
        applicationScope.launch(Dispatchers.IO) {
            startNextQueued()
        }
    }

    override suspend fun deleteDownload(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            activeCalls.remove(id)?.cancel()
            activeJobs.remove(id)?.cancelAndJoin()
            val all = downloadBox.all
            val entity = all.firstOrNull { it.downloadId == id }
            if (entity != null) {
                deleteOutput(entity)
                downloadBox.remove(entity.objectBoxId)
            }
            startNextQueued()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error("Failed to delete download", e)
        }
    }

    override suspend fun updateStorageConfig(treeUri: String?, displayName: String?): Result<DownloadStorageConfig> = withContext(Dispatchers.IO) {
        try {
            preferenceRepository.setDownloadTreeUri(treeUri)
            Result.success(DownloadStorageConfig(
                treeUri = treeUri,
                displayName = displayName,
                isWritable = !treeUri.isNullOrBlank()
            ))
        } catch (e: Exception) {
            Result.error("Failed to update storage config", e)
        }
    }

    private suspend fun startNextQueued() {
        schedulerMutex.withLock {
            if (playbackActive || activeJobs.isNotEmpty()) return
            
            val all = downloadBox.all
            val next = all.firstOrNull { 
                (it.status == DownloadStatus.PENDING.name || (it.status == DownloadStatus.FAILED.name && it.retryCount < MAX_RETRIES)) 
                && !activeJobs.containsKey(it.downloadId) 
            } ?: return
            
            startDownloadJob(next)
        }
    }

    private fun startDownloadJob(entity: DownloadEntity) {
        activeJobs[entity.downloadId] = applicationScope.launch(Dispatchers.IO) {
            captureDownload(entity)
        }
    }

    private suspend fun captureDownload(initial: DownloadEntity) {
        var current = initial
        current.status = DownloadStatus.DOWNLOADING.name
        current.failureReason = null
        downloadBox.put(current)

        try {
            val account = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst() 
                ?: throw IllegalStateException("No active account")
            
            val requestUrl = XtreamUrlFactory.buildPlaybackUrl(
                serverUrl = account.serverUrl,
                username = account.username,
                password = account.password,
                kind = if (current.contentType == DownloadContentType.MOVIE.name) "movie" else "series",
                streamId = current.contentId,
                extension = current.containerExtension
            )

            val request = Request.Builder().url(requestUrl).get().build()
            val call = okHttpClient.newCall(request)
            activeCalls[current.downloadId] = call
            
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty body")
                
                val contentLength = body.contentLength()
                current.totalBytes = if (contentLength > 0) contentLength else null
                
                val target = createOutputTarget(current, response.header("Content-Type"))
                current.outputUri = target.uri.toString()
                current.outputDisplayPath = target.displayPath
                downloadBox.put(current)

                target.output.use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytesWrittenTotal = 0L
                        var lastUpdate = 0L
                        
                        while (true) {
                            yield()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesWrittenTotal += read
                            
                            if (bytesWrittenTotal - lastUpdate >= PROGRESS_UPDATE_BYTES) {
                                current.bytesWritten = bytesWrittenTotal
                                downloadBox.put(current)
                                lastUpdate = bytesWrittenTotal
                            }
                        }
                        current.bytesWritten = bytesWrittenTotal
                    }
                }
                current.status = DownloadStatus.COMPLETED.name
                current.completedAt = System.currentTimeMillis()
                downloadBox.put(current)
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                // Ignore
            } else {
                current.status = DownloadStatus.FAILED.name
                current.failureReason = e.message
                current.retryCount++
                downloadBox.put(current)
            }
        } finally {
            activeCalls.remove(current.downloadId)
            activeJobs.remove(current.downloadId)
            applicationScope.launch { startNextQueued() }
        }
    }

    private fun createOutputTarget(entity: DownloadEntity, contentType: String?): OutputTarget {
        val fileName = buildFileName(entity, contentType ?: "video/mp4")
        val treeUri = preferenceRepository.downloadTreeUri.value

        if (!treeUri.isNullOrBlank()) {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            val file = tree?.createFile(contentType ?: "video/mp4", fileName)
                ?: throw IOException("Failed to create file in SAF")
            val output = context.contentResolver.openOutputStream(file.uri)
                ?: throw IOException("Failed to open SAF output stream")
            return OutputTarget(file.uri, file.name ?: fileName, output)
        }

        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ArenaDownloads").apply { mkdirs() }
        val file = File(dir, fileName)
        val uri = Uri.fromFile(file)
        return OutputTarget(uri, file.absolutePath, FileOutputStream(file))
    }

    private fun buildFileName(entity: DownloadEntity, mimeType: String): String {
        val extension = entity.containerExtension ?: "mp4"
        val name = entity.contentName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return "$name.$extension"
    }

    private fun deleteOutput(entity: DownloadEntity) {
        entity.outputUri?.let {
            try {
                val uri = Uri.parse(it)
                DocumentFile.fromSingleUri(context, uri)?.delete()
            } catch (e: Exception) {}
        }
        entity.outputDisplayPath?.let {
            try {
                File(it).delete()
            } catch (e: Exception) {}
        }
    }

    private data class OutputTarget(val uri: Uri, val displayPath: String, val output: OutputStream)
}
