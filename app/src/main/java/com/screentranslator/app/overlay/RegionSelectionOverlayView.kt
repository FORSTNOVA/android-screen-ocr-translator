package com.screentranslator.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class RegionSelectionOverlayView(
    context: Context,
    private val onRegionSelected: (Rect) -> Unit,
    private val onCancelled: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val minSelectionPx = (36 * density).roundToInt()

    private val scrimPaint = Paint().apply {
        color = Color.argb(125, 0, 0, 0)
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.parseColor("#3B82F6")
    }

    private val hintBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 15, 23, 42)
    }

    private val hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
    }

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isSelecting = false
    private val selectionRect = RectF()
    private val locationOnScreen = IntArray(2)

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        if (isSelecting && selectionRect.width() > 4f && selectionRect.height() > 4f) {
            val radius = 8f * density
            canvas.drawRoundRect(selectionRect, radius, radius, clearPaint)
            canvas.drawRoundRect(selectionRect, radius, radius, borderPaint)
        }

        drawTopHint(canvas)
    }

    private fun drawTopHint(canvas: Canvas) {
        val label = "拖动框选要翻译的区域 · 轻点取消"
        val textWidth = hintTextPaint.measureText(label)
        val pillWidth = textWidth + 30 * density
        val pillHeight = 30 * density
        val cx = width / 2f
        val top = 44 * density
        val pillRect = RectF(cx - pillWidth / 2f, top, cx + pillWidth / 2f, top + pillHeight)

        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, hintBgPaint)
        val fm = hintTextPaint.fontMetrics
        val baseline = pillRect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, cx, baseline, hintTextPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                currentX = event.x
                currentY = event.y
                isSelecting = true
                updateSelectionRect()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                currentX = event.x
                currentY = event.y
                updateSelectionRect()
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                currentX = event.x
                currentY = event.y
                updateSelectionRect()
                isSelecting = false

                if (selectionRect.width() >= minSelectionPx && selectionRect.height() >= minSelectionPx) {
                    getLocationOnScreen(locationOnScreen)
                    val offsetX = locationOnScreen[0]
                    val offsetY = locationOnScreen[1]
                    val screenRect = Rect(
                        selectionRect.left.roundToInt() + offsetX,
                        selectionRect.top.roundToInt() + offsetY,
                        selectionRect.right.roundToInt() + offsetX,
                        selectionRect.bottom.roundToInt() + offsetY
                    )
                    onRegionSelected(screenRect)
                } else {
                    onCancelled()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isSelecting = false
                onCancelled()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateSelectionRect() {
        selectionRect.set(
            min(startX, currentX).coerceIn(0f, width.toFloat()),
            min(startY, currentY).coerceIn(0f, height.toFloat()),
            max(startX, currentX).coerceIn(0f, width.toFloat()),
            max(startY, currentY).coerceIn(0f, height.toFloat())
        )
    }
}
