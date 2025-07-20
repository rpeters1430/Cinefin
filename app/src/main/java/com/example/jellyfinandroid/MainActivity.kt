package com.example.jellyfinandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import javax.inject.Inject
import com.example.jellyfinandroid.ui.JellyfinApp
import com.example.jellyfinandroid.utils.ImageLoaderInitializer
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var imageLoaderInitializer: ImageLoaderInitializer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        imageLoaderInitializer.initialize()

        setContent {
            JellyfinApp()
        }
    }
}
