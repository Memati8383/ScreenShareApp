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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.example.screenmirror.data.RoomHistory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RecentActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: RoomAdapter
    private lateinit var viewModel: RecentViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recent)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        tvEmpty = findViewById(R.id.tvEmpty)

        val manager = (application as ScreenMirrorApp).roomHistoryManager
        viewModel = ViewModelProvider(this, RecentViewModelFactory(manager))[RecentViewModel::class.java]

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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rooms.collect { rooms ->
                    adapter.submitList(rooms)
                    tvEmpty.visibility = if (rooms.isEmpty()) View.VISIBLE else View.GONE
                    recyclerView.visibility = if (rooms.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        viewModel.loadRooms()
    }

    private fun deleteRoom(room: RoomHistory) {
        viewModel.deleteRoom(room)
        Snackbar.make(recyclerView, getString(R.string.recent_deleted, room.roomName), Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.recent_undo)) {
                viewModel.undoDelete(room)
            }
            .show()
    }

    private fun showDeleteDialog(room: RoomHistory) {
        AlertDialog.Builder(this, R.style.Theme_ScreenShare_Dialog)
            .setTitle(getString(R.string.recent_delete_title))
            .setMessage(getString(R.string.recent_delete_msg, room.roomName))
            .setPositiveButton(getString(R.string.recent_delete_confirm)) { _, _ -> deleteRoom(room) }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        if (item.itemId == R.id.action_delete_all) {
            showDeleteAllDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showDeleteAllDialog() {
        AlertDialog.Builder(this, R.style.Theme_ScreenShare_Dialog)
            .setTitle(getString(R.string.recent_delete_title))
            .setMessage("Tum oda gecmisini silmek istediginize emin misiniz?")
            .setPositiveButton(getString(R.string.recent_delete_confirm)) { _, _ ->
                viewModel.deleteAll()
                Snackbar.make(recyclerView, "Gecmis temizlendi", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
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
                tvParticipants.text = itemView.context.getString(R.string.recent_participants, room.participantCount)

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
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.forLanguageTag("tr"))
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
