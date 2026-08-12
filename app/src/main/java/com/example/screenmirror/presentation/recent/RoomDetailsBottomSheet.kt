package com.example.screenmirror.presentation.recent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.screenmirror.R
import com.example.screenmirror.data.local.RoomHistoryEntity
import com.example.screenmirror.model.RoomRole
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class RoomDetailsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var room: RoomHistoryEntity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_room_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        room = arguments?.getSerializable(ARG_ROOM) as? RoomHistoryEntity ?: run {
            dismiss()
            return
        }

        setupViews(view)
    }

    private fun setupViews(view: View) {
        view.findViewById<TextView>(R.id.tvRoomName).text = room.roomName
        view.findViewById<TextView>(R.id.tvRole).text = when (room.role) {
            RoomRole.SENDER -> getString(R.string.role_sender)
            RoomRole.VIEWER -> getString(R.string.role_viewer)
        }
        
        view.findViewById<TextView>(R.id.tvStartTime).text = formatDateTime(room.startTime)
        view.findViewById<TextView>(R.id.tvEndTime).text = formatDateTime(room.endTime)
        view.findViewById<TextView>(R.id.tvDuration).text = formatDuration(room.duration)
        view.findViewById<TextView>(R.id.tvParticipants).text = room.participantCount.toString()

        // Bağlantı kalitesi
        room.connectionQuality?.let { quality ->
            view.findViewById<TextView>(R.id.tvConnectionQuality).text = when (quality) {
                com.example.screenmirror.model.ConnectionQuality.GOOD -> getString(R.string.quality_good)
                com.example.screenmirror.model.ConnectionQuality.MEDIUM -> getString(R.string.quality_medium)
                com.example.screenmirror.model.ConnectionQuality.BAD -> getString(R.string.quality_bad)
            }
        } ?: run {
            view.findViewById<View>(R.id.layoutConnectionQuality).visibility = View.GONE
        }

        // Ortalama bitrate
        room.avgBitrate?.let { bitrate ->
            view.findViewById<TextView>(R.id.tvAvgBitrate).text = "${bitrate / 1000} kbps"
        } ?: run {
            view.findViewById<View>(R.id.layoutAvgBitrate).visibility = View.GONE
        }

        // Toplam veri transferi
        room.totalDataTransferred?.let { data ->
            view.findViewById<TextView>(R.id.tvDataTransferred).text = formatDataSize(data)
        } ?: run {
            view.findViewById<View>(R.id.layoutDataTransferred).visibility = View.GONE
        }

        // Bağlantı kesme nedeni
        room.disconnectReason?.let { reason ->
            view.findViewById<TextView>(R.id.tvDisconnectReason).text = reason
        } ?: run {
            view.findViewById<View>(R.id.layoutDisconnectReason).visibility = View.GONE
        }

        // Notlar
        room.notes?.let { notes ->
            view.findViewById<TextView>(R.id.tvNotes).text = notes
        } ?: run {
            view.findViewById<View>(R.id.layoutNotes).visibility = View.GONE
        }

        // Butonlar
        view.findViewById<MaterialButton>(R.id.btnReconnect).setOnClickListener {
            // TODO: Yeniden bağlanma özelliği
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnClose).setOnClickListener {
            dismiss()
        }
    }

    private fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.forLanguageTag("tr"))
        return sdf.format(Date(timestamp))
    }

    private fun formatDuration(millis: Long): String {
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        val seconds = (millis % 60000) / 1000

        return when {
            hours > 0 -> "${hours}sa ${minutes}dk ${seconds}sn"
            minutes > 0 -> "${minutes}dk ${seconds}sn"
            else -> "${seconds}sn"
        }
    }

    private fun formatDataSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    companion object {
        private const val ARG_ROOM = "room"

        fun newInstance(room: RoomHistoryEntity): RoomDetailsBottomSheet {
            return RoomDetailsBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_ROOM, room as java.io.Serializable)
                }
            }
        }
    }
}
