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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.classicbookreader.app.feature.reader.ReaderViewModel.ReaderEvent
import com.classicbookreader.app.ui.components.GlassCard
import com.classicbookreader.app.ui.components.PillButton
import com.classicbookreader.app.ui.components.ProgressRing
import com.classicbookreader.app.ui.components.UiState
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Spacing
import kotlin.math.min

private const val MAX_RENDER_WIDTH_PX = 1440

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                ReaderEvent.AiComingSoon ->
                    snackbarHostState.showSnackbar(context.getString(R.string.reader_ai_coming))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val meta = state.meta) {
            is UiState.Loading, is UiState.Empty ->
                Box(modifier = Modifier.fillMaxSize())

            is UiState.Error -> ReaderError(onBack = onBack)

            is UiState.Content -> ReaderContent(
                meta = meta.data,
                currentPage = state.currentPage,
                pageBitmap = viewModel::pageBitmap,
                onPageSettled = viewModel::onPageSettled,
                onCircleAiClicked = viewModel::onCircleAiClicked,
                onBack = onBack,
            )
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
    pageBitmap: suspend (pageIndex: Int, targetWidthPx: Int) -> Bitmap?,
    onPageSettled: (Int) -> Unit,
    onCircleAiClicked: () -> Unit,
    onBack: () -> Unit,
) {
    var isZoomed by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = meta.initialPage) { meta.pageCount }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect(onPageSettled)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val targetWidthPx = min(constraints.maxWidth, MAX_RENDER_WIDTH_PX)

        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = !isZoomed,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            val bitmap by produceState<Bitmap?>(initialValue = null, pageIndex, targetWidthPx) {
                value = pageBitmap(pageIndex, targetWidthPx)
            }
            ZoomablePage(
                bitmap = bitmap?.asImageBitmap(),
                resetKey = pagerState.settledPage,
                onZoomChanged = { zoomed -> isZoomed = zoomed },
            )
        }

        ReaderChrome(
            title = meta.title,
            currentPage = currentPage,
            pageCount = meta.pageCount,
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )

        PillButton(
            text = stringResource(R.string.reader_circle_ai),
            onClick = onCircleAiClicked,
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

@Composable
private fun ReaderChrome(
    title: String,
    currentPage: Int,
    pageCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        GlassCard(cornerRadius = 999.dp) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        GlassCard(cornerRadius = 999.dp, modifier = Modifier.weight(1f)) {
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
