package com.cloudinaryfiles.app

import android.app.Application

/**
 * Custom Application class — registered in AndroidManifest.xml.
 * Initializes AppLogger before any Activity or Service starts, so every
 * log call from the very first line of MainActivity is captured to disk.
 */
class CloudVaultApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Init logger first — before anything else
        AppLogger.init(this)
        AppLogger.i("CloudVaultApp", "Application.onCreate() — process started")
    }

    override fun onTerminate() {
        AppLogger.i("CloudVaultApp", "Application.onTerminate()")
        super.onTerminate()
    }

    override fun onLowMemory() {
        AppLogger.w("CloudVaultApp", "onLowMemory() — system is low on memory!")
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        AppLogger.w("CloudVaultApp", "onTrimMemory(level=$level)")
        super.onTrimMemory(level)
    }
}
