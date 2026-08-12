package com.example.screenmirror.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [RoomHistoryEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RoomHistoryDatabase : RoomDatabase() {
    abstract fun roomHistoryDao(): RoomHistoryDao
}
