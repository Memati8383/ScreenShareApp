package com.example.screenmirror

import android.app.Application
import com.example.screenmirror.data.RoomHistoryManager

class ScreenMirrorApp : Application() {

    lateinit var roomHistoryManager: RoomHistoryManager
        private set

    override fun onCreate() {
        super.onCreate()
        AppSettings.applyLanguage(this)
        SecureCredentialStore.migrateFromPlainText(this)
        roomHistoryManager = RoomHistoryManager(this)
    }
}
