package com.example.screenmirror.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RoomHistoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("room_history", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveRoom(room: RoomHistory) {
        val rooms = getAll().toMutableList()
        rooms.add(0, room)
        if (rooms.size > 50) {
            rooms.removeAt(rooms.lastIndex)
        }
        prefs.edit().putString("rooms", gson.toJson(rooms)).apply()
    }

    fun getAll(): List<RoomHistory> {
        val json = prefs.getString("rooms", "[]")
        val type = object : TypeToken<List<RoomHistory>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteRoom(roomId: Long) {
        val rooms = getAll().toMutableList()
        rooms.removeAll { it.id == roomId }
        prefs.edit().putString("rooms", gson.toJson(rooms)).apply()
    }

    fun deleteAll() {
        prefs.edit().remove("rooms").apply()
    }

    fun getCount(): Int {
        return getAll().size
    }
}
