package com.eliteprime.app.di

import com.eliteprime.app.data.remote.TmdbApiService
import com.eliteprime.app.data.remote.XtreamApi
import com.eliteprime.app.data.remote.util.RateLimitInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import kotlinx.serialization.json.Json
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(Double::class.java, object : TypeAdapter<Double>() {
                override fun write(out: JsonWriter, value: Double?) { out.value(value) }
                override fun read(`in`: JsonReader): Double? {
                    if (`in`.peek() == JsonToken.NULL) { `in`.nextNull(); return 0.0 }
                    val s = `in`.nextString()
                    return if (s.isEmpty()) 0.0 else s.toDoubleOrNull() ?: 0.0
                }
            }.nullSafe())
            .registerTypeAdapter(Int::class.java, object : TypeAdapter<Int>() {
                override fun write(out: JsonWriter, value: Int?) { out.value(value) }
                override fun read(`in`: JsonReader): Int? {
                    if (`in`.peek() == JsonToken.NULL) { `in`.nextNull(); return 0 }
                    val s = `in`.nextString()
                    return if (s.isEmpty()) 0 else s.toIntOrNull() ?: 0
                }
            }.nullSafe())
            .registerTypeAdapter(Long::class.java, object : TypeAdapter<Long>() {
                override fun write(out: JsonWriter, value: Long?) { out.value(value) }
                override fun read(`in`: JsonReader): Long? {
                    if (`in`.peek() == JsonToken.NULL) { `in`.nextNull(); return 0L }
                    val s = `in`.nextString()
                    return if (s.isEmpty()) 0L else s.toLongOrNull() ?: 0L
                }
            }.nullSafe())
            .create()
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .addInterceptor(RateLimitInterceptor(30))
            .build()
    }

    @Provides
    @Singleton
    fun provideTmdbApi(client: OkHttpClient, gson: Gson): TmdbApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(TmdbApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideXtreamApi(client: OkHttpClient, gson: Gson): XtreamApi {
        return Retrofit.Builder()
            .baseUrl("http://localhost/") // Placeholder, using @Url
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(XtreamApi::class.java)
    }
}
