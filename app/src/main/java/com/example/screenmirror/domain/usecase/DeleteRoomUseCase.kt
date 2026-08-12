package com.example.screenmirror.domain.usecase

import com.example.screenmirror.data.local.RoomHistoryEntity
import com.example.screenmirror.data.repository.RoomHistoryRepository
import javax.inject.Inject

class DeleteRoomUseCase @Inject constructor(
    private val repository: RoomHistoryRepository
) {
    suspend operator fun invoke(room: RoomHistoryEntity) = repository.delete(room)
    
    suspend fun deleteById(id: Long) = repository.deleteById(id)
    
    suspend fun deleteAll() = repository.deleteAll()
}
