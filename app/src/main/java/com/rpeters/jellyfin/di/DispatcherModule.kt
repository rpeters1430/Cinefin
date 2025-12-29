package com.rpeters.jellyfin.di

import com.rpeters.jellyfin.data.common.DefaultDispatcherProvider
import com.rpeters.jellyfin.data.common.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing DispatcherProvider for dependency injection.
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
}
