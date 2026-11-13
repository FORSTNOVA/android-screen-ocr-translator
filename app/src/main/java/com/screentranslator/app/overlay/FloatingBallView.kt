package com.screentranslator.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class FloatingBallView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val screenWidthProvider: () -> Int,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val ballSizePx = (54 * density).toInt()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setShadowLayer(10 * density, 0f, 4 * density, Color.argb(100, 15, 23, 42))
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.argb(230, 255, 255, 255)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    var isTranslating: Boolean = false
        set(value) {
            field = value
            updateGradientShader()
            invalidate()
        }

    private fun updateGradientShader() {
        val size = ballSizePx.toFloat()
        val colors = if (isTranslating) {
            intArrayOf(Color.parseColor("#F59E0B"), Color.parseColor("#EA580C"))
        } else {
            intArrayOf(Color.parseColor("#2563EB"), Color.parseColor("#4F46E5"))
        }
        bgPaint.shader = LinearGradient(0f, 0f, size, size, colors, null, Shader.TileMode.CLAMP)
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var longPressTriggered = false

    private val longPressRunnable = Runnable {
        if (!isDragging) {
            longPressTriggered = true
            onLongPress()
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        updateGradientShader()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(ballSizePx, ballSizePx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - 4 * density

        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)

        val label = if (isTranslating) "…" else "译"
        val fontMetrics = textPaint.fontMetrics
        val baseline = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(label, cx, baseline, textPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                longPressTriggered = false
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                    removeCallbacks(longPressRunnable)
                }
                if (isDragging) {
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    runCatching { windowManager.updateViewLayout(this, layoutParams) }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (isDragging) {
                    snapToNearestEdge()
                } else if (!longPressTriggered) {
                    onTap()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun snapToNearestEdge() {
        val screenWidth = screenWidthProvider()
        val margin = (12 * density).toInt()
        val center = layoutParams.x + ballSizePx / 2
        layoutParams.x = if (center < screenWidth / 2) {
            margin
        } else {
            (screenWidth - ballSizePx - margin).coerceAtLeast(margin)
        }
        runCatching { windowManager.updateViewLayout(this, layoutParams) }
    }
}
