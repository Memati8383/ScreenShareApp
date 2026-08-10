package com.example.screenmirror

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class ConnectionTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Session(val dayIndex: Int, val minutes: Int, val isHost: Boolean)

    private var sessions: List<Session> = emptyList()
    private var maxMinutes: Int = 1

    private val hostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00B4FF")
    }

    private val viewerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0DFFFFFF")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val cornerRadius = 8f

    fun setData(dailyData: Array<Pair<Int, Int>>) {
        val allMinutes = dailyData.map { it.first + it.second }
        maxMinutes = allMinutes.maxOrNull()?.coerceAtLeast(1) ?: 1

        val list = mutableListOf<Session>()
        dailyData.forEachIndexed { index, (hostMin, viewerMin) ->
            if (hostMin > 0) list.add(Session(index, hostMin, true))
            if (viewerMin > 0) list.add(Session(index, viewerMin, false))
        }
        sessions = list
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (resources.displayMetrics.density * 100).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingLeft = 24f
        val paddingRight = 24f
        val barHeight = height - paddingTop - paddingBottom - 40f
        val barWidth = width - paddingLeft - paddingRight

        val bgRect = RectF(paddingLeft, paddingTop.toFloat(), width - paddingRight, paddingTop + barHeight)
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

        if (sessions.isEmpty()) {
            textPaint.textSize = 28f
            canvas.drawText(
                context.getString(R.string.timeline_no_data),
                width / 2f,
                height / 2f,
                textPaint
            )
            return
        }

        val dayWidth = barWidth / 30f

        for (session in sessions) {
            val x = paddingLeft + session.dayIndex * dayWidth + dayWidth / 2
            val segHeight = if (maxMinutes > 0) {
                (session.minutes.toFloat() / maxMinutes * barHeight * 0.8f).coerceAtLeast(6f)
            } else 6f

            val segTop = paddingTop + barHeight - segHeight
            val segBottom = paddingTop + barHeight

            dotPaint.color = if (session.isHost) Color.parseColor("#00B4FF") else Color.parseColor("#00E676")
            dotPaint.alpha = 200

            val segRect = RectF(
                (x - dayWidth * 0.3f).coerceAtLeast(paddingLeft),
                segTop,
                (x + dayWidth * 0.3f).coerceAtMost(width - paddingRight),
                segBottom
            )
            canvas.drawRoundRect(segRect, cornerRadius, cornerRadius, dotPaint)
        }

        textPaint.textSize = 22f
        for (i in 0..6) {
            val dayOffset = i * 5
            val x = paddingLeft + dayOffset * dayWidth + dayWidth / 2
            val label = when (i) {
                0 -> "Bugün"
                1 -> "5g"
                2 -> "10g"
                3 -> "15g"
                4 -> "20g"
                5 -> "25g"
                6 -> "30g"
                else -> ""
            }
            canvas.drawText(label, x, height - 8f, textPaint)
        }
    }
}
