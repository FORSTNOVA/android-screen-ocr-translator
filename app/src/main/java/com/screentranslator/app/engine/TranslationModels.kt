package com.screentranslator.app.engine

import android.graphics.Rect
import com.google.mlkit.nl.translate.TranslateLanguage

enum class OcrScript {
    LATIN,
    CHINESE,
    JAPANESE,
    KOREAN
}

enum class LanguageOption(
    val displayName: String,
    val mlKitTranslateCode: String,
    val ocrScript: OcrScript
) {
    ENGLISH("英语 (English)", TranslateLanguage.ENGLISH, OcrScript.LATIN),
    JAPANESE("日语 (日本語)", TranslateLanguage.JAPANESE, OcrScript.JAPANESE),
    KOREAN("韩语 (한국어)", TranslateLanguage.KOREAN, OcrScript.KOREAN),
    CHINESE("简体中文", TranslateLanguage.CHINESE, OcrScript.CHINESE)
}

data class OcrBlock(
    val text: String,
    val boundingBox: Rect,
    val bgColor: Int,
    val textColor: Int
)

data class TranslatedBlock(
    val originalText: String,
    val translatedText: String,
    val boundingBox: Rect,
    val bgColor: Int,
    val textColor: Int
)
