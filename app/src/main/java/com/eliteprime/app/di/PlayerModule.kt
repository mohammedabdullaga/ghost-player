package com.eliteprime.app.di

import com.eliteprime.app.player.MasterPlayerEngine
import com.eliteprime.app.player.PlayerEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    @Singleton
    abstract fun bindPlayerEngine(engine: MasterPlayerEngine): PlayerEngine
}
