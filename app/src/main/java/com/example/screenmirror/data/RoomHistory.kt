package com.example.screenmirror.data

data class RoomHistory(
    val id: Long = System.currentTimeMillis(),
    val roomName: String,
    val role: String,
    val duration: Long,
    val participantCount: Int,
    val startTime: Long,
    val endTime: Long
)
