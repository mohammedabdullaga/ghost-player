package com.eliteprime.app.di

import android.content.Context
import android.util.Log
import com.eliteprime.app.data.local.entity.MyObjectBox
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.objectbox.BoxStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBoxStore(@ApplicationContext context: Context): BoxStore {
        return try {
            MyObjectBox.builder()
                .androidContext(context)
                .maxSizeInKByte(4 * 1024 * 1024) // 4GB max database size for massive catalogs
                .build()
        } catch (e: Exception) {
            Log.e("DatabaseModule", "ObjectBox initialization failed due to DB corruption, recovering...", e)
            try {
                BoxStore.deleteAllFiles(context, null)
            } catch (cleanupEx: Exception) {
                Log.e("DatabaseModule", "Error deleting corrupt ObjectBox files", cleanupEx)
            }
            MyObjectBox.builder()
                .androidContext(context)
                .maxSizeInKByte(4 * 1024 * 1024)
                .build()
        }
    }
}
