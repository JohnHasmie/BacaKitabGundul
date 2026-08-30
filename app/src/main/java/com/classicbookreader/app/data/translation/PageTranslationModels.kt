package com.classicbookreader.app.data.translation

import com.classicbookreader.app.data.analysis.AnalysisEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Response models for POST /v1/page-translate (plan §5, §Fase 3). Every
 * field defaults so a partial or older backend payload still deserializes.
 * The bbox is advisory — its consumer is the tap-word-to-analyze feature,
 * not the interlinear layout (which follows line/word order).
 */
@Serializable
data class WordBox(
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 0f,
    val h: Float = 0f,
)

@Serializable
data class TranslatedWord(
    val arabic: String = "",
    val gloss: String = "",
    val bbox: WordBox = WordBox(),
)

@Serializable
data class TranslationLine(
    val words: List<TranslatedWord> = emptyList(),
)

@Serializable
data class PageTranslation(
    val lines: List<TranslationLine> = emptyList(),
    val confidence: Float = 0f,
)

/** What the reader hands to the translation pipeline. */
data class PageTranslationRequest(
    val jpegImage: ByteArray,
    val bookTitle: String,
    val pageNumber: Int,
)

sealed interface PageTranslationEvent {
    /** Words emitted so far — progress feedback for a 20-60s generation. */
    data class Progress(val wordCount: Int) : PageTranslationEvent

    data class Complete(
        val result: PageTranslation,
        val fromCache: Boolean = false,
    ) : PageTranslationEvent

    data class Failed(val reason: AnalysisEvent.FailureReason) : PageTranslationEvent
}

/** One way of producing a page translation (remote backend, demo, …). */
interface PageTranslationSource {
    fun translate(request: PageTranslationRequest): Flow<PageTranslationEvent>
}
