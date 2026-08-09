package com.eliteprime.app.di

import androidx.media3.common.util.UnstableApi
import com.eliteprime.app.player.Media3PlayerEngine
import com.eliteprime.app.player.PlayerEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @OptIn(UnstableApi::class)
    @Binds
    @Singleton
    abstract fun bindPlayerEngine(engine: Media3PlayerEngine): PlayerEngine
}
