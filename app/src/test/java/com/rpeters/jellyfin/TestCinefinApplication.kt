package com.rpeters.jellyfin

import android.app.Application

class TestCinefinApplication : Application() {
    override fun onCreate() {
        // Do nothing, specifically don't initialize Firebase
    }
}
