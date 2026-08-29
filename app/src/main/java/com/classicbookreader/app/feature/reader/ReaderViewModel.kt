package com.classicbookreader.app.feature.reader

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classicbookreader.app.core.cache.LruPageCache
import com.classicbookreader.app.core.cache.LruPageCache.PageKey
import com.classicbookreader.app.core.reader.prefetchWindow
import com.classicbookreader.app.core.selection.AnalysisCacheKey
import com.classicbookreader.app.core.selection.SelectionGeometry
import com.classicbookreader.app.core.selection.SelectionPoint
import com.classicbookreader.app.data.analysis.AnalysisEvent
import com.classicbookreader.app.data.analysis.AnalysisRepository
import com.classicbookreader.app.data.analysis.AnalysisRequest
import com.classicbookreader.app.data.analysis.AnalysisResult
import com.classicbookreader.app.data.analysis.WordAnalysis
import com.classicbookreader.app.data.pdf.PdfPageSource
import com.classicbookreader.app.data.pdf.PdfPageSourceFactory
import com.classicbookreader.app.data.prefs.UserPreferencesRepository
import com.classicbookreader.app.data.repository.BookRepository
import com.classicbookreader.app.data.repository.SavedWordRepository
import com.classicbookreader.app.ui.components.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
    private val sourceFactory: PdfPageSourceFactory,
    private val analysisRepository: AnalysisRepository,
    private val savedWordRepository: SavedWordRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    data class BookMeta(
        val id: Long,
        val title: String,
        val pageCount: Int,
        val initialPage: Int,
    )

    /** State machine of the circle-to-analyze flow (mockup screens 5-8). */
    sealed interface AiUiState {
        data object Off : AiUiState
        data object Selecting : AiUiState
        data object Preparing : AiUiState
        data class Streaming(val vocalizedText: String) : AiUiState
        data class Ready(val result: AnalysisResult, val fromCache: Boolean) : AiUiState
        data class Failed(val reason: AnalysisEvent.FailureReason) : AiUiState
    }

    data class ReaderState(
        val meta: UiState<BookMeta> = UiState.Loading,
        val currentPage: Int = 0,
        val ai: AiUiState = AiUiState.Off,
    )

    sealed interface ReaderEvent {
        data object WordSaved : ReaderEvent
        data object ReportAcknowledged : ReaderEvent
    }

    private val bookId: Long = checkNotNull(savedStateHandle["bookId"])

    private val stateFlow = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = stateFlow

    /** True while no backend URL is configured — the sheet shows a demo note. */
    val isDemoMode: StateFlow<Boolean> = preferences.backendBaseUrl
        .map { it.isBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val eventChannel = Channel<ReaderEvent>(Channel.BUFFERED)
    val events: Flow<ReaderEvent> = eventChannel.receiveAsFlow()

    private var source: PdfPageSource? = null
    private val cache = LruPageCache<Bitmap>(
        maxBytes = 40L * 1024 * 1024,
        sizeOf = { bitmap -> bitmap.allocationByteCount.toLong() },
    )
    private val renderLock = Mutex()
    private val inFlight = mutableMapOf<PageKey, Deferred<Bitmap?>>()
    private var saveJob: Job? = null
    private var analysisJob: Job? = null
    private var lastAnalysis: Pair<String, AnalysisRequest>? = null

    init {
        viewModelScope.launch {
            val book = repository.getBook(bookId)
            if (book == null) {
                stateFlow.update { it.copy(meta = UiState.Error("")) }
                return@launch
            }
            try {
                source = sourceFactory.open(File(book.filePath))
                val initialPage = book.lastReadPage.coerceIn(0, (book.pageCount - 1).coerceAtLeast(0))
                stateFlow.update {
                    it.copy(
                        meta = UiState.Content(
                            BookMeta(
                                id = book.id,
                                title = book.title,
                                pageCount = book.pageCount,
                                initialPage = initialPage,
                            ),
                        ),
                        currentPage = initialPage,
                    )
                }
            } catch (_: Exception) {
                stateFlow.update { it.copy(meta = UiState.Error("")) }
            }
        }
    }

    /** Cache-through render; kicks off prefetch of the surrounding window. */
    suspend fun pageBitmap(pageIndex: Int, targetWidthPx: Int): Bitmap? {
        val bitmap = renderPage(pageIndex, targetWidthPx) ?: return null
        prefetchAround(pageIndex, targetWidthPx)
        return bitmap
    }

    private suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap? {
        val document = source ?: return null
        if (pageIndex !in 0 until document.pageCount) return null
        val key = PageKey(pageIndex, targetWidthPx)

        val task = renderLock.withLock {
            cache.get(key)?.let { return it }
            inFlight.getOrPut(key) {
                viewModelScope.async {
                    try {
                        val bitmap = document.renderPage(pageIndex, targetWidthPx)
                        renderLock.withLock {
                            cache.put(key, bitmap)
                            inFlight.remove(key)
                        }
                        bitmap
                    } catch (_: Exception) {
                        renderLock.withLock { inFlight.remove(key) }
                        null
                    }
                }
            }
        }
        return task.await()
    }

    private fun prefetchAround(pageIndex: Int, targetWidthPx: Int) {
        val meta = stateFlow.value.meta
        if (meta !is UiState.Content) return
        prefetchWindow(pageIndex, meta.data.pageCount)
            .drop(1) // current page is already rendered
            .forEach { neighbour ->
                viewModelScope.launch { renderPage(neighbour, targetWidthPx) }
            }
    }

    fun onPageSettled(pageIndex: Int) {
        stateFlow.update { it.copy(currentPage = pageIndex) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            repository.saveReadingProgress(bookId, pageIndex)
        }
    }

    /** Unconditional save, called from the screen's ON_STOP observer. */
    fun flushProgress() {
        saveJob?.cancel()
        val page = stateFlow.value.currentPage
        viewModelScope.launch { repository.saveReadingProgress(bookId, page) }
    }

    // ---- Circle-to-analyze -------------------------------------------------

    fun enterAiMode() {
        stateFlow.update { it.copy(ai = AiUiState.Selecting) }
    }

    fun exitAiMode() {
        analysisJob?.cancel()
        stateFlow.update { it.copy(ai = AiUiState.Off) }
    }

    /** Sheet dismissed → back to selection so another circle can follow. */
    fun dismissAnalysis() {
        analysisJob?.cancel()
        stateFlow.update { it.copy(ai = AiUiState.Selecting) }
    }

    fun retryAnalysis() {
        val (cacheKey, request) = lastAnalysis ?: return
        runAnalysis(cacheKey, request)
    }

    /**
     * A finished circle gesture, in view coordinates: map to page fractions,
     * re-render the page hi-res, crop selection + context margin, then stream
     * the analysis.
     */
    fun onSelectionDrawn(points: List<SelectionPoint>, viewWidth: Float, viewHeight: Float) {
        val meta = stateFlow.value.meta
        if (meta !is UiState.Content || stateFlow.value.ai !is AiUiState.Selecting) return
        val page = stateFlow.value.currentPage

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            stateFlow.update { it.copy(ai = AiUiState.Preparing) }

            val pageBitmap = renderPage(page, ANALYSIS_RENDER_WIDTH_PX)
            if (pageBitmap == null) {
                stateFlow.update { it.copy(ai = AiUiState.Failed(AnalysisEvent.FailureReason.SERVER)) }
                return@launch
            }

            val selection = SelectionGeometry.normalizeSelection(
                points = points,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                imageWidth = pageBitmap.width.toFloat(),
                imageHeight = pageBitmap.height.toFloat(),
            )
            if (selection == null) {
                // Stroke never touched the page — stay in selection mode.
                stateFlow.update { it.copy(ai = AiUiState.Selecting) }
                return@launch
            }

            val request = withContext(Dispatchers.Default) {
                val aspect = pageBitmap.height.toFloat() / pageBitmap.width.toFloat()
                val cropRect = SelectionGeometry.expandWithContext(selection, pageAspectRatio = aspect)
                val cropPixels = SelectionGeometry.toPixelRect(cropRect, pageBitmap.width, pageBitmap.height)
                val cropped = Bitmap.createBitmap(
                    pageBitmap, cropPixels.left, cropPixels.top, cropPixels.width, cropPixels.height,
                )
                val jpeg = encodeJpeg(cropped)
                if (cropped !== pageBitmap) cropped.recycle()
                AnalysisRequest(
                    jpegImage = jpeg,
                    selectionBbox = SelectionGeometry.selectionWithinCrop(
                        selection = selection,
                        crop = cropRect,
                        cropWidthPx = cropPixels.width,
                        cropHeightPx = cropPixels.height,
                    ),
                    bookTitle = meta.data.title,
                    pageNumber = page + 1,
                )
            }

            val cacheKey = AnalysisCacheKey.forSelection(bookId, page, selection)
            lastAnalysis = cacheKey to request
            collectAnalysis(cacheKey, request)
        }
    }

    private fun runAnalysis(cacheKey: String, request: AnalysisRequest) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            stateFlow.update { it.copy(ai = AiUiState.Preparing) }
            collectAnalysis(cacheKey, request)
        }
    }

    private suspend fun collectAnalysis(cacheKey: String, request: AnalysisRequest) {
        analysisRepository.analyze(cacheKey, request).collect { event ->
            stateFlow.update { state ->
                state.copy(
                    ai = when (event) {
                        is AnalysisEvent.Partial -> AiUiState.Streaming(event.vocalizedText)
                        is AnalysisEvent.Complete -> AiUiState.Ready(event.result, event.fromCache)
                        is AnalysisEvent.Failed -> AiUiState.Failed(event.reason)
                    },
                )
            }
        }
    }

    fun saveWord(word: WordAnalysis) {
        val page = stateFlow.value.currentPage
        viewModelScope.launch {
            savedWordRepository.save(word, bookId = bookId, pageNumber = page + 1)
            eventChannel.send(ReaderEvent.WordSaved)
        }
    }

    /** Correction reports queue server-side in a later phase; acknowledge now. */
    fun reportAnalysis() {
        viewModelScope.launch { eventChannel.send(ReaderEvent.ReportAcknowledged) }
    }

    private fun encodeJpeg(bitmap: Bitmap, maxBytes: Int = MAX_JPEG_BYTES): ByteArray {
        var quality = 85
        while (true) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val bytes = stream.toByteArray()
            if (bytes.size <= maxBytes || quality <= 40) return bytes
            quality -= 15
        }
    }

    override fun onCleared() {
        // Snapshot: cancelled tasks mutate the map from their catch blocks.
        inFlight.values.toList().forEach { it.cancel() }
        source?.close()
        cache.clear()
        super.onCleared()
    }

    private companion object {
        /** ~200dpi on a typical kitab page; shares the render cache with the pager. */
        const val ANALYSIS_RENDER_WIDTH_PX = 1600
        const val MAX_JPEG_BYTES = 300_000
    }
}
