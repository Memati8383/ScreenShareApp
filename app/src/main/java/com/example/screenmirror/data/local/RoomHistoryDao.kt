package com.example.screenmirror.data.local

import androidx.paging.PagingSource
import androidx.room.*
import com.example.screenmirror.model.RoomRole
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomHistoryDao {
    
    @Query("SELECT * FROM room_history ORDER BY endTime DESC")
    fun getAllPaged(): PagingSource<Int, RoomHistoryEntity>
    
    @Query("SELECT * FROM room_history ORDER BY endTime DESC")
    fun getAll(): Flow<List<RoomHistoryEntity>>
    
    @Query("SELECT * FROM room_history WHERE isFavorite = 1 ORDER BY endTime DESC")
    fun getFavorites(): Flow<List<RoomHistoryEntity>>
    
    @Query("SELECT * FROM room_history WHERE roomName LIKE '%' || :query || '%' ORDER BY endTime DESC")
    fun searchByName(query: String): Flow<List<RoomHistoryEntity>>
    
    @Query("SELECT * FROM room_history WHERE role = :role ORDER BY endTime DESC")
    fun getByRole(role: RoomRole): Flow<List<RoomHistoryEntity>>
    
    @Query("SELECT * FROM room_history WHERE endTime BETWEEN :startTime AND :endTime ORDER BY endTime DESC")
    fun getByDateRange(startTime: Long, endTime: Long): Flow<List<RoomHistoryEntity>>
    
    @Query("SELECT * FROM room_history WHERE id = :id")
    suspend fun getById(id: Long): RoomHistoryEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(room: RoomHistoryEntity): Long
    
    @Update
    suspend fun update(room: RoomHistoryEntity)
    
    @Delete
    suspend fun delete(room: RoomHistoryEntity)
    
    @Query("DELETE FROM room_history WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM room_history")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM room_history")
    suspend fun getCount(): Int
    
    @Query("SELECT SUM(duration) FROM room_history")
    suspend fun getTotalDuration(): Long?
    
    @Query("SELECT COUNT(*) FROM room_history WHERE role = :role")
    suspend fun getCountByRole(role: RoomRole): Int
    
    @Query("SELECT * FROM room_history ORDER BY duration DESC LIMIT :limit")
    suspend fun getLongestSessions(limit: Int = 10): List<RoomHistoryEntity>
    
    @Query("UPDATE room_history SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)
}
