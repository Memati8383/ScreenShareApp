package com.example.screenmirror.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.screenmirror.model.ConnectionQuality
import com.example.screenmirror.model.RoomRole
import java.io.Serializable

@Entity(tableName = "room_history")
@TypeConverters(Converters::class)
data class RoomHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val roomName: String,
    val role: RoomRole,
    val duration: Long,
    val participantCount: Int,
    val startTime: Long,
    val endTime: Long,
    val thumbnailPath: String? = null,
    val connectionQuality: ConnectionQuality? = null,
    val avgBitrate: Int? = null,
    val totalDataTransferred: Long? = null,
    val disconnectReason: String? = null,
    val isFavorite: Boolean = false,
    val notes: String? = null,
    val tags: String? = null // JSON string for list
) : Serializable
