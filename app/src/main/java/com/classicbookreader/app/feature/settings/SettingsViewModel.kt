package com.classicbookreader.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classicbookreader.app.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    sealed interface SettingsEvent {
        data object Saved : SettingsEvent
    }

    val backendBaseUrl: StateFlow<String> = preferences.backendBaseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    fun saveBackendBaseUrl(value: String) {
        viewModelScope.launch {
            preferences.setBackendBaseUrl(value)
            eventChannel.send(SettingsEvent.Saved)
        }
    }
}
