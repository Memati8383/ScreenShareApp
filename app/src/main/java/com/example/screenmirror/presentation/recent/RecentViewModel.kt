package com.example.screenmirror.presentation.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.screenmirror.data.local.RoomHistoryEntity
import com.example.screenmirror.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecentViewModel @Inject constructor(
    private val getRoomHistoryUseCase: GetRoomHistoryUseCase,
    private val saveRoomUseCase: SaveRoomUseCase,
    private val deleteRoomUseCase: DeleteRoomUseCase,
    private val getStatisticsUseCase: GetStatisticsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecentUiState>(RecentUiState.Loading)
    val uiState: StateFlow<RecentUiState> = _uiState.asStateFlow()

    private var currentFilter: FilterType = FilterType.All
    private var currentSearchQuery: String = ""
    private var loadJob: Job? = null

    init {
        loadRooms()
        loadStatistics()
    }

    fun onEvent(event: RecentEvent) {
        when (event) {
            is RecentEvent.DeleteRoom -> deleteRoom(event.room)
            is RecentEvent.UndoDelete -> undoDelete(event.room)
            is RecentEvent.DeleteAll -> deleteAll()
            is RecentEvent.ToggleFavorite -> toggleFavorite(event.id, event.isFavorite)
            is RecentEvent.Search -> search(event.query)
            is RecentEvent.ApplyFilter -> applyFilter(event.filterType)
            is RecentEvent.LoadStatistics -> loadStatistics()
            is RecentEvent.ShowDetails -> {} // Handled by UI
        }
    }

    private fun loadRooms() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val flow = when (currentFilter) {
                    is FilterType.All -> {
                        if (currentSearchQuery.isNotEmpty()) {
                            getRoomHistoryUseCase.searchByName(currentSearchQuery)
                        } else {
                            getRoomHistoryUseCase()
                        }
                    }
                    is FilterType.Favorites -> getRoomHistoryUseCase.getFavorites()
                    is FilterType.ByRole -> getRoomHistoryUseCase.filterByRole((currentFilter as FilterType.ByRole).role)
                    is FilterType.ByDateRange -> {
                        val filter = currentFilter as FilterType.ByDateRange
                        getRoomHistoryUseCase.filterByDateRange(filter.startTime, filter.endTime)
                    }
                }

                flow.collect { rooms ->
                    val stats = if (_uiState.value is RecentUiState.Success) {
                        (_uiState.value as RecentUiState.Success).statistics
                    } else null

                    _uiState.value = RecentUiState.Success(
                        rooms = rooms,
                        statistics = stats,
                        filterType = currentFilter,
                        searchQuery = currentSearchQuery
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = RecentUiState.Error(e.message ?: "Bilinmeyen hata")
            }
        }
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            try {
                val stats = getStatisticsUseCase()
                val currentState = _uiState.value
                if (currentState is RecentUiState.Success) {
                    _uiState.value = currentState.copy(statistics = stats)
                }
            } catch (e: Exception) {
                // İstatistikler yüklenemezse sessizce başarısız ol
            }
        }
    }

    private fun deleteRoom(room: RoomHistoryEntity) {
        viewModelScope.launch {
            try {
                deleteRoomUseCase(room)
                loadStatistics()
            } catch (e: Exception) {
                _uiState.value = RecentUiState.Error(e.message ?: "Silme başarısız")
            }
        }
    }

    private fun undoDelete(room: RoomHistoryEntity) {
        viewModelScope.launch {
            try {
                saveRoomUseCase(room)
                loadStatistics()
            } catch (e: Exception) {
                _uiState.value = RecentUiState.Error(e.message ?: "Geri alma başarısız")
            }
        }
    }

    private fun deleteAll() {
        viewModelScope.launch {
            try {
                deleteRoomUseCase.deleteAll()
                loadStatistics()
            } catch (e: Exception) {
                _uiState.value = RecentUiState.Error(e.message ?: "Silme başarısız")
            }
        }
    }

    private fun toggleFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            try {
                saveRoomUseCase.toggleFavorite(id, isFavorite)
            } catch (e: Exception) {
                _uiState.value = RecentUiState.Error(e.message ?: "Favori işlemi başarısız")
            }
        }
    }

    private fun search(query: String) {
        currentSearchQuery = query
        loadRooms()
    }

    private fun applyFilter(filterType: FilterType) {
        currentFilter = filterType
        loadRooms()
    }
}
