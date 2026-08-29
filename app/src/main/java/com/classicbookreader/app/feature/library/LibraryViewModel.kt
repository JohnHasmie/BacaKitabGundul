package com.classicbookreader.app.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classicbookreader.app.R
import com.classicbookreader.app.data.import_.PdfImporter
import com.classicbookreader.app.data.repository.BookRepository
import com.classicbookreader.app.ui.components.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: BookRepository,
) : ViewModel() {

    data class BookUi(
        val id: Long,
        val title: String,
        val coverPath: String?,
        val pageCount: Int,
        val lastReadPage: Int,
    ) {
        val progress: Float
            get() = if (pageCount <= 0) 0f else (lastReadPage + 1f) / pageCount
    }

    sealed interface ImportState {
        data object Idle : ImportState
        data object Importing : ImportState
    }

    sealed interface LibraryEvent {
        data class ImportSucceeded(val bookId: Long) : LibraryEvent
        data class ImportFailed(val messageResId: Int) : LibraryEvent
    }

    // Empty list stays a Content state; the screen renders its own
    // mockup-styled empty card (copy lives in strings.xml).
    val books: StateFlow<UiState<List<BookUi>>> = repository.observeBooks()
        .map { entities ->
            UiState.Content(
                entities.map { entity ->
                    BookUi(
                        id = entity.id,
                        title = entity.title,
                        coverPath = entity.coverPath,
                        pageCount = entity.pageCount,
                        lastReadPage = entity.lastReadPage,
                    )
                },
            ) as UiState<List<BookUi>>
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val importStateFlow = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = importStateFlow

    private val eventChannel = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = eventChannel.receiveAsFlow()

    /** Called from the SAF launcher; [fallbackTitle] is the localized default. */
    fun onPdfPicked(uri: Uri?, fallbackTitle: String) {
        if (uri == null) return
        viewModelScope.launch {
            importStateFlow.value = ImportState.Importing
            val result = repository.importPdf(uri, fallbackTitle)
            importStateFlow.value = ImportState.Idle
            eventChannel.send(
                when (result) {
                    is PdfImporter.Result.Success -> LibraryEvent.ImportSucceeded(result.bookId)
                    is PdfImporter.Result.Failure -> LibraryEvent.ImportFailed(
                        when (result.reason) {
                            PdfImporter.FailureReason.PROTECTED_PDF -> R.string.import_error_protected
                            PdfImporter.FailureReason.COPY_FAILED,
                            PdfImporter.FailureReason.INVALID_PDF,
                            -> R.string.import_error_generic
                        },
                    )
                },
            )
        }
    }
}
