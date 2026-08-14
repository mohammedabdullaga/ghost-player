package com.eliteprime.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.eliteprime.app.data.config.ConfigRepository
import com.eliteprime.app.data.remote.proxy.NetworkManager
import com.eliteprime.app.data.repository.PreferenceRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class ElitePrimeApp : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var preferenceRepository: PreferenceRepository
    @Inject lateinit var networkManager: NetworkManager
    @Inject lateinit var configRepository: ConfigRepository
    @Inject lateinit var okHttpClient: OkHttpClient

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(16 * 1024 * 1024L) // Reduced to 16MB as requested for general data
                    .build()
            }
            .diskCachePolicy(CachePolicy.DISABLED) // DISABLE DISK CACHE BY DEFAULT
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        preferenceRepository.applySavedLocale()
        
        // Ensure config is fetched as early as possible
        appScope.launch {
            configRepository.fetchConfig()
            networkManager.init()
        }
    }
}
