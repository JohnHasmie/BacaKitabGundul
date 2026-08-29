package com.classicbookreader.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")

    val onboardingCompleted: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[onboardingCompletedKey] ?: false }

    suspend fun setOnboardingCompleted() {
        dataStore.edit { preferences -> preferences[onboardingCompletedKey] = true }
    }

    private val backendBaseUrlKey = stringPreferencesKey("backend_base_url")

    /** Blank = no backend configured; the analysis pipeline runs in demo mode. */
    val backendBaseUrl: Flow<String> =
        dataStore.data.map { preferences -> preferences[backendBaseUrlKey]?.trim().orEmpty() }

    suspend fun setBackendBaseUrl(value: String) {
        dataStore.edit { preferences -> preferences[backendBaseUrlKey] = value.trim() }
    }
}
