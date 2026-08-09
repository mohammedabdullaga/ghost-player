package com.eliteprime.app.data.remote

import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Series
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import io.objectbox.Box
import io.objectbox.BoxStore
import java.io.InputStream
import java.io.InputStreamReader

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamStreamParser @Inject constructor(
    private val boxStore: BoxStore,
    private val gson: Gson
) {
    fun parseLiveStreams(inputStream: InputStream, categoryId: String?, onProgress: (Int) -> Unit) {
        val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
        val liveBox: Box<LiveStream> = boxStore.boxFor(LiveStream::class.java)
        var count = 0
        
        boxStore.runInTx {
            reader.beginArray()
            while (reader.hasNext()) {
                val live: LiveStream = gson.fromJson(reader, LiveStream::class.java)
                if (categoryId == null || live.remoteCategoryId == categoryId) {
                    liveBox.put(live)
                }
                count++
                if (count % 100 == 0) onProgress(count)
            }
            reader.endArray()
        }
        onProgress(count)
        reader.close()
    }

    fun parseMovies(inputStream: InputStream, categoryId: String?, onProgress: (Int) -> Unit) {
        val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
        val movieBox: Box<Movie> = boxStore.boxFor(Movie::class.java)
        var count = 0
        
        boxStore.runInTx {
            reader.beginArray()
            while (reader.hasNext()) {
                val movie: Movie = gson.fromJson(reader, Movie::class.java)
                if (categoryId == null || movie.remoteCategoryId == categoryId) {
                    movieBox.put(movie)
                }
                count++
                if (count % 50 == 0) onProgress(count)
            }
            reader.endArray()
        }
        onProgress(count)
        reader.close()
    }

    fun parseSeries(inputStream: InputStream, categoryId: String?, onProgress: (Int) -> Unit) {
        val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
        val seriesBox: Box<Series> = boxStore.boxFor(Series::class.java)
        var count = 0
        
        boxStore.runInTx {
            reader.beginArray()
            while (reader.hasNext()) {
                val series: Series = gson.fromJson(reader, Series::class.java)
                if (categoryId == null || series.remoteCategoryId == categoryId) {
                    seriesBox.put(series)
                }
                count++
                if (count % 20 == 0) onProgress(count)
            }
            reader.endArray()
        }
        onProgress(count)
        reader.close()
    }
}
