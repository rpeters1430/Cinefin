package com.rpeters.jellyfin.di

import com.rpeters.jellyfin.ui.player.audio.AudioServiceForegroundIntentProvider
import com.rpeters.jellyfin.ui.player.audio.DefaultAudioServiceForegroundIntentProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindAudioServiceForegroundIntentProvider(
        impl: DefaultAudioServiceForegroundIntentProvider,
    ): AudioServiceForegroundIntentProvider
}

