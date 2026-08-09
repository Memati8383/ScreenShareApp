package com.example.screenmirror.model

enum class RoomRole {
    SENDER,
    VIEWER;

    val label: String
        get() = when (this) {
            SENDER -> "sender"
            VIEWER -> "viewer"
        }

    companion object {
        fun fromString(value: String): RoomRole = when (value.lowercase()) {
            "sender" -> SENDER
            else -> VIEWER
        }
    }
}
