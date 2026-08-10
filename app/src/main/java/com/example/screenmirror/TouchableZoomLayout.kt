package com.example.screenmirror

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

class TouchableZoomLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val matrix = Matrix()
    private val prevMatrix = Matrix()
    private var currentScale = 1f
    private val matrixValues = FloatArray(9)
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var isScaling = false

    var onTap: (() -> Unit)? = null
    private var downX = 0f
    private var downY = 0f
    private var isDragging = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            prevMatrix.set(matrix)
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val newScale = currentScale * scaleFactor
            if (newScale in 0.5f..5f) {
                currentScale = newScale
                val focusX = detector.focusX
                val focusY = detector.focusY
                matrix.set(prevMatrix)
                matrix.postScale(scaleFactor, scaleFactor, focusX, focusY)
                prevMatrix.set(matrix)
                constrainMatrix()
                applyMatrix()
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            if (currentScale < 1f) {
                resetZoom()
            }
        }
    })

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                isDragging = false
                prevMatrix.set(matrix)
            }
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount == 1 && !isScaling && currentScale > 1f) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        isDragging = true
                        matrix.set(prevMatrix)
                        matrix.postTranslate(dx, dy)
                        constrainMatrix()
                        applyMatrix()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && !isScaling) {
                    onTap?.invoke()
                }
                isDragging = false
            }
        }
        scaleDetector.onTouchEvent(ev)
        return true
    }

    private fun applyMatrix() {
        for (i in 0 until childCount) {
            getChildAt(i)?.let { child ->
                child.matrix?.set(matrix)
            }
        }
    }

    private fun constrainMatrix() {
        matrix.getValues(matrixValues)
        val scale = matrixValues[Matrix.MSCALE_X]
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]
        val w = width.toFloat()
        val h = height.toFloat()
        val sw = w * scale
        val sh = h * scale
        var fx = 0f
        var fy = 0f
        if (sw > w) {
            if (transX > 0) fx = -transX
            if (transX < w - sw) fx = w - sw - transX
        } else {
            fx = (w - sw) / 2f - transX
        }
        if (sh > h) {
            if (transY > 0) fy = -transY
            if (transY < h - sh) fy = h - sh - transY
        } else {
            fy = (h - sh) / 2f - transY
        }
        if (fx != 0f || fy != 0f) matrix.postTranslate(fx, fy)
    }

    fun resetZoom() {
        matrix.reset()
        currentScale = 1f
        applyMatrix()
    }
}
