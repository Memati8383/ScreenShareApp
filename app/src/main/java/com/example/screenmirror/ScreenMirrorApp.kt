package com.example.screenmirror

import android.app.Application
import com.example.screenmirror.data.RoomHistoryManager
import com.example.screenmirror.data.migration.RoomHistoryMigration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ScreenMirrorApp : Application() {

    @Inject
    lateinit var roomHistoryMigration: RoomHistoryMigration
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Eski SharedPreferences manager - geriye dönük uyumluluk için
    @Deprecated("Use Room Database with Hilt injection instead")
    lateinit var roomHistoryManager: RoomHistoryManager
        private set

    override fun onCreate() {
        super.onCreate()
        AppSettings.applyLanguage(this)
        SecureCredentialStore.migrateFromPlainText(this)
        roomHistoryManager = RoomHistoryManager(this)
        
        // Eski verilerden yeni veritabanına migrasyon
        applicationScope.launch {
            roomHistoryMigration.migrateIfNeeded()
        }
    }
}
