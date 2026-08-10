package com.example.screenmirror

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.screenmirror.R

class WeeklyActivityView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class DayData(val label: String, val minutes: Int)

    private var data: List<DayData> = emptyList()
    private var maxMinutes: Int = 1

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00B4FF")
    }

    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1AFFFFFF")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FFFFFF")
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FFFFFF")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val cornerRadius = 12f

    fun setData(dailyMinutes: IntArray) {
        val days = arrayOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
        data = dailyMinutes.mapIndexed { index, minutes ->
            DayData(days[index], minutes)
        }
        maxMinutes = dailyMinutes.maxOrNull()?.coerceAtLeast(1) ?: 1
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (resources.displayMetrics.density * 180).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (data.isEmpty()) {
            textPaint.textSize = 36f
            canvas.drawText(
                context.getString(R.string.activity_no_data),
                width / 2f,
                height / 2f,
                textPaint
            )
            return
        }

        val paddingLeft = 20f
        val paddingRight = 20f
        val paddingTop = 40f
        val paddingBottom = 60f

        val barAreaWidth = width - paddingLeft - paddingRight
        val barAreaHeight = height - paddingTop - paddingBottom
        val barWidth = barAreaWidth / data.size * 0.5f
        val barSpacing = barAreaWidth / data.size

        data.forEachIndexed { index, dayData ->
            val x = paddingLeft + barSpacing * index + barSpacing / 2

            val barHeight = if (maxMinutes > 0) {
                (dayData.minutes.toFloat() / maxMinutes * barAreaHeight).coerceAtLeast(4f)
            } else 4f

            val barTop = paddingTop + barAreaHeight - barHeight
            val barBottom = paddingTop + barAreaHeight

            val barRect = RectF(
                x - barWidth / 2,
                barTop,
                x + barWidth / 2,
                barBottom
            )
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barBgPaint)

            if (dayData.minutes > 0) {
                val activeRect = RectF(
                    x - barWidth / 2,
                    barTop,
                    x + barWidth / 2,
                    barBottom
                )
                canvas.drawRoundRect(activeRect, cornerRadius, cornerRadius, barPaint)
            }

            canvas.drawText(dayData.label, x, barBottom + 40f, labelPaint)

            if (dayData.minutes > 0) {
                canvas.drawText("${dayData.minutes}", x, barTop - 12f, valuePaint)
            }
        }
    }
}
