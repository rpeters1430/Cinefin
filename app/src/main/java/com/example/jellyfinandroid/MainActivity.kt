package com.example.jellyfinandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.jellyfinandroid.ui.JellyfinApp
import com.example.jellyfinandroid.utils.ImageLoaderInitializer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ImageLoaderInitializerEntryPoint {
    fun imageLoaderInitializer(): ImageLoaderInitializer
}

@androidx.media3.common.util.UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val imageLoaderInitializer: ImageLoaderInitializer by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            ImageLoaderInitializerEntryPoint::class.java
        ).imageLoaderInitializer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        imageLoaderInitializer.initialize()

        setContent {
            JellyfinApp()
        }
    }
}
