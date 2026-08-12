package com.example.screenmirror.data.migration

import android.content.Context
import com.example.screenmirror.data.RoomHistory
import com.example.screenmirror.data.RoomHistoryManager
import com.example.screenmirror.data.local.RoomHistoryEntity
import com.example.screenmirror.data.repository.RoomHistoryRepository
import com.example.screenmirror.model.RoomRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHistoryMigration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RoomHistoryRepository
) {
    
    private val prefs = context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
    private val MIGRATION_KEY = "room_history_migrated"
    
    suspend fun migrateIfNeeded() = withContext(Dispatchers.IO) {
        if (isMigrationCompleted()) {
            return@withContext
        }
        
        try {
            val oldManager = RoomHistoryManager(context)
            val oldRooms = oldManager.getAll()
            
            if (oldRooms.isEmpty()) {
                markMigrationCompleted()
                return@withContext
            }
            
            // Eski verileri yeni formata dönüştür
            oldRooms.forEach { oldRoom ->
                val newRoom = convertToEntity(oldRoom)
                repository.insert(newRoom)
            }
            
            markMigrationCompleted()
        } catch (e: Exception) {
            // Sessizce başarısız ol, uygulama çalışmaya devam etsin
            e.printStackTrace()
        }
    }
    
    private fun convertToEntity(oldRoom: RoomHistory): RoomHistoryEntity {
        return RoomHistoryEntity(
            id = 0, // Auto-generate
            roomName = oldRoom.roomName,
            role = when (oldRoom.role.lowercase()) {
                "sender" -> RoomRole.SENDER
                "viewer" -> RoomRole.VIEWER
                else -> RoomRole.VIEWER
            },
            duration = oldRoom.duration,
            participantCount = oldRoom.participantCount,
            startTime = oldRoom.startTime,
            endTime = oldRoom.endTime,
            thumbnailPath = null,
            connectionQuality = null,
            avgBitrate = null,
            totalDataTransferred = null,
            disconnectReason = null,
            isFavorite = false,
            notes = null,
            tags = null
        )
    }
    
    private fun isMigrationCompleted(): Boolean {
        return prefs.getBoolean(MIGRATION_KEY, false)
    }
    
    private fun markMigrationCompleted() {
        prefs.edit().putBoolean(MIGRATION_KEY, true).apply()
    }
}
