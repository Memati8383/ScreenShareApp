package com.example.screenmirror

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScreenshotHelper {

    private const val MAX_SCREENSHOT_WIDTH = 1280
    private const val SCREENSHOT_DIR = "ScreenMirror"

    fun takeScreenshot(context: Context, rootView: View, prefix: String): Boolean {
        val screenView = rootView
        val width = screenView.width
        val height = screenView.height

        val bitmap = if (width > MAX_SCREENSHOT_WIDTH) {
            val scale = MAX_SCREENSHOT_WIDTH.toFloat() / width
            val scaledWidth = MAX_SCREENSHOT_WIDTH
            val scaledHeight = (height * scale).toInt()
            val raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(raw)
            screenView.draw(canvas)
            Bitmap.createScaledBitmap(raw, scaledWidth, scaledHeight, true).also {
                if (it !== raw) raw.recycle()
            }
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                Canvas(bmp).also { screenView.draw(it) }
            }
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "${prefix}_$timestamp.png"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(context, bitmap, filename)
            } else {
                saveToFile(bitmap, filename)
            }
        } catch (e: Exception) {
            false
        } finally {
            bitmap.recycle()
        }
    }

    private fun saveWithMediaStore(context: Context, bitmap: Bitmap, filename: String): Boolean {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$SCREENSHOT_DIR")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        contentValues.clear()
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)
        return true
    }

    private fun saveToFile(bitmap: Bitmap, filename: String): Boolean {
        val picturesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        val dir = File(picturesDir, SCREENSHOT_DIR)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return true
    }

    fun showResult(context: Context, success: Boolean, filename: String) {
        val message = if (success) {
            context.getString(R.string.sender_screenshot_saved, filename)
        } else {
            context.getString(R.string.screenshot_error_io)
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
