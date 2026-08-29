package com.classicbookreader.app.feature.savedwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classicbookreader.app.data.repository.SavedWordRepository
import com.classicbookreader.app.ui.components.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedWordsViewModel @Inject constructor(
    private val repository: SavedWordRepository,
) : ViewModel() {

    data class SavedWordUi(
        val id: Long,
        val vocalized: String,
        val gloss: String,
        val transliteration: String?,
        val pageNumber: Int?,
    )

    // Empty list stays Content; the screen renders its own empty card.
    val words: StateFlow<UiState<List<SavedWordUi>>> = repository.observeAll()
        .map { entities ->
            UiState.Content(
                entities.map { entity ->
                    SavedWordUi(
                        id = entity.id,
                        vocalized = entity.vocalizedText.ifBlank { entity.arabicText },
                        gloss = entity.gloss,
                        transliteration = entity.transliteration,
                        pageNumber = entity.pageNumber,
                    )
                },
            ) as UiState<List<SavedWordUi>>
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
