package com.example.screenmirror

import java.text.SimpleDateFormat
import java.util.*

object OnScreenLog {
    private const val MAX_LINES = 80
    private val lines = mutableListOf<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    var onUpdate: ((String) -> Unit)? = null

    @Synchronized
    fun add(tag: String, msg: String) {
        val ts = fmt.format(Date())
        val line = "$ts $tag: $msg"
        lines.add(line)
        if (lines.size > MAX_LINES) lines.removeAt(0)
        onUpdate?.invoke(lines.joinToString("\n"))
    }

    @Synchronized
    fun getAll(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
        onUpdate?.invoke("")
    }
}
