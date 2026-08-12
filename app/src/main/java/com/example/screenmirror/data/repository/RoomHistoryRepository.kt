package com.example.screenmirror.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.screenmirror.data.local.RoomHistoryDao
import com.example.screenmirror.data.local.RoomHistoryEntity
import com.example.screenmirror.model.RoomRole
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHistoryRepository @Inject constructor(
    private val dao: RoomHistoryDao
) {
    
    fun getAllPaged(): Flow<PagingData<RoomHistoryEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { dao.getAllPaged() }
        ).flow
    }
    
    fun getAll(): Flow<List<RoomHistoryEntity>> = dao.getAll()
    
    fun getFavorites(): Flow<List<RoomHistoryEntity>> = dao.getFavorites()
    
    fun searchByName(query: String): Flow<List<RoomHistoryEntity>> = dao.searchByName(query)
    
    fun getByRole(role: RoomRole): Flow<List<RoomHistoryEntity>> = dao.getByRole(role)
    
    fun getByDateRange(startTime: Long, endTime: Long): Flow<List<RoomHistoryEntity>> =
        dao.getByDateRange(startTime, endTime)
    
    suspend fun getById(id: Long): RoomHistoryEntity? = dao.getById(id)
    
    suspend fun insert(room: RoomHistoryEntity): Long = dao.insert(room)
    
    suspend fun update(room: RoomHistoryEntity) = dao.update(room)
    
    suspend fun delete(room: RoomHistoryEntity) = dao.delete(room)
    
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    
    suspend fun deleteAll() = dao.deleteAll()
    
    suspend fun getCount(): Int = dao.getCount()
    
    suspend fun getTotalDuration(): Long = dao.getTotalDuration() ?: 0L
    
    suspend fun getCountByRole(role: RoomRole): Int = dao.getCountByRole(role)
    
    suspend fun getLongestSessions(limit: Int = 10): List<RoomHistoryEntity> =
        dao.getLongestSessions(limit)
    
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) =
        dao.updateFavoriteStatus(id, isFavorite)
}
