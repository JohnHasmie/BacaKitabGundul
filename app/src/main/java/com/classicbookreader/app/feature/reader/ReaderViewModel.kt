package com.classicbookreader.app.feature.reader

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classicbookreader.app.core.cache.LruPageCache
import com.classicbookreader.app.core.cache.LruPageCache.PageKey
import com.classicbookreader.app.core.reader.prefetchWindow
import com.classicbookreader.app.core.selection.AnalysisCacheKey
import com.classicbookreader.app.core.selection.NormalizedRect
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
import com.classicbookreader.app.core.util.encodeJpeg
import com.classicbookreader.app.data.repository.BookRepository
import com.classicbookreader.app.data.repository.SavedWordRepository
import com.classicbookreader.app.data.translation.PageTranslation
import com.classicbookreader.app.data.translation.PageTranslationEvent
import com.classicbookreader.app.data.translation.PageTranslationRepository
import com.classicbookreader.app.data.translation.PageTranslationRequest
import com.classicbookreader.app.data.translation.TranslatedWord
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
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
    private val sourceFactory: PdfPageSourceFactory,
    private val analysisRepository: AnalysisRepository,
    private val savedWordRepository: SavedWordRepository,
    private val translationRepository: PageTranslationRepository,
    private val preferences: UserPreferencesRepository,
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

    /** Per-page state of the interlinear view (mockup screen 9). */
    sealed interface TranslationUiState {
        data object Idle : TranslationUiState
        data class Loading(val wordCount: Int) : TranslationUiState
        data class Ready(val translation: PageTranslation, val fromCache: Boolean) : TranslationUiState
        data class Failed(val reason: AnalysisEvent.FailureReason) : TranslationUiState
    }

    data class ReaderState(
        val meta: UiState<BookMeta> = UiState.Loading,
        val currentPage: Int = 0,
        val ai: AiUiState = AiUiState.Off,
        val translationMode: Boolean = false,
        // Keyed per page: the pager composes neighbors, so a single
        // "current page" state would paint the wrong page's words.
        val translations: Map<Int, TranslationUiState> = emptyMap(),
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

    /** Font scale for the interlinear view, persisted in DataStore. */
    val translationTextScale: StateFlow<Float> = preferences.translationTextScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

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
    private val translationJobs = mutableMapOf<Int, Job>()

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
        stateFlow.update { state ->
            state.copy(
                currentPage = pageIndex,
                // Prune Ready results outside the pager window: they rehydrate
                // from Room instantly, and a 300-page kitab must not pile up
                // 300 parsed translations in the state.
                translations = state.translations.filterKeys { page ->
                    kotlin.math.abs(page - pageIndex) <= 2 ||
                        state.translations[page] !is TranslationUiState.Ready
                },
            )
        }
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

    // ---- Interlinear translation (mockup screen 9) -------------------------

    fun toggleTranslationMode() {
        analysisJob?.cancel()
        // Translate mode and AI circle mode are mutually exclusive: the lasso
        // maps onto the page bitmap, which the chips view replaces.
        stateFlow.update { it.copy(translationMode = !it.translationMode, ai = AiUiState.Off) }
    }

    /** Cheap Room lookup on page composition — shows cached pages instantly. */
    fun loadCachedTranslation(pageIndex: Int) {
        if (stateFlow.value.translations[pageIndex] != null) return
        viewModelScope.launch {
            val cached = translationRepository.getCached(bookId, pageIndex + 1) ?: return@launch
            updateTranslation(pageIndex, TranslationUiState.Ready(cached, fromCache = true))
        }
    }

    /** Explicit per-page action ("Terjemahkan halaman ini") — cost control. */
    fun translatePage(pageIndex: Int) {
        val meta = stateFlow.value.meta
        if (meta !is UiState.Content) return
        if (translationJobs[pageIndex]?.isActive == true) return
        // Only one paid translation at a time.
        if (translationJobs.values.any { it.isActive }) return

        translationJobs[pageIndex] = viewModelScope.launch {
            updateTranslation(pageIndex, TranslationUiState.Loading(0))

            // One-shot hi-res render, deliberately NOT through the pager cache.
            val pageBitmap = try {
                source?.renderPage(pageIndex, ANALYSIS_RENDER_WIDTH_PX)
            } catch (_: Exception) {
                null
            }
            if (pageBitmap == null) {
                updateTranslation(
                    pageIndex,
                    TranslationUiState.Failed(AnalysisEvent.FailureReason.SERVER),
                )
                return@launch
            }

            val jpeg = withContext(Dispatchers.Default) {
                encodeJpeg(pageBitmap, maxBytes = PAGE_JPEG_BYTES).also {
                    pageBitmap.recycle() // one-shot render, not cached anywhere
                }
            }

            val request = PageTranslationRequest(
                jpegImage = jpeg,
                bookTitle = meta.data.title,
                pageNumber = pageIndex + 1,
            )
            translationRepository.translate(bookId, pageIndex + 1, request).collect { event ->
                updateTranslation(
                    pageIndex,
                    when (event) {
                        is PageTranslationEvent.Progress ->
                            TranslationUiState.Loading(event.wordCount)
                        is PageTranslationEvent.Complete ->
                            TranslationUiState.Ready(event.result, event.fromCache)
                        is PageTranslationEvent.Failed ->
                            TranslationUiState.Failed(event.reason)
                    },
                )
            }
        }
    }

    /** "Terjemahkan ulang": drop the cached page and request a fresh one. */
    fun retranslatePage(pageIndex: Int) {
        if (translationJobs.values.any { it.isActive }) return
        viewModelScope.launch {
            translationRepository.invalidate(bookId, pageIndex + 1)
            updateTranslation(pageIndex, TranslationUiState.Idle)
            translatePage(pageIndex)
        }
    }

    fun setTranslationTextScale(value: Float) {
        viewModelScope.launch { preferences.setTranslationTextScale(value) }
    }

    private fun updateTranslation(pageIndex: Int, state: TranslationUiState) {
        stateFlow.update { it.copy(translations = it.translations + (pageIndex to state)) }
    }

    // ---- Circle-to-analyze -------------------------------------------------

    fun enterAiMode() {
        stateFlow.update { it.copy(ai = AiUiState.Selecting, translationMode = false) }
    }

    fun exitAiMode() {
        analysisJob?.cancel()
        stateFlow.update { it.copy(ai = AiUiState.Off) }
    }

    /**
     * Sheet dismissed → back to selection so another circle can follow, or
     * fully off when the analysis came from a translate-mode word tap.
     */
    fun dismissAnalysis() {
        analysisJob?.cancel()
        stateFlow.update {
            it.copy(ai = if (it.translationMode) AiUiState.Off else AiUiState.Selecting)
        }
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

            // One-shot hi-res render, deliberately NOT through the pager cache:
            // a 1600px page would evict the on-screen bitmaps from the budget.
            val pageBitmap = try {
                source?.renderPage(page, ANALYSIS_RENDER_WIDTH_PX)
            } catch (_: Exception) {
                null
            }
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
                pageBitmap.recycle()
                stateFlow.update { it.copy(ai = AiUiState.Selecting) }
                return@launch
            }
            analyzeRegion(meta.data, page, pageBitmap, selection)
        }
    }

    /**
     * Translate-mode entry point: tapping a word chip analyzes it via the
     * backend's advisory bbox. Words without a usable bbox fall back to a
     * page-center region (harmless in demo mode, whose result is fixed).
     */
    fun analyzeTranslatedWord(pageIndex: Int, word: TranslatedWord) {
        val meta = stateFlow.value.meta
        if (meta !is UiState.Content) return
        val box = word.bbox
        val selection = if (box.w > 0f && box.h > 0f) {
            NormalizedRect(
                left = box.x.coerceIn(0f, 1f),
                top = box.y.coerceIn(0f, 1f),
                right = (box.x + box.w).coerceIn(0f, 1f),
                bottom = (box.y + box.h).coerceIn(0f, 1f),
            )
        } else {
            NormalizedRect(0.4f, 0.45f, 0.6f, 0.55f)
        }

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            stateFlow.update { it.copy(ai = AiUiState.Preparing) }
            val pageBitmap = try {
                source?.renderPage(pageIndex, ANALYSIS_RENDER_WIDTH_PX)
            } catch (_: Exception) {
                null
            }
            if (pageBitmap == null) {
                stateFlow.update { it.copy(ai = AiUiState.Failed(AnalysisEvent.FailureReason.SERVER)) }
                return@launch
            }
            analyzeRegion(meta.data, pageIndex, pageBitmap, selection)
        }
    }

    /** Shared tail of both analysis entry points: crop → JPEG → stream. */
    private suspend fun analyzeRegion(
        meta: BookMeta,
        page: Int,
        pageBitmap: Bitmap,
        selection: NormalizedRect,
    ) {
        val request = withContext(Dispatchers.Default) {
            val aspect = pageBitmap.height.toFloat() / pageBitmap.width.toFloat()
            val cropRect = SelectionGeometry.expandWithContext(selection, pageAspectRatio = aspect)
            val cropPixels = SelectionGeometry.toPixelRect(cropRect, pageBitmap.width, pageBitmap.height)
            val cropped = Bitmap.createBitmap(
                pageBitmap, cropPixels.left, cropPixels.top, cropPixels.width, cropPixels.height,
            )
            val jpeg = encodeJpeg(cropped, maxBytes = MAX_JPEG_BYTES)
            if (cropped !== pageBitmap) cropped.recycle()
            pageBitmap.recycle() // one-shot render, not cached anywhere
            AnalysisRequest(
                jpegImage = jpeg,
                selectionBbox = SelectionGeometry.selectionWithinCrop(
                    selection = selection,
                    crop = cropRect,
                    cropWidthPx = cropPixels.width,
                    cropHeightPx = cropPixels.height,
                ),
                bookTitle = meta.title,
                pageNumber = page + 1,
            )
        }

        val cacheKey = AnalysisCacheKey.forSelection(bookId, page, selection)
        lastAnalysis = cacheKey to request
        collectAnalysis(cacheKey, request)
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

    override fun onCleared() {
        // Snapshot: cancelled tasks mutate the map from their catch blocks.
        inFlight.values.toList().forEach { it.cancel() }
        source?.close()
        cache.clear()
        super.onCleared()
    }

    private companion object {
        /** ~200dpi on a typical kitab page. */
        const val ANALYSIS_RENDER_WIDTH_PX = 1600
        const val MAX_JPEG_BYTES = 300_000
        const val PAGE_JPEG_BYTES = 500_000
    }
}
