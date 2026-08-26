package com.eliteprime.app.data.remote

import android.util.Log
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamStreamParser @Inject constructor(
    private val boxStore: BoxStore,
    private val gson: Gson
) {
    private val TAG = "XtreamStreamParser"
    private val BATCH_SIZE = 1000

    suspend fun parseLiveStreams(inputStream: InputStream, categoryId: String?, onProgress: (Int) -> Unit) = coroutineScope {
        val trace = FirebasePerformance.getInstance().newTrace("parse_live_streams")
        trace.start()
        val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
        val liveBox: Box<LiveStream> = boxStore.boxFor(LiveStream::class.java)
        var count = 0
        val writeChannel = Channel<List<LiveStream>>(capacity = 10)
        
        val writerJob = launch(Dispatchers.IO) {
            for (batch in writeChannel) {
                liveBox.put(batch)
            }
        }
        
        try {
            val batch = mutableListOf<LiveStream>()
            reader.beginArray()
            while (reader.hasNext()) {
                try {
                    val live: LiveStream = gson.fromJson(reader, LiveStream::class.java)
                    live.order = count
                    if (categoryId == null || live.remoteCategoryId == categoryId) {
                        batch.add(live)
                    }
                    count++
                    
                    if (batch.size >= BATCH_SIZE) {
                        writeChannel.send(batch.toList())
                        batch.clear()
                        onProgress(count)
                    } else if (count % 500 == 0) {
                        onProgress(count)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing live stream at count $count: ${e.message}")
                    FirebaseCrashlytics.getInstance().recordException(e)
                    if (e is IOException || e is JsonSyntaxException) {
                        // Connection reset or bad JSON - stop here but save what we have
                        break
                    }
                }
            }
            if (batch.isNotEmpty()) {
                writeChannel.send(batch)
            }
            reader.endArray()
            trace.putAttribute("count", count.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in parseLiveStreams: ${e.message}")
            FirebaseCrashlytics.getInstance().recordException(e)
        } finally {
            writeChannel.close()
            writerJob.join()
            onProgress(count)
            try { reader.close() } catch (_: Exception) {}
            trace.stop()
        }
    }

    suspend fun parseMovies(inputStream: InputStream, categoryId: String?, onProgress: (Int) -> Unit) = coroutineScope {
        val trace = FirebasePerformance.getInstance().newTrace("parse_movies")
        trace.start()
        val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
        val movieBox: Box<Movie> = boxStore.boxFor(Movie::class.java)
        var count = 0
        val writeChannel = Channel<List<Movie>>(capacity = 10)
        
        val writerJob = launch(Dispatchers.IO) {
            for (batch in writeChannel) {
                movieBox.put(batch)
            }
        }
        
        try {
            val batch = mutableListOf<Movie>()
            reader.beginArray()
            while (reader.hasNext()) {
                try {
                    val movie: Movie = gson.fromJson(reader, Movie::class.java)
                    movie.order = count
                    if (categoryId == null || movie.remoteCategoryId == categoryId) {
                        batch.add(movie)
                    }
                    count++
                    
                    if (batch.size >= BATCH_SIZE) {
                        writeChannel.send(batch.toList())
                        batch.clear()
                        onProgress(count)
                    } else if (count % 500 == 0) {
                        onProgress(count)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing movie at count $count: ${e.message}")
                    FirebaseCrashlytics.getInstance().recordException(e)
                    if (e is IOException || e is JsonSyntaxException) {
                        break
                    }
                }
            }
            if (batch.isNotEmpty()) {
                writeChannel.send(batch)
            }
            reader.endArray()
            trace.putAttribute("count", count.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in parseMovies: ${e.message}")
            FirebaseCrashlytics.getInstance().recordException(e)
        } finally {
            writeChannel.close()
            writerJob.join()
            onProgress(count)
            try { reader.close() } catch (_: Exception) {}
            trace.stop()
        }
    }

    suspend fun parseSeries(inputStream: InputStream, categoryId: String?, onProgress: (Int) -> Unit) = coroutineScope {
        val trace = FirebasePerformance.getInstance().newTrace("parse_series")
        trace.start()
        val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
        val seriesBox: Box<Series> = boxStore.boxFor(Series::class.java)
        var count = 0
        val writeChannel = Channel<List<Series>>(capacity = 10)
        
        val writerJob = launch(Dispatchers.IO) {
            for (batch in writeChannel) {
                seriesBox.put(batch)
            }
        }
        
        try {
            val batch = mutableListOf<Series>()
            reader.beginArray()
            while (reader.hasNext()) {
                try {
                    val series: Series = gson.fromJson(reader, Series::class.java)
                    series.order = count
                    if (categoryId == null || series.remoteCategoryId == categoryId) {
                        batch.add(series)
                    }
                    count++
                    
                    if (batch.size >= BATCH_SIZE) {
                        writeChannel.send(batch.toList())
                        batch.clear()
                        onProgress(count)
                    } else if (count % 500 == 0) {
                        onProgress(count)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing series at count $count: ${e.message}")
                    FirebaseCrashlytics.getInstance().recordException(e)
                    if (e is IOException || e is JsonSyntaxException) {
                        break
                    }
                }
            }
            if (batch.isNotEmpty()) {
                writeChannel.send(batch)
            }
            reader.endArray()
            trace.putAttribute("count", count.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in parseSeries: ${e.message}")
            FirebaseCrashlytics.getInstance().recordException(e)
        } finally {
            writeChannel.close()
            writerJob.join()
            onProgress(count)
            try { reader.close() } catch (_: Exception) {}
            trace.stop()
        }
    }
}
