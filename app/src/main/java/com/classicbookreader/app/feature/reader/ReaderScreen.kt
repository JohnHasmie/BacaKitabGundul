package com.classicbookreader.app.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classicbookreader.app.R
import com.classicbookreader.app.core.selection.SelectionPoint
import com.classicbookreader.app.data.translation.TranslatedWord
import com.classicbookreader.app.feature.reader.ReaderViewModel.AiUiState
import com.classicbookreader.app.feature.reader.ReaderViewModel.ReaderEvent
import com.classicbookreader.app.ui.components.GlassCard
import com.classicbookreader.app.ui.components.PillButton
import com.classicbookreader.app.ui.components.ProgressRing
import com.classicbookreader.app.ui.components.UiState
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Radius
import com.classicbookreader.app.ui.theme.Spacing
import kotlin.math.min

private const val MAX_RENDER_WIDTH_PX = 1440

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()
    val translationTextScale by viewModel.translationTextScale.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushProgress()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ReaderEvent.WordSaved ->
                    snackbarHostState.showSnackbar(context.getString(R.string.ai_word_saved))
                ReaderEvent.ReportAcknowledged ->
                    snackbarHostState.showSnackbar(context.getString(R.string.ai_report_thanks))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val meta = state.meta) {
            is UiState.Loading, is UiState.Empty ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

            is UiState.Error -> ReaderError(onBack = onBack)

            is UiState.Content -> ReaderContent(
                meta = meta.data,
                currentPage = state.currentPage,
                ai = state.ai,
                translationMode = state.translationMode,
                translations = state.translations,
                translationTextScale = translationTextScale,
                pageBitmap = viewModel::pageBitmap,
                onPageSettled = viewModel::onPageSettled,
                onEnterAiMode = viewModel::enterAiMode,
                onExitAiMode = viewModel::exitAiMode,
                onStrokeFinished = viewModel::onSelectionDrawn,
                onToggleTranslate = viewModel::toggleTranslationMode,
                onLoadCachedTranslation = viewModel::loadCachedTranslation,
                onTranslatePage = viewModel::translatePage,
                onRetranslatePage = viewModel::retranslatePage,
                onWordTap = viewModel::analyzeTranslatedWord,
                onTextScaleChanged = viewModel::setTranslationTextScale,
                onBack = onBack,
            )
        }

        when (state.ai) {
            is AiUiState.Preparing, is AiUiState.Streaming,
            is AiUiState.Ready, is AiUiState.Failed,
            -> AnalysisSheet(
                ai = state.ai,
                isDemoMode = isDemoMode,
                onDismiss = viewModel::dismissAnalysis,
                onRetry = viewModel::retryAnalysis,
                onSaveWord = viewModel::saveWord,
                onReport = viewModel::reportAnalysis,
            )
            else -> Unit
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp),
        )
    }
}

@Composable
private fun ReaderContent(
    meta: ReaderViewModel.BookMeta,
    currentPage: Int,
    ai: AiUiState,
    translationMode: Boolean,
    translations: Map<Int, ReaderViewModel.TranslationUiState>,
    translationTextScale: Float,
    pageBitmap: suspend (pageIndex: Int, targetWidthPx: Int) -> Bitmap?,
    onPageSettled: (Int) -> Unit,
    onEnterAiMode: () -> Unit,
    onExitAiMode: () -> Unit,
    onStrokeFinished: (List<SelectionPoint>, Float, Float) -> Unit,
    onToggleTranslate: () -> Unit,
    onLoadCachedTranslation: (Int) -> Unit,
    onTranslatePage: (Int) -> Unit,
    onRetranslatePage: (Int) -> Unit,
    onWordTap: (Int, TranslatedWord) -> Unit,
    onTextScaleChanged: (Float) -> Unit,
    onBack: () -> Unit,
) {
    var isZoomed by remember { mutableStateOf(false) }
    val aiActive = ai !is AiUiState.Off
    val pagerState = rememberPagerState(initialPage = meta.initialPage) { meta.pageCount }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect(onPageSettled)
    }
    // ZoomablePage leaves composition in translate mode; without this a
    // stale isZoomed=true would freeze the pager permanently.
    LaunchedEffect(translationMode) {
        if (translationMode) isZoomed = false
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val targetWidthPx = min(constraints.maxWidth, MAX_RENDER_WIDTH_PX)

        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = translationMode || (!isZoomed && !aiActive),
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            if (translationMode) {
                LaunchedEffect(pageIndex) { onLoadCachedTranslation(pageIndex) }
                TranslationPageView(
                    state = translations[pageIndex] ?: ReaderViewModel.TranslationUiState.Idle,
                    textScale = translationTextScale,
                    onTranslate = { onTranslatePage(pageIndex) },
                    onRetranslate = { onRetranslatePage(pageIndex) },
                    onWordTap = { word -> onWordTap(pageIndex, word) },
                )
            } else {
                val bitmap by produceState<Bitmap?>(initialValue = null, pageIndex, targetWidthPx) {
                    value = pageBitmap(pageIndex, targetWidthPx)
                }
                ZoomablePage(
                    bitmap = bitmap?.asImageBitmap(),
                    // Entering AI mode resets zoom so gesture coordinates map 1:1.
                    resetKey = pagerState.settledPage * 2 + if (aiActive) 1 else 0,
                    onZoomChanged = { zoomed -> isZoomed = zoomed },
                )
            }
        }

        if (aiActive && !translationMode) {
            CircleSelectionOverlay(
                onStrokeFinished = onStrokeFinished,
                onExit = onExitAiMode,
            )
        } else {
            ReaderChrome(
                title = meta.title,
                currentPage = currentPage,
                pageCount = meta.pageCount,
                isTranslateActive = translationMode,
                onToggleTranslate = onToggleTranslate,
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )

            if (translationMode) {
                TextScaleFooter(
                    scale = translationTextScale,
                    onScaleChanged = onTextScaleChanged,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = Spacing.xxl),
                )
            } else {
                PillButton(
                    text = stringResource(R.string.reader_circle_ai),
                    onClick = onEnterAiMode,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = Spacing.xxl),
                    trailing = {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = AppTheme.glass.amber,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun TextScaleFooter(
    scale: Float,
    onScaleChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember(scale) { mutableFloatStateOf(scale) }
    GlassCard(cornerRadius = Radius.pill, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.translate_text_size),
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.glass.inkTertiary,
            )
            Slider(
                value = value,
                onValueChange = { value = it },
                // Persisted on release only, not per drag frame.
                onValueChangeFinished = { onScaleChanged(value) },
                valueRange = 0.7f..1.6f,
                modifier = Modifier.width(180.dp),
            )
        }
    }
}

@Composable
private fun ReaderChrome(
    title: String,
    currentPage: Int,
    pageCount: Int,
    isTranslateActive: Boolean,
    onToggleTranslate: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        GlassCard(cornerRadius = Radius.pill) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        GlassCard(cornerRadius = Radius.pill, modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.reader_page_of, currentPage + 1, pageCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.glass.inkTertiary,
                    )
                }
                ProgressRing(
                    progress = if (pageCount <= 0) 0f else (currentPage + 1f) / pageCount,
                    size = 34.dp,
                )
            }
        }
        GlassCard(cornerRadius = Radius.pill) {
            IconButton(onClick = onToggleTranslate) {
                Icon(
                    imageVector = Icons.Outlined.Translate,
                    contentDescription = stringResource(R.string.translate_toggle),
                    tint = if (isTranslateActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                )
            }
        }
    }
}

@Composable
private fun ReaderError(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassCard {
            Column(
                modifier = Modifier.padding(Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = stringResource(R.string.reader_open_error),
                    style = MaterialTheme.typography.titleMedium,
                )
                PillButton(text = stringResource(R.string.reader_back), onClick = onBack)
            }
        }
    }
}
