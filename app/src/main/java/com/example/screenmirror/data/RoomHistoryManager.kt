package com.example.screenmirror.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RoomHistoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("room_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val lock = Any()
    @Volatile private var cachedRooms: List<RoomHistory>? = null

    private fun getCache(): List<RoomHistory> {
        cachedRooms?.let { return it }
        val json = prefs.getString("rooms", "[]")
        val type = object : TypeToken<List<RoomHistory>>() {}.type
        val rooms: List<RoomHistory> = try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        cachedRooms = rooms
        return rooms
    }

    private fun saveToPrefs(rooms: List<RoomHistory>) {
        cachedRooms = rooms
        prefs.edit().putString("rooms", gson.toJson(rooms)).apply()
    }

    fun saveRoom(room: RoomHistory) {
        synchronized(lock) {
            val rooms = getCache().toMutableList()
            rooms.add(0, room)
            if (rooms.size > 50) {
                rooms.removeAt(rooms.lastIndex)
            }
            saveToPrefs(rooms)
        }
    }

    fun getAll(): List<RoomHistory> {
        synchronized(lock) {
            return getCache()
        }
    }

    fun deleteRoom(roomId: Long) {
        synchronized(lock) {
            val rooms = getCache().toMutableList()
            rooms.removeAll { it.id == roomId }
            saveToPrefs(rooms)
        }
    }

    fun deleteAll() {
        synchronized(lock) {
            cachedRooms = emptyList()
            prefs.edit().remove("rooms").apply()
        }
    }

    fun getCount(): Int {
        synchronized(lock) {
            return getCache().size
        }
    }
}
