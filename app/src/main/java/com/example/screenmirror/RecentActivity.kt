package com.example.screenmirror

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.example.screenmirror.data.RoomHistory
import com.example.screenmirror.data.RoomHistoryManager
import java.text.SimpleDateFormat
import java.util.*

class RecentActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: RoomAdapter
    private lateinit var historyManager: RoomHistoryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recent)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        tvEmpty = findViewById(R.id.tvEmpty)

        historyManager = RoomHistoryManager(this)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.title = getString(R.string.nav_recent)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        adapter = RoomAdapter { room ->
            showDeleteDialog(room)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val room = adapter.getItemAt(position)
                deleteRoom(room)
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)

        loadRooms()
    }

    private fun loadRooms() {
        val rooms = historyManager.getAll()
        adapter.submitList(rooms)
        tvEmpty.visibility = if (rooms.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (rooms.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun deleteRoom(room: RoomHistory) {
        historyManager.deleteRoom(room.id)
        loadRooms()
        Snackbar.make(recyclerView, "${room.roomName} silindi", Snackbar.LENGTH_LONG)
            .setAction("Geri Al") {
                historyManager.saveRoom(room)
                loadRooms()
            }
            .show()
    }

    private fun showDeleteDialog(room: RoomHistory) {
        val dialogTheme = if (AppSettings.isDarkTheme(this)) {
            R.style.Theme_ScreenShare_Dialog
        } else {
            R.style.Theme_ScreenShare_Light_Dialog
        }
        AlertDialog.Builder(this, dialogTheme)
            .setTitle("Oda Sil")
            .setMessage("${room.roomName} odasını silmek istediğinize emin misiniz?")
            .setPositiveButton("Sil") { _, _ -> deleteRoom(room) }
            .setNegativeButton("İptal", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    inner class RoomAdapter(
        private val onItemClick: (RoomHistory) -> Unit
    ) : RecyclerView.Adapter<RoomAdapter.RoomViewHolder>() {

        private var items = listOf<RoomHistory>()

        fun submitList(newItems: List<RoomHistory>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun getItemAt(position: Int): RoomHistory = items[position]

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_room_history, parent, false)
            return RoomViewHolder(view)
        }

        override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvRoomName: TextView = itemView.findViewById(R.id.tvRoomName)
            private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
            private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
            private val tvParticipants: TextView = itemView.findViewById(R.id.tvParticipants)
            private val statusDot: View = itemView.findViewById(R.id.statusDot)

            fun bind(room: RoomHistory) {
                tvRoomName.text = room.roomName
                tvTime.text = formatDate(room.endTime)
                tvDuration.text = formatDuration(room.duration)
                tvParticipants.text = "Katılımcı: ${room.participantCount}"

                val dotColor = if (room.role == "sender") {
                    ContextCompat.getColor(itemView.context, R.color.dark_accent)
                } else {
                    ContextCompat.getColor(itemView.context, R.color.dark_status_good)
                }
                statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(dotColor)

                itemView.setOnClickListener { onItemClick(room) }
            }
        }
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale("tr"))
        return sdf.format(Date(timestamp))
    }

    private fun formatDuration(millis: Long): String {
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        val seconds = (millis % 60000) / 1000

        return when {
            hours > 0 -> "${hours}sa ${minutes}dk"
            minutes > 0 -> "${minutes}dk ${seconds}sn"
            else -> "${seconds}sn"
        }
    }
}
