package com.example.screenmirror

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class ScreenshotAdapter(
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ScreenshotAdapter.ViewHolder>() {

    private val items = mutableListOf<Uri>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivScreenshot: ImageView = view.findViewById(R.id.ivScreenshot)
        val btnRemove: ImageView = view.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_screenshot, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val uri = items[position]
        val context = holder.itemView.context

        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            holder.ivScreenshot.setImageBitmap(bitmap)
        } catch (_: Exception) {
            holder.ivScreenshot.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.btnRemove.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onRemove(pos)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun addItem(uri: Uri) {
        items.add(uri)
        notifyItemInserted(items.size - 1)
    }

    fun removeItem(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, items.size)
        }
    }

    fun getItems(): List<Uri> = items.toList()

    fun clear() {
        val size = items.size
        items.clear()
        notifyItemRangeRemoved(0, size)
    }
}
