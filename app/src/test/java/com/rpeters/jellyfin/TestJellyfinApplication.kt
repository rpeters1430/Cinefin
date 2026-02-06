package com.rpeters.jellyfin

import android.app.Application

class TestJellyfinApplication : Application() {
    override fun onCreate() {
        // Do nothing, specifically don't initialize Firebase
    }
}
