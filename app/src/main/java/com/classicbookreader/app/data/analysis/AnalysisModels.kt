package com.classicbookreader.app.data.analysis

import com.classicbookreader.app.core.selection.PixelRect
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Response models for POST /v1/analyze (plan §5). Every field defaults so a
 * partially-filled or older backend payload still deserializes.
 */
@Serializable
data class WordIrab(
    val role: String = "",
    val reasoning: String = "",
    val caseMarker: String = "",
)

@Serializable
data class WordSarf(
    val root: String = "",
    val pattern: String = "",
    val form: String = "",
)

@Serializable
data class WordAnalysis(
    val arabic: String = "",
    val vocalized: String = "",
    val transliteration: String = "",
    val gloss: String = "",
    val irab: WordIrab = WordIrab(),
    val sarf: WordSarf = WordSarf(),
)

@Serializable
data class AnalysisResult(
    val selectedText: String = "",
    val vocalizedText: String = "",
    val transliteration: String = "",
    val contextBefore: String = "",
    val contextAfter: String = "",
    val words: List<WordAnalysis> = emptyList(),
    val phraseGloss: String = "",
    val confidence: Float = 0f,
)

/** What the reader hands to the analysis pipeline. */
data class AnalysisRequest(
    val jpegImage: ByteArray,
    val selectionBbox: PixelRect,
    val bookTitle: String,
    val pageNumber: Int,
)

sealed interface AnalysisEvent {
    /** Early harakat: shown as soon as the first chunk arrives. */
    data class Partial(val vocalizedText: String) : AnalysisEvent

    data class Complete(val result: AnalysisResult, val fromCache: Boolean = false) : AnalysisEvent

    data class Failed(val reason: FailureReason) : AnalysisEvent

    enum class FailureReason { NETWORK, SERVER }
}

/** One way of producing an analysis (remote backend, demo, …). */
interface AnalysisSource {
    fun analyze(request: AnalysisRequest): Flow<AnalysisEvent>
}
