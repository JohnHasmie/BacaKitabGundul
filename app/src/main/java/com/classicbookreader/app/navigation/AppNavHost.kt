package com.classicbookreader.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.classicbookreader.app.R
import com.classicbookreader.app.feature.home.HomeScreen
import com.classicbookreader.app.feature.library.LibraryScreen
import com.classicbookreader.app.feature.savedwords.SavedWordsScreen
import com.classicbookreader.app.feature.settings.SettingsScreen
import com.classicbookreader.app.ui.components.DockItem
import com.classicbookreader.app.ui.components.GlassDock

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SAVED_WORDS = "saved_words"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME

    val dockItems = listOf(
        DockItem(Routes.HOME, stringResource(R.string.nav_home), Icons.Outlined.Home),
        DockItem(Routes.LIBRARY, stringResource(R.string.nav_library), Icons.Outlined.AutoStories),
        DockItem(Routes.SAVED_WORDS, stringResource(R.string.nav_saved), Icons.Outlined.BookmarkBorder),
        DockItem(Routes.SETTINGS, stringResource(R.string.nav_settings), Icons.Outlined.Settings),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) { HomeScreen() }
            composable(Routes.LIBRARY) { LibraryScreen() }
            composable(Routes.SAVED_WORDS) { SavedWordsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }

        GlassDock(
            items = dockItems,
            selectedRoute = currentRoute,
            onItemSelected = { item ->
                navController.navigate(item.route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        )
    }
}
