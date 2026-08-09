package com.example.screenmirror

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.screenmirror.data.RoomHistory
import com.example.screenmirror.data.RoomHistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RecentViewModel(
    private val manager: RoomHistoryManager
) : ViewModel() {

    private val _rooms = MutableStateFlow<List<RoomHistory>>(emptyList())
    val rooms: StateFlow<List<RoomHistory>> = _rooms

    fun loadRooms() {
        viewModelScope.launch(Dispatchers.IO) {
            _rooms.value = manager.getAll()
        }
    }

    fun deleteRoom(room: RoomHistory) {
        viewModelScope.launch(Dispatchers.IO) {
            manager.deleteRoom(room.id)
            loadRooms()
        }
    }

    fun undoDelete(room: RoomHistory) {
        viewModelScope.launch(Dispatchers.IO) {
            manager.saveRoom(room)
            loadRooms()
        }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            manager.deleteAll()
            loadRooms()
        }
    }
}
