package com.rpeters.jellyfin.di

import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.LibraryHealthChecker
import com.rpeters.jellyfin.data.repository.common.LibraryLoadingManager
import com.rpeters.jellyfin.ui.utils.PerformanceMonitor
import com.rpeters.jellyfin.ui.viewmodel.common.SharedAppStateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 4: Performance & Scalability Enhancements - Dependency Injection Module
 *
 * Provides all Phase 4 components for dependency injection including:
 * - Shared state management
 * - Performance monitoring
 * - Optimized media loading
 * - Background sync system
 */
@Module
@InstallIn(SingletonComponent::class)
object Phase4Module {

    @Provides
    @Singleton
    fun provideSharedAppStateManager(): SharedAppStateManager {
        return SharedAppStateManager()
    }

    @Provides
    @Singleton
    fun providePerformanceMonitor(): PerformanceMonitor {
        return PerformanceMonitor()
    }

    @Provides
    @Singleton
    fun provideLibraryHealthChecker(): LibraryHealthChecker {
        return LibraryHealthChecker()
    }

    @Provides
    @Singleton
    fun provideLibraryLoadingManager(
        mediaRepository: JellyfinMediaRepository,
    ): LibraryLoadingManager {
        return LibraryLoadingManager(mediaRepository)
    }
}
