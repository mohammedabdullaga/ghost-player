package com.eliteprime.app.di

import com.eliteprime.app.data.remote.TmdbApiService
import com.eliteprime.app.data.remote.XtreamApi
import com.eliteprime.app.data.remote.util.RateLimitInterceptor
import com.eliteprime.app.domain.model.ProxyConfig
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
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.SocketAddress
import java.io.IOException
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import javax.inject.Singleton
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

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
    fun provideProxySelector(): ProxySelector {
        return object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                val host = uri?.host ?: ""

                // 1. If this is the config endpoint, ALWAYS route through local Shadowsocks SOCKS5 proxy
                if (host.contains("dropremax.site", ignoreCase = true)) {
                    return listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1080)))
                }

                // 2. If general secure connection is disabled, do not proxy other traffic
                if (!ProxyConfig.isEnabled) return listOf(Proxy.NO_PROXY)

                // 3. Bypass local engine and common metadata providers
                if (host == "127.0.0.1" || 
                    host == "localhost" || 
                    host.contains("tmdb.org", ignoreCase = true) || 
                    host.contains("themoviedb.org", ignoreCase = true)) {
                    return listOf(Proxy.NO_PROXY)
                }

                if (ProxyConfig.USE_SHADOWSOCKS) {
                    // Routing through local Shadowsocks SOCKS5 daemon
                    return listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1080)))
                } else {
                    // Routing through Secure Tunnel Engine HTTP proxy
                    return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 8080)))
                }
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        proxySelector: ProxySelector
    ): OkHttpClient {
        val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustAllManager), SecureRandom())
        }

        return OkHttpClient.Builder()
            .cache(
                Cache(
                    directory = File(context.cacheDir, "http_cache"),
                    maxSize = 16L * 1024 * 1024 // Reduced to 16 MB as requested
                )
            )
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .proxySelector(proxySelector)
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
