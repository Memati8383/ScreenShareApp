package com.example.screenmirror.presentation.recent

import com.example.screenmirror.data.local.RoomHistoryEntity
import com.example.screenmirror.domain.usecase.RoomStatistics
import com.example.screenmirror.model.RoomRole

sealed class RecentUiState {
    object Loading : RecentUiState()
    data class Success(
        val rooms: List<RoomHistoryEntity>,
        val statistics: RoomStatistics?,
        val filterType: FilterType,
        val searchQuery: String
    ) : RecentUiState()
    data class Error(val message: String) : RecentUiState()
}

sealed class FilterType {
    object All : FilterType()
    object Favorites : FilterType()
    data class ByRole(val role: RoomRole) : FilterType()
    data class ByDateRange(val startTime: Long, val endTime: Long) : FilterType()
}

sealed class RecentEvent {
    data class DeleteRoom(val room: RoomHistoryEntity) : RecentEvent()
    data class UndoDelete(val room: RoomHistoryEntity) : RecentEvent()
    object DeleteAll : RecentEvent()
    data class ToggleFavorite(val id: Long, val isFavorite: Boolean) : RecentEvent()
    data class Search(val query: String) : RecentEvent()
    data class ApplyFilter(val filterType: FilterType) : RecentEvent()
    object LoadStatistics : RecentEvent()
    data class ShowDetails(val room: RoomHistoryEntity) : RecentEvent()
}
