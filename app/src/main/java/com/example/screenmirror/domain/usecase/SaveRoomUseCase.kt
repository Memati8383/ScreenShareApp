package com.example.screenmirror.domain.usecase

import com.example.screenmirror.data.local.RoomHistoryEntity
import com.example.screenmirror.data.repository.RoomHistoryRepository
import javax.inject.Inject

class SaveRoomUseCase @Inject constructor(
    private val repository: RoomHistoryRepository
) {
    suspend operator fun invoke(room: RoomHistoryEntity): Long = repository.insert(room)
    
    suspend fun update(room: RoomHistoryEntity) = repository.update(room)
    
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) =
        repository.toggleFavorite(id, isFavorite)
}
