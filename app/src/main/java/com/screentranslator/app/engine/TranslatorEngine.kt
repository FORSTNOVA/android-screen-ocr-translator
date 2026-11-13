package com.screentranslator.app.engine

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

interface TranslatorEngine {
    suspend fun isModelDownloaded(language: LanguageOption): Boolean
    suspend fun ensureModelReady(source: LanguageOption, target: LanguageOption)
    suspend fun translateBlocks(
        blocks: List<OcrBlock>,
        source: LanguageOption,
        target: LanguageOption
    ): List<TranslatedBlock>
    fun close()
}

class MlKitTranslatorEngine : TranslatorEngine {

    private var activePair: Pair<LanguageOption, LanguageOption>? = null
    private var activeTranslator: Translator? = null

    private fun getOrCreateTranslator(source: LanguageOption, target: LanguageOption): Translator {
        val pair = source to target
        if (activePair == pair && activeTranslator != null) {
            return activeTranslator!!
        }
        activeTranslator?.close()
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source.mlKitTranslateCode)
            .setTargetLanguage(target.mlKitTranslateCode)
            .build()
        return Translation.getClient(options).also {
            activePair = pair
            activeTranslator = it
        }
    }

    override suspend fun isModelDownloaded(language: LanguageOption): Boolean {
        val model = TranslateRemoteModel.Builder(language.mlKitTranslateCode).build()
        return RemoteModelManager.getInstance().isModelDownloaded(model).await()
    }

    override suspend fun ensureModelReady(source: LanguageOption, target: LanguageOption) {
        if (source == target) return
        val translator = getOrCreateTranslator(source, target)
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions).await()
    }

    override suspend fun translateBlocks(
        blocks: List<OcrBlock>,
        source: LanguageOption,
        target: LanguageOption
    ): List<TranslatedBlock> {
        if (blocks.isEmpty()) return emptyList()
        if (source == target) {
            return blocks.map {
                TranslatedBlock(
                    originalText = it.text,
                    translatedText = it.text,
                    boundingBox = it.boundingBox,
                    bgColor = it.bgColor,
                    textColor = it.textColor
                )
            }
        }

        val translator = getOrCreateTranslator(source, target)
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions).await()

        return coroutineScope {
            blocks.map { block ->
                async {
                    val translated = runCatching {
                        translator.translate(block.text).await()
                    }.getOrDefault(block.text)
                    TranslatedBlock(
                        originalText = block.text,
                        translatedText = translated,
                        boundingBox = block.boundingBox,
                        bgColor = block.bgColor,
                        textColor = block.textColor
                    )
                }
            }.awaitAll()
        }
    }

    override fun close() {
        activeTranslator?.close()
        activeTranslator = null
        activePair = null
    }
}
