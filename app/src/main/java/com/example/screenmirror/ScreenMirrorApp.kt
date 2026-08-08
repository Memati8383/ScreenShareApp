package com.example.screenmirror

import android.app.Application

class ScreenMirrorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.applyLanguage(this)
    }
}
