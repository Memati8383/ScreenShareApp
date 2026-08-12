package com.example.screenmirror.presentation.recent

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.screenmirror.R
import com.example.screenmirror.data.local.RoomHistoryEntity
import com.example.screenmirror.model.RoomRole
import java.text.SimpleDateFormat
import java.util.*

class RoomHistoryAdapter(
    private val onItemClick: (RoomHistoryEntity) -> Unit,
    private val onFavoriteClick: (RoomHistoryEntity) -> Unit,
    private val onDeleteClick: (RoomHistoryEntity) -> Unit
) : ListAdapter<RoomHistoryEntity, RoomHistoryAdapter.RoomViewHolder>(RoomDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_history_new, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun getItemAt(position: Int): RoomHistoryEntity = getItem(position)

    inner class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRoomName: TextView = itemView.findViewById(R.id.tvRoomName)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvParticipants: TextView = itemView.findViewById(R.id.tvParticipants)
        private val tvRole: TextView = itemView.findViewById(R.id.tvRole)
        private val statusDot: View = itemView.findViewById(R.id.statusDot)
        private val ivFavorite: ImageView = itemView.findViewById(R.id.ivFavorite)
        private val ivDelete: ImageView = itemView.findViewById(R.id.ivDelete)
        private val qualityIndicator: View? = itemView.findViewById(R.id.qualityIndicator)

        fun bind(room: RoomHistoryEntity) {
            tvRoomName.text = room.roomName
            tvTime.text = formatDate(room.endTime)
            tvDuration.text = formatDuration(room.duration)
            tvParticipants.text = itemView.context.getString(
                R.string.recent_participants,
                room.participantCount
            )

            // Rol gösterimi
            tvRole.text = when (room.role) {
                RoomRole.SENDER -> itemView.context.getString(R.string.role_sender)
                RoomRole.VIEWER -> itemView.context.getString(R.string.role_viewer)
            }

            // Status dot color
            val dotColor = when (room.role) {
                RoomRole.SENDER -> ContextCompat.getColor(itemView.context, R.color.dark_accent)
                RoomRole.VIEWER -> ContextCompat.getColor(itemView.context, R.color.dark_status_good)
            }
            statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(dotColor)

            // Favori ikonu
            ivFavorite.setImageResource(
                if (room.isFavorite) R.drawable.ic_badge_star else R.drawable.ic_badge_star_locked
            )
            ivFavorite.setColorFilter(
                if (room.isFavorite) 
                    ContextCompat.getColor(itemView.context, R.color.dark_accent)
                else 
                    ContextCompat.getColor(itemView.context, R.color.text_hint)
            )

            // Bağlantı kalitesi göstergesi
            room.connectionQuality?.let { quality ->
                qualityIndicator?.visibility = View.VISIBLE
                val qualityColor = when (quality) {
                    com.example.screenmirror.model.ConnectionQuality.GOOD -> R.color.dark_status_good
                    com.example.screenmirror.model.ConnectionQuality.MEDIUM -> R.color.dark_status_warning
                    com.example.screenmirror.model.ConnectionQuality.BAD -> R.color.dark_status_error
                }
                qualityIndicator?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, qualityColor)
                )
            } ?: run {
                qualityIndicator?.visibility = View.GONE
            }

            // Click listeners
            itemView.setOnClickListener { onItemClick(room) }
            ivFavorite.setOnClickListener { onFavoriteClick(room) }
            ivDelete.setOnClickListener { onDeleteClick(room) }
        }

        private fun formatDate(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60000 -> itemView.context.getString(R.string.time_just_now)
                diff < 3600000 -> {
                    val minutes = diff / 60000
                    itemView.context.getString(R.string.time_minutes_ago, minutes)
                }
                diff < 86400000 -> {
                    val hours = diff / 3600000
                    itemView.context.getString(R.string.time_hours_ago, hours)
                }
                diff < 172800000 -> itemView.context.getString(R.string.time_yesterday)
                else -> {
                    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.forLanguageTag("tr"))
                    sdf.format(Date(timestamp))
                }
            }
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

    private class RoomDiffCallback : DiffUtil.ItemCallback<RoomHistoryEntity>() {
        override fun areItemsTheSame(oldItem: RoomHistoryEntity, newItem: RoomHistoryEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RoomHistoryEntity, newItem: RoomHistoryEntity): Boolean {
            return oldItem == newItem
        }
    }
}
