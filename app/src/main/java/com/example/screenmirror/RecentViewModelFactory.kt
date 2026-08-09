package com.example.screenmirror

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.screenmirror.data.RoomHistoryManager

class RecentViewModelFactory(
    private val manager: RoomHistoryManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecentViewModel::class.java)) {
            return RecentViewModel(manager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
