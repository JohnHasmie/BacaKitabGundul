package com.classicbookreader.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classicbookreader.app.R
import com.classicbookreader.app.feature.settings.SettingsViewModel.SettingsEvent
import com.classicbookreader.app.ui.components.GlassCard
import com.classicbookreader.app.ui.components.PillButton
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Sizes
import com.classicbookreader.app.ui.theme.Spacing

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val storedUrl by viewModel.backendBaseUrl.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var urlField by remember(storedUrl) { mutableStateOf(storedUrl) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.Saved ->
                    snackbarHostState.showSnackbar(context.getString(R.string.settings_saved))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(
                        text = stringResource(R.string.settings_ai_server_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.settings_ai_server_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.glass.inkSecondary,
                    )
                    OutlinedTextField(
                        value = urlField,
                        onValueChange = { urlField = it },
                        placeholder = { Text(stringResource(R.string.settings_ai_server_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PillButton(
                        text = stringResource(R.string.action_save),
                        onClick = { viewModel.saveBackendBaseUrl(urlField) },
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Sizes.dockClearance),
        )
    }
}
