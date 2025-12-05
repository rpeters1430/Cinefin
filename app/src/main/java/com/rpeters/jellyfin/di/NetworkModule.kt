package com.rpeters.jellyfin.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.DeviceCapabilities
import com.rpeters.jellyfin.data.cache.JellyfinCache
import com.rpeters.jellyfin.data.playback.EnhancedPlaybackManager
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import com.rpeters.jellyfin.network.CachePolicyInterceptor
import com.rpeters.jellyfin.network.ConnectivityChecker
import com.rpeters.jellyfin.network.DeviceIdentityProvider
import com.rpeters.jellyfin.network.JellyfinAuthInterceptor
import com.rpeters.jellyfin.utils.withStrictModeTagger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import okio.Path.Companion.toOkioPath
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        connectivityChecker: ConnectivityChecker,
        authInterceptor: JellyfinAuthInterceptor,
    ): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = okhttp3.Cache(cacheDir, 150L * 1024 * 1024) // 150 MB

        val builder = OkHttpClient.Builder()
            .withStrictModeTagger()
            .cache(cache)
            .addInterceptor(authInterceptor)
            .authenticator(authInterceptor)
            .addInterceptor(CachePolicyInterceptor(connectivityChecker))

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
        }

        return builder
            .connectionPool(okhttp3.ConnectionPool(5, 10, TimeUnit.MINUTES))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }

    @Provides
    @Singleton
    fun provideJellyfinAuthInterceptor(
        authRepositoryProvider: Provider<JellyfinAuthRepository>,
        deviceIdentityProvider: DeviceIdentityProvider,
    ): JellyfinAuthInterceptor {
        return JellyfinAuthInterceptor(authRepositoryProvider, deviceIdentityProvider)
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(120L * 1024 * 1024)
                    .build()
            }
            .components {
                add(
                    coil3.network.okhttp.OkHttpNetworkFetcherFactory(
                        callFactory = {
                            okHttpClient.newBuilder()
                                .addInterceptor { chain ->
                                    val request = chain.request().newBuilder()
                                        .header("Accept", "image/webp,image/avif,image/*,*/*;q=0.8")
                                        .build()
                                    chain.proceed(request)
                                }
                                .build()
                        },
                    ),
                )
            }
            // Coil 3.x: crossfade, allowRgb565, allowHardware are now request-level options
            // Set as defaults in requests if needed
            .build()
    }

    @Provides
    @Singleton
    fun provideJellyfinSdk(@ApplicationContext context: Context): Jellyfin {
        return createJellyfin {
            clientInfo = ClientInfo(
                name = "Jellyfin Android",
                version = "1.0.0",
            )
            deviceInfo = DeviceInfo(
                id = "android-jellyfin-client",
                name = "Android Device",
            )
            this.context = context
        }
    }

    @Provides
    @Singleton
    fun provideOptimizedClientFactory(
        @ApplicationContext context: Context,
        jellyfin: Jellyfin,
        authRepositoryProvider: Provider<JellyfinAuthRepository>,
    ): OptimizedClientFactory {
        return OptimizedClientFactory(context, jellyfin, authRepositoryProvider)
    }

    @Provides
    @Singleton
    fun provideJellyfinCache(@ApplicationContext context: Context): JellyfinCache {
        return JellyfinCache(context)
    }

    @Provides
    @Singleton
    fun provideDeviceCapabilities(): DeviceCapabilities {
        return DeviceCapabilities()
    }

    @Provides
    @Singleton
    fun provideEnhancedPlaybackManager(
        @ApplicationContext context: Context,
        repository: JellyfinRepository,
        streamRepository: JellyfinStreamRepository,
        deviceCapabilities: DeviceCapabilities,
    ): EnhancedPlaybackManager {
        return EnhancedPlaybackManager(context, repository, streamRepository, deviceCapabilities)
    }

    @Provides
    @Singleton
    fun provideTimeProvider(): () -> Long {
        return System::currentTimeMillis
    }
}
