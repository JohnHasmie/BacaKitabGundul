package com.classicbookreader.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classicbookreader.app.data.repository.BookRepository
import com.classicbookreader.app.data.repository.SavedWordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: BookRepository,
    savedWordRepository: SavedWordRepository,
) : ViewModel() {

    val savedWordCount: StateFlow<Int> = savedWordRepository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    data class ContinueReadingUi(
        val bookId: Long,
        val title: String,
        val coverPath: String?,
        val lastReadPage: Int,
        val pageCount: Int,
    ) {
        val progress: Float
            get() = if (pageCount <= 0) 0f else (lastReadPage + 1f) / pageCount
    }

    val continueReading: StateFlow<ContinueReadingUi?> = repository.observeContinueReading()
        .map { entity ->
            entity?.let {
                ContinueReadingUi(
                    bookId = it.id,
                    title = it.title,
                    coverPath = it.coverPath,
                    lastReadPage = it.lastReadPage,
                    pageCount = it.pageCount,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
