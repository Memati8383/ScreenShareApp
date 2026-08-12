package com.example.screenmirror.domain.usecase

import com.example.screenmirror.data.repository.RoomHistoryRepository
import com.example.screenmirror.model.RoomRole
import javax.inject.Inject

data class RoomStatistics(
    val totalSessions: Int,
    val totalDuration: Long,
    val senderCount: Int,
    val viewerCount: Int,
    val averageDuration: Long,
    val longestSession: Long
)

class GetStatisticsUseCase @Inject constructor(
    private val repository: RoomHistoryRepository
) {
    suspend operator fun invoke(): RoomStatistics {
        val totalSessions = repository.getCount()
        val totalDuration = repository.getTotalDuration()
        val senderCount = repository.getCountByRole(RoomRole.SENDER)
        val viewerCount = repository.getCountByRole(RoomRole.VIEWER)
        val longestSessions = repository.getLongestSessions(1)
        val longestSession = longestSessions.firstOrNull()?.duration ?: 0L
        
        return RoomStatistics(
            totalSessions = totalSessions,
            totalDuration = totalDuration,
            senderCount = senderCount,
            viewerCount = viewerCount,
            averageDuration = if (totalSessions > 0) totalDuration / totalSessions else 0L,
            longestSession = longestSession
        )
    }
}
