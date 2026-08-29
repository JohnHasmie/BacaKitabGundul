package com.classicbookreader.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classicbookreader.app.data.prefs.UserPreferencesRepository
import com.classicbookreader.app.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    preferences: UserPreferencesRepository,
) : ViewModel() {

    /** Null while the preference loads; the themed background shows meanwhile. */
    val startDestination: StateFlow<String?> = preferences.onboardingCompleted
        .take(1)
        .map { completed -> if (completed) Routes.HOME else Routes.ONBOARDING }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
