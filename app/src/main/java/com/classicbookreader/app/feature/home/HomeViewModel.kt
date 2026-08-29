package com.classicbookreader.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classicbookreader.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: BookRepository,
) : ViewModel() {

    data class ContinueReadingUi(
        val bookId: Long,
        val title: String,
        val coverPath: String?,
        val lastReadPage: Int,
        val pageCount: Int,
    ) {
        val progress: Float
            get() = if (pageCount <= 0) 0f else (lastReadPage + 1f) / pageCount
        val progressPercent: Int
            get() = (progress * 100).roundToInt()
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
