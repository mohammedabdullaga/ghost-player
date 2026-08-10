package com.eliteprime.app.di

import com.eliteprime.app.data.manager.DownloadManagerImpl
import com.eliteprime.app.data.repository.PlaybackHistoryRepositoryImpl
import com.eliteprime.app.domain.repository.DownloadManager
import com.eliteprime.app.domain.repository.PlaybackHistoryRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDownloadManager(impl: DownloadManagerImpl): DownloadManager

    @Binds
    @Singleton
    abstract fun bindPlaybackHistoryRepository(impl: PlaybackHistoryRepositoryImpl): PlaybackHistoryRepository

    companion object {
        @Provides
        @Singleton
        fun provideApplicationScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
    }
}
