package com.example.screenmirror.domain.usecase

import com.example.screenmirror.data.local.RoomHistoryEntity
import com.example.screenmirror.data.repository.RoomHistoryRepository
import com.example.screenmirror.model.RoomRole
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRoomHistoryUseCase @Inject constructor(
    private val repository: RoomHistoryRepository
) {
    operator fun invoke(): Flow<List<RoomHistoryEntity>> = repository.getAll()
    
    fun getFavorites(): Flow<List<RoomHistoryEntity>> = repository.getFavorites()
    
    fun searchByName(query: String): Flow<List<RoomHistoryEntity>> = repository.searchByName(query)
    
    fun filterByRole(role: RoomRole): Flow<List<RoomHistoryEntity>> = repository.getByRole(role)
    
    fun filterByDateRange(startTime: Long, endTime: Long): Flow<List<RoomHistoryEntity>> =
        repository.getByDateRange(startTime, endTime)
}
