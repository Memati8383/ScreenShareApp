package com.example.screenmirror.data.local

import androidx.room.TypeConverter
import com.example.screenmirror.model.ConnectionQuality
import com.example.screenmirror.model.RoomRole

class Converters {
    @TypeConverter
    fun fromRoomRole(value: RoomRole): String = value.name

    @TypeConverter
    fun toRoomRole(value: String): RoomRole = RoomRole.valueOf(value)

    @TypeConverter
    fun fromConnectionQuality(value: ConnectionQuality?): String? = value?.name

    @TypeConverter
    fun toConnectionQuality(value: String?): ConnectionQuality? =
        value?.let { ConnectionQuality.valueOf(it) }
}
