package com.screentranslator.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap, sourceLanguage: LanguageOption): List<OcrBlock>
    fun close()
}

class MlKitOcrEngine : OcrEngine {

    private val recognizers = mutableMapOf<OcrScript, TextRecognizer>()

    private fun getRecognizer(script: OcrScript): TextRecognizer {
        return recognizers.getOrPut(script) {
            when (script) {
                OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                OcrScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                OcrScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                OcrScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            }
        }
    }

    override suspend fun recognize(bitmap: Bitmap, sourceLanguage: LanguageOption): List<OcrBlock> {
        val recognizer = getRecognizer(sourceLanguage.ocrScript)
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()

        val rawLines = result.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val text = line.text.trim()
                if (text.isBlank() || box.width() < 10 || box.height() < 10) return@mapNotNull null
                LineItem(text = text, box = Rect(box))
            }

        val mergedParagraphs = mergeLinesIntoParagraphs(rawLines, sourceLanguage.ocrScript)

        return mergedParagraphs.map { para ->
            val bgColor = sampleEdgeBackgroundColor(bitmap, para.box)
            val textColor = pickContrastingTextColor(bgColor)
            OcrBlock(
                text = para.text,
                boundingBox = para.box,
                bgColor = bgColor,
                textColor = textColor
            )
        }
    }

    private data class LineItem(val text: String, val box: Rect)

    private data class ParagraphCluster(
        val lines: MutableList<LineItem>,
        val box: Rect,
        var lastLineHeight: Int
    ) {
        fun add(line: LineItem) {
            lines.add(line)
            box.union(line.box)
            lastLineHeight = line.box.height()
        }
    }

    /**
     * 按行高相近度、垂直间距和水平对齐度，将散碎的识别行合并为自然段落，减少断句导致的翻译语序错误。
     */
    private fun mergeLinesIntoParagraphs(
        lines: List<LineItem>,
        script: OcrScript
    ): List<LineItem> {
        if (lines.isEmpty()) return emptyList()
        val sorted = lines.sortedWith(compareBy<LineItem> { it.box.top }.thenBy { it.box.left })
        val clusters = mutableListOf<ParagraphCluster>()

        for (line in sorted) {
            val lineHeight = line.box.height().coerceAtLeast(1)
            val targetCluster = clusters.lastOrNull { cluster ->
                val avgHeight = ((cluster.lastLineHeight + lineHeight) / 2f).coerceAtLeast(1f)
                val heightRatio = max(cluster.lastLineHeight, lineHeight).toFloat() /
                    min(cluster.lastLineHeight, lineHeight).coerceAtLeast(1)
                val verticalGap = line.box.top - cluster.box.bottom
                val horizontalOverlap = min(cluster.box.right, line.box.right) -
                    max(cluster.box.left, line.box.left)
                val leftDiff = abs(cluster.box.left - line.box.left)

                heightRatio <= 1.65f &&
                    verticalGap >= -avgHeight * 0.35f &&
                    verticalGap <= avgHeight * 0.85f &&
                    (horizontalOverlap > 0 || leftDiff <= avgHeight * 2.2f)
            }

            if (targetCluster != null) {
                targetCluster.add(line)
            } else {
                clusters.add(
                    ParagraphCluster(
                        lines = mutableListOf(line),
                        box = Rect(line.box),
                        lastLineHeight = lineHeight
                    )
                )
            }
        }

        val joiner = if (script == OcrScript.LATIN) " " else ""
        return clusters.map { cluster ->
            val combinedText = cluster.lines.joinToString(joiner) { it.text }.trim()
            LineItem(text = combinedText, box = cluster.box)
        }
    }

    /**
     * 采样文本框外围边缘像素的平均色作为遮罩底色，使译文自然融入原画面。
     */
    private fun sampleEdgeBackgroundColor(bitmap: Bitmap, box: Rect): Int {
        val left = (box.left - 3).coerceIn(0, bitmap.width - 1)
        val right = (box.right + 3).coerceIn(0, bitmap.width - 1)
        val top = (box.top - 3).coerceIn(0, bitmap.height - 1)
        val bottom = (box.bottom + 3).coerceIn(0, bitmap.height - 1)

        val samplePoints = listOf(
            left to top,
            ((left + right) / 2) to top,
            right to top,
            left to bottom,
            ((left + right) / 2) to bottom,
            right to bottom
        )

        var rSum = 0
        var gSum = 0
        var bSum = 0
        for ((x, y) in samplePoints) {
            val pixel = bitmap.getPixel(x, y)
            rSum += Color.red(pixel)
            gSum += Color.green(pixel)
            bSum += Color.blue(pixel)
        }
        val count = samplePoints.size
        return Color.argb(
            238,
            (rSum.toFloat() / count).roundToInt(),
            (gSum.toFloat() / count).roundToInt(),
            (bSum.toFloat() / count).roundToInt()
        )
    }

    private fun pickContrastingTextColor(bgColor: Int): Int {
        val luminance = (
            0.299 * Color.red(bgColor) +
                0.587 * Color.green(bgColor) +
                0.114 * Color.blue(bgColor)
            ) / 255.0
        return if (luminance > 0.55) Color.rgb(24, 24, 27) else Color.WHITE
    }

    override fun close() {
        recognizers.values.forEach { it.close() }
        recognizers.clear()
    }
}
