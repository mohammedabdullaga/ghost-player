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
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
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
        val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustAllManager), SecureRandom())
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .connectTimeout(NetworkTimeoutConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkTimeoutConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkTimeoutConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
            .dispatcher(Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 20
            })
            .addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                if (request.header("User-Agent").isNullOrBlank()) {
                    builder.header("User-Agent", "ELITPRIME/1.0 (Android; Media3; OkHttp)")
                }
                chain.proceed(builder.build())
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .addInterceptor(RateLimitInterceptor(60))
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

object NetworkTimeoutConfig {
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L
}
