package com.eliteprime.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
import com.eliteprime.app.util.DeviceRenderProfile
import com.eliteprime.app.util.RenderTier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
        val tier = DeviceRenderProfile.getTier()
        val memPercent = if (tier == RenderTier.REDUCED) 0.06 else 0.12

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, memPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024L) // 100MB limit
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(tier != RenderTier.REDUCED)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase explicitly
        try {
            FirebaseApp.initializeApp(this)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
            Log.i("ElitePrimeApp", "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e("ElitePrimeApp", "Firebase initialization failed: ${e.message}")
        }

        val isLite = preferenceRepository.isLiteModeEnabled.value
        DeviceRenderProfile.initialize(this, liteMode = isLite)
        preferenceRepository.applySavedLocale()
        
        // Fetch config directly, then initialize network/proxy
        appScope.launch {
            configRepository.fetchConfig()
            networkManager.init()
        }
    }
}
