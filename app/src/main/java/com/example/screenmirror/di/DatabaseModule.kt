package com.example.screenmirror.di

import android.content.Context
import androidx.room.Room
import com.example.screenmirror.data.local.RoomHistoryDao
import com.example.screenmirror.data.local.RoomHistoryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideRoomHistoryDatabase(
        @ApplicationContext context: Context
    ): RoomHistoryDatabase {
        return Room.databaseBuilder(
            context,
            RoomHistoryDatabase::class.java,
            "room_history_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideRoomHistoryDao(database: RoomHistoryDatabase): RoomHistoryDao {
        return database.roomHistoryDao()
    }
}
