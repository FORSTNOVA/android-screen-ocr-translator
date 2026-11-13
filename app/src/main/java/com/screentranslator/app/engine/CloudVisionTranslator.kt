package com.screentranslator.app.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

class CloudVisionTranslator {

    suspend fun fetchAvailableModels(
        baseUrl: String,
        apiKey: String
    ): List<String> = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        require(cleanBase.isNotBlank()) { "请先填写 Base URL" }
        val modelsUrl = buildModelsUrl(cleanBase)

        val conn = (URL(modelsUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            }
        }

        val responseText = try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${body.take(200)}")
            }
            body
        } finally {
            conn.disconnect()
        }

        parseModelIds(responseText)
    }

    private fun buildModelsUrl(cleanBase: String): String {
        val normalized = if (cleanBase.endsWith("/chat/completions")) {
            cleanBase.removeSuffix("/chat/completions")
        } else {
            cleanBase
        }
        return if (normalized.endsWith("/models")) normalized else "$normalized/models"
    }

    private fun parseModelIds(jsonText: String): List<String> {
        val trimmed = jsonText.trim()
        val ids = mutableListOf<String>()
        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            val dataArray = root.optJSONArray("data") ?: root.optJSONArray("models")
            if (dataArray != null) {
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.opt(i)
                    when (item) {
                        is JSONObject -> {
                            val id = item.optString("id").ifBlank { item.optString("name") }.trim()
                            if (id.isNotBlank()) ids += id
                        }
                        is String -> if (item.isNotBlank()) ids += item.trim()
                    }
                }
            }
        } else if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                val id = arr.optString(i).trim()
                if (id.isNotBlank()) ids += id
            }
        }
        return ids.distinct().sorted()
    }

    suspend fun recognizeAndTranslate(
        bitmap: Bitmap,
        sourceLanguage: LanguageOption,
        targetLanguage: LanguageOption,
        config: CloudConfig
    ): List<TranslatedBlock> = withContext(Dispatchers.IO) {
        require(config.apiKey.isNotBlank()) { "请先在主界面填写云端 API Key" }

        val scaledBitmap = scaleBitmapIfNeeded(bitmap, maxSide = 1280)
        val base64Image = try {
            bitmapToBase64Jpeg(scaledBitmap)
        } finally {
            if (scaledBitmap !== bitmap) {
                scaledBitmap.recycle()
            }
        }

        val endpoint = buildChatCompletionsUrl(config.baseUrl)
        val prompt = """
            你是一个手机屏幕 OCR 与翻译引擎。请识别图片中的所有可读文字（源语言参考：${sourceLanguage.displayName}），并将其准确翻译为${targetLanguage.displayName}。
            请按段落合并同一语义区域的文字，严格只返回 JSON 数组（不要输出任何 Markdown 代码块或额外解释），格式如下：
            [{"box":[ymin,xmin,ymax,xmax],"original":"原文","translated":"译文"}]
            其中 box 为 0 到 1000 的归一化整数坐标（ymin=上边界, xmin=左边界, ymax=下边界, xmax=右边界）。
        """.trimIndent()

        val payload = JSONObject().apply {
            put("model", config.model.ifBlank { "gpt-4o-mini" })
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64Image")
                            })
                        })
                    })
                })
            })
        }

        val responseText = postJson(endpoint, config.apiKey, payload.toString())
        val content = JSONObject(responseText)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")

        parseResponseToBlocks(content, bitmap.width, bitmap.height)
    }

    /**
     * 纯文本回退方案：当所选云端模型不支持 image_url 识图时，由本地 ML Kit OCR 提取文字与坐标，再交由大模型翻译。
     */
    suspend fun translateTextBlocksWithLlm(
        blocks: List<OcrBlock>,
        sourceLanguage: LanguageOption,
        targetLanguage: LanguageOption,
        config: CloudConfig
    ): List<TranslatedBlock> = withContext(Dispatchers.IO) {
        if (blocks.isEmpty()) return@withContext emptyList()
        require(config.apiKey.isNotBlank()) { "请先在主界面填写云端 API Key" }

        val inputArray = JSONArray().apply {
            blocks.forEachIndexed { index, block ->
                put(JSONObject().apply {
                    put("id", index)
                    put("text", block.text)
                })
            }
        }

        val prompt = """
            请将以下 JSON 数组中的每一条文本从${sourceLanguage.displayName}翻译为${targetLanguage.displayName}。
            严格只返回 JSON 数组（不要输出任何 Markdown 代码块或额外解释），保持相同的 id 与顺序，格式如下：
            [{"id":0,"translated":"译文"}]
            待翻译内容：
            $inputArray
        """.trimIndent()

        val payload = JSONObject().apply {
            put("model", config.model.ifBlank { "gpt-4o-mini" })
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val endpoint = buildChatCompletionsUrl(config.baseUrl)
        val responseText = postJson(endpoint, config.apiKey, payload.toString())
        val content = JSONObject(responseText)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")

        val cleaned = content
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val translatedById = mutableMapOf<Int, String>()
        runCatching {
            val start = cleaned.indexOf('[')
            val end = cleaned.lastIndexOf(']')
            if (start >= 0 && end > start) {
                val arr = JSONArray(cleaned.substring(start, end + 1))
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optInt("id", i)
                    val text = obj.optString("translated").trim()
                    if (text.isNotBlank()) translatedById[id] = text
                }
            }
        }

        blocks.mapIndexed { index, block ->
            TranslatedBlock(
                originalText = block.text,
                translatedText = translatedById[index] ?: block.text,
                boundingBox = block.boundingBox,
                bgColor = block.bgColor,
                textColor = block.textColor
            )
        }
    }

    private fun parseResponseToBlocks(
        rawContent: String,
        imageWidth: Int,
        imageHeight: Int
    ): List<TranslatedBlock> {
        val cleaned = rawContent
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonArray = runCatching {
            val start = cleaned.indexOf('[')
            val end = cleaned.lastIndexOf(']')
            if (start >= 0 && end > start) {
                JSONArray(cleaned.substring(start, end + 1))
            } else {
                null
            }
        }.getOrNull()

        if (jsonArray != null && jsonArray.length() > 0) {
            val result = mutableListOf<TranslatedBlock>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val translated = obj.optString("translated").trim()
                if (translated.isBlank()) continue
                val original = obj.optString("original").trim()
                val boxArr = obj.optJSONArray("box")
                val rect = if (boxArr != null && boxArr.length() == 4) {
                    val ymin = (boxArr.optDouble(0, 0.0) / 1000.0 * imageHeight).roundToInt().coerceIn(0, imageHeight)
                    val xmin = (boxArr.optDouble(1, 0.0) / 1000.0 * imageWidth).roundToInt().coerceIn(0, imageWidth)
                    val ymax = (boxArr.optDouble(2, 1000.0) / 1000.0 * imageHeight).roundToInt().coerceIn(0, imageHeight)
                    val xmax = (boxArr.optDouble(3, 1000.0) / 1000.0 * imageWidth).roundToInt().coerceIn(0, imageWidth)
                    Rect(xmin, ymin, max(xmin + 24, xmax), max(ymin + 24, ymax))
                } else {
                    Rect(16, 16, (imageWidth - 16).coerceAtLeast(64), (imageHeight - 16).coerceAtLeast(64))
                }

                result += TranslatedBlock(
                    originalText = original,
                    translatedText = translated,
                    boundingBox = rect,
                    bgColor = Color.argb(232, 18, 24, 38),
                    textColor = Color.WHITE
                )
            }
            if (result.isNotEmpty()) return result
        }

        // 若模型未按 JSON 返回，则降级为整块显示
        if (cleaned.isBlank()) return emptyList()
        return listOf(
            TranslatedBlock(
                originalText = "",
                translatedText = cleaned,
                boundingBox = Rect(
                    (imageWidth * 0.06f).roundToInt(),
                    (imageHeight * 0.15f).roundToInt(),
                    (imageWidth * 0.94f).roundToInt(),
                    (imageHeight * 0.85f).roundToInt()
                ),
                bgColor = Color.argb(235, 18, 24, 38),
                textColor = Color.WHITE
            )
        )
    }

    private fun buildChatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    private fun postJson(urlStr: String, apiKey: String, jsonBody: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        try {
            conn.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                Log.e("CloudVisionTranslator", "HTTP $code from $urlStr: $body")
                val conciseMsg = extractErrorMessage(body)
                throw IllegalStateException("HTTP $code: $conciseMsg")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun extractErrorMessage(body: String): String {
        val parsed = runCatching {
            val root = JSONObject(body)
            root.optJSONObject("error")?.optString("message")
                ?.ifBlank { root.optString("message") }
                ?: root.optString("message")
        }.getOrNull()?.trim()
        return if (!parsed.isNullOrBlank()) parsed.take(180) else body.take(180)
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxSide: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / largest
        val newW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val newH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
