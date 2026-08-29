package com.classicbookreader.app.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classicbookreader.app.R
import com.classicbookreader.app.feature.library.LibraryViewModel.BookUi
import com.classicbookreader.app.feature.library.LibraryViewModel.ImportState
import com.classicbookreader.app.feature.library.LibraryViewModel.LibraryEvent
import com.classicbookreader.app.ui.components.AsyncView
import com.classicbookreader.app.ui.components.CoverImage
import com.classicbookreader.app.ui.components.GlassCard
import com.classicbookreader.app.ui.components.PillButton
import com.classicbookreader.app.ui.components.ProgressRing
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Radius
import com.classicbookreader.app.ui.theme.Sizes
import com.classicbookreader.app.ui.theme.Spacing

@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val fallbackTitle = stringResource(R.string.import_default_title)

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> viewModel.onPdfPicked(uri, fallbackTitle) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.ImportSucceeded -> Unit // grid updates reactively
                is LibraryEvent.ImportFailed ->
                    snackbarHostState.showSnackbar(context.getString(event.messageResId))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                PillButton(
                    text = stringResource(R.string.library_add_book),
                    onClick = { showAddSheet = true },
                )
            }

            if (importState is ImportState.Importing) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = stringResource(R.string.import_in_progress),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = AppTheme.glass.inkWash,
                        )
                    }
                }
            }

            AsyncView(state = books, onRetry = {}) { items ->
                if (items.isEmpty()) {
                    EmptyLibrary(onAddBook = { showAddSheet = true })
                } else {
                    BookGrid(books = items, onOpenBook = onOpenBook)
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

    if (showAddSheet) {
        AddBookSheet(
            onPickPdf = {
                showAddSheet = false
                pdfPicker.launch(arrayOf("application/pdf"))
            },
            onDismiss = { showAddSheet = false },
        )
    }
}

@Composable
private fun BookGrid(books: List<BookUi>, onOpenBook: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(books, key = { it.id }) { book ->
            BookTile(book = book, onClick = { onOpenBook(book.id) })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(Sizes.dockClearance))
        }
    }
}

@Composable
private fun BookTile(book: BookUi, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            CoverImage(
                path = book.coverPath,
                contentDescription = book.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(Radius.sm)),
            )
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        R.string.library_page_of,
                        book.lastReadPage + 1,
                        book.pageCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.glass.inkTertiary,
                )
                ProgressRing(progress = book.progress, size = 30.dp)
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onAddBook: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassCard {
            Column(
                modifier = Modifier.padding(Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = stringResource(R.string.library_empty),
                    style = MaterialTheme.typography.titleMedium,
                )
                PillButton(text = stringResource(R.string.library_add_book), onClick = onAddBook)
            }
        }
    }
}
