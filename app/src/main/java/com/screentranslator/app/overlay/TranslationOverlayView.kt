package com.screentranslator.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.withTranslation
import com.screentranslator.app.engine.TranslatedBlock

@SuppressLint("ViewConstructor")
class TranslationOverlayView(
    context: Context,
    private val onDismissRequest: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private var blocks: List<TranslatedBlock> = emptyList()
    private val locationOnScreen = IntArray(2)

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(60, 255, 255, 255)
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val hintBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 15, 23, 42)
    }

    private val hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
    }

    fun setTranslatedBlocks(newBlocks: List<TranslatedBlock>) {
        blocks = newBlocks
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (blocks.isEmpty()) return

        getLocationOnScreen(locationOnScreen)
        val offsetX = locationOnScreen[0].toFloat()
        val offsetY = locationOnScreen[1].toFloat()

        val paddingH = 8f * density
        val paddingV = 4f * density
        val cornerRadius = 8f * density

        for (block in blocks) {
            val left = block.boundingBox.left - offsetX - paddingH / 2f
            val top = block.boundingBox.top - offsetY - paddingV / 2f
            val right = block.boundingBox.right - offsetX + paddingH / 2f
            val bottom = block.boundingBox.bottom - offsetY + paddingV / 2f

            val rect = RectF(left, top, right, bottom)
            boxPaint.color = block.bgColor
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, boxPaint)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

            val availableWidth = (rect.width() - paddingH).toInt().coerceAtLeast(16)
            val availableHeight = (rect.height() - paddingV).toInt().coerceAtLeast(12)

            val layout = buildFittedLayout(
                text = block.translatedText,
                maxWidth = availableWidth,
                maxHeight = availableHeight
            )

            textPaint.color = block.textColor
            val textTop = rect.top + ((rect.height() - layout.height) / 2f).coerceAtLeast(paddingV / 2f)
            canvas.withTranslation(rect.left + paddingH / 2f, textTop) {
                layout.draw(this)
            }
        }

        drawTopDismissHint(canvas)
    }

    private fun buildFittedLayout(
        text: String,
        maxWidth: Int,
        maxHeight: Int
    ): StaticLayout {
        val minSize = 9f * density
        var low = minSize
        var high = (maxHeight * 0.72f).coerceIn(minSize, 26f * density)
        var bestLayout: StaticLayout? = null

        repeat(8) {
            val mid = (low + high) / 2f
            textPaint.textSize = mid
            val candidate = StaticLayout.Builder
                .obtain(text, 0, text.length, textPaint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.05f)
                .setIncludePad(false)
                .build()

            if (candidate.height <= maxHeight) {
                bestLayout = candidate
                low = mid
            } else {
                high = mid
            }
        }

        if (bestLayout != null) return bestLayout!!

        textPaint.textSize = minSize
        return StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.05f)
            .setIncludePad(false)
            .build()
    }

    private fun drawTopDismissHint(canvas: Canvas) {
        val label = "✕  轻点任意处关闭译文"
        val textWidth = hintTextPaint.measureText(label)
        val pillWidth = textWidth + 32 * density
        val pillHeight = 32 * density
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
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            onDismissRequest()
            return true
        }
        return true
    }
}
