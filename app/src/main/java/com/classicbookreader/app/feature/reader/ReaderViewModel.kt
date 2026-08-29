package com.classicbookreader.app.feature.reader

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classicbookreader.app.core.cache.LruPageCache
import com.classicbookreader.app.core.cache.LruPageCache.PageKey
import com.classicbookreader.app.core.reader.prefetchWindow
import com.classicbookreader.app.data.pdf.PdfPageSource
import com.classicbookreader.app.data.pdf.PdfPageSourceFactory
import com.classicbookreader.app.data.repository.BookRepository
import com.classicbookreader.app.ui.components.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
    private val sourceFactory: PdfPageSourceFactory,
) : ViewModel() {

    data class BookMeta(
        val id: Long,
        val title: String,
        val pageCount: Int,
        val initialPage: Int,
    )

    data class ReaderState(
        val meta: UiState<BookMeta> = UiState.Loading,
        val currentPage: Int = 0,
    )

    sealed interface ReaderEvent {
        data object AiComingSoon : ReaderEvent
    }

    private val bookId: Long = checkNotNull(savedStateHandle["bookId"])

    private val stateFlow = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = stateFlow

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

    fun onCircleAiClicked() {
        viewModelScope.launch { eventChannel.send(ReaderEvent.AiComingSoon) }
    }

    override fun onCleared() {
        // Snapshot: cancelled tasks mutate the map from their catch blocks.
        inFlight.values.toList().forEach { it.cancel() }
        source?.close()
        cache.clear()
        super.onCleared()
    }
}
