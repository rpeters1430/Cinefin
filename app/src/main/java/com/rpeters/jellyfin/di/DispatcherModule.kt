package com.rpeters.jellyfin.di

import com.rpeters.jellyfin.data.common.DefaultDispatcherProvider
import com.rpeters.jellyfin.data.common.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Hilt module providing DispatcherProvider and CoroutineScope for dependency injection.
 * This allows ViewModels and repositories to use dispatchers that can be
 * replaced with test dispatchers during testing.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DispatcherModule {

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(
        impl: DefaultDispatcherProvider,
    ): DispatcherProvider

    companion object {
        @Provides
        @Singleton
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        /**
         * Provides an application-scoped CoroutineScope.
         * Uses SupervisorJob so failures in one coroutine don't cancel others.
         * This scope is tied to the application lifecycle.
         */
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
    }
}
