package com.ghost.tv

import android.app.Activity
import android.app.Application
import android.os.Bundle
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
import com.ghost.tv.data.config.ConfigRepository
import com.ghost.tv.data.remote.proxy.NetworkManager
import com.ghost.tv.data.repository.PreferenceRepository
import com.ghost.tv.data.sync.CatalogSyncWorker
import com.ghost.tv.data.sync.VodSyncWorker
import com.ghost.tv.util.DeviceRenderProfile
import com.ghost.tv.util.RenderTier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class GhostTvApp : Application(), SingletonImageLoader.Factory {
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
                    .maxSizeBytes(32 * 1024 * 1024L) // 32MB limit as requested
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
            Log.i("GhostTvApp", "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e("GhostTvApp", "Firebase initialization failed: ${e.message}")
        }

        val isLite = preferenceRepository.isLiteModeEnabled.value
        try {
            DeviceRenderProfile.initialize(this, liteMode = isLite)
        } catch (e: Exception) {
            Log.e("GhostTvApp", "DeviceRenderProfile initialization failed", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        preferenceRepository.applySavedLocale()

        Log.i("GhostTvApp", "Initializing Network and Config...")
        // Explicitly trigger remote config refresh on app start
        configRepository.refreshRemoteConfig()
        
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                Log.d("GhostTvApp", "Activity Resumed: ${activity.localClassName}, refreshing remote config")
                configRepository.refreshRemoteConfig()
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // Initialize network/proxy immediately so it can react to local state
        networkManager.init()

        // Fetch config & schedule background syncs
        appScope.launch {
            configRepository.fetchConfig()
            CatalogSyncWorker.schedulePeriodicSync(this@GhostTvApp)
            VodSyncWorker.scheduleSilentSync(this@GhostTvApp)
        }
    }
}
