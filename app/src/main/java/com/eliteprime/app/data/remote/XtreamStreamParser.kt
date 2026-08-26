package com.eliteprime.app.data.remote

import android.util.Log
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.google.gson.Gson
import com.google.gson.JsonParser
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
import java.io.BufferedInputStream
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
    private val BATCH_SIZE = 2000

    suspend fun parseLiveStreams(inputStream: InputStream, categoryId: String?, onProgress: (Int) -> Unit) = coroutineScope {
        val trace = FirebasePerformance.getInstance().newTrace("parse_live_streams")
        trace.start()
        val reader = JsonReader(InputStreamReader(BufferedInputStream(inputStream, 1024 * 128), "UTF-8"))
        reader.setLenient(true)
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
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray()
            }
            while (reader.hasNext()) {
                val element = try {
                    JsonParser.parseReader(reader)
                } catch (e: Exception) {
                    Log.e(TAG, "Syntax error parsing live stream at count $count: ${e.message}")
                    break
                }

                try {
                    val live: LiveStream? = gson.fromJson(element, LiveStream::class.java)
                    if (live != null) {
                        live.order = count
                        if (categoryId == null || live.remoteCategoryId == categoryId) {
                            batch.add(live)
                        }
                        count++
                    }
                    
                    if (batch.size >= BATCH_SIZE) {
                        writeChannel.send(batch.toList())
                        batch.clear()
                        onProgress(count)
                    } else if (count % 500 == 0) {
                        onProgress(count)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Mapping error for live stream at count $count: ${e.message}")
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
            if (batch.isNotEmpty()) {
                writeChannel.send(batch)
            }
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
        val reader = JsonReader(InputStreamReader(BufferedInputStream(inputStream, 1024 * 128), "UTF-8"))
        reader.setLenient(true)
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
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray()
            }
            while (reader.hasNext()) {
                val element = try {
                    JsonParser.parseReader(reader)
                } catch (e: Exception) {
                    Log.e(TAG, "Syntax error parsing movie at count $count: ${e.message}")
                    break
                }

                try {
                    val movie: Movie? = gson.fromJson(element, Movie::class.java)
                    if (movie != null) {
                        movie.order = count
                        if (categoryId == null || movie.remoteCategoryId == categoryId) {
                            batch.add(movie)
                        }
                        count++
                    }
                    
                    if (batch.size >= BATCH_SIZE) {
                        writeChannel.send(batch.toList())
                        batch.clear()
                        onProgress(count)
                    } else if (count % 500 == 0) {
                        onProgress(count)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Mapping error for movie at count $count: ${e.message}")
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
            if (batch.isNotEmpty()) {
                writeChannel.send(batch)
            }
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
        val reader = JsonReader(InputStreamReader(BufferedInputStream(inputStream, 1024 * 128), "UTF-8"))
        reader.setLenient(true)
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
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray()
            }
            while (reader.hasNext()) {
                val element = try {
                    JsonParser.parseReader(reader)
                } catch (e: Exception) {
                    Log.e(TAG, "Syntax error parsing series at count $count: ${e.message}")
                    break
                }

                try {
                    val series: Series? = gson.fromJson(element, Series::class.java)
                    if (series != null) {
                        series.order = count
                        if (categoryId == null || series.remoteCategoryId == categoryId) {
                            batch.add(series)
                        }
                        count++
                    }
                    
                    if (batch.size >= BATCH_SIZE) {
                        writeChannel.send(batch.toList())
                        batch.clear()
                        onProgress(count)
                    } else if (count % 500 == 0) {
                        onProgress(count)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Mapping error for series at count $count: ${e.message}")
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
            if (batch.isNotEmpty()) {
                writeChannel.send(batch)
            }
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
