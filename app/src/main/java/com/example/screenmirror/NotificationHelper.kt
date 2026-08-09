package com.example.screenmirror

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {

    fun createScreenShareChannel(context: Context) {
        createChannel(
            context,
            NotificationConstants.CHANNEL_ID_SCREEN,
            NotificationConstants.CHANNEL_NAME_SCREEN
        )
    }

    fun createViewerChannel(context: Context) {
        createChannel(
            context,
            NotificationConstants.CHANNEL_ID_VIEWER,
            NotificationConstants.CHANNEL_NAME_VIEWER
        )
    }

    private fun createChannel(context: Context, channelId: String, channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
