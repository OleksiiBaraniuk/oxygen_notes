package com.oxygennotes.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.oxygennotes.app.ui.screens.EditorScreen
import com.oxygennotes.app.ui.screens.SettingsScreen
import com.oxygennotes.app.ui.screens.StitchPrototypeScreen
import com.oxygennotes.app.ui.theme.OxygenNotesTheme

@Composable
fun OxygenNotesApp(
    viewModel: OxygenNotesViewModel = viewModel(factory = OxygenNotesViewModel.Factory)
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val navController = rememberNavController()

    OxygenNotesTheme(darkTheme = isDarkTheme) {
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                StitchPrototypeScreen(
                    viewModel = viewModel,
                    onNoteClick = { noteId ->
                        navController.navigate("editor?noteId=$noteId")
                    },
                    onAddNoteClick = {
                        navController.navigate("editor")
                    },
                    onSettingsClick = {
                        navController.navigate("settings")
                    }
                )
            }
            composable(
                route = "editor?noteId={noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.LongType; defaultValue = -1L })
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getLong("noteId") ?: -1L
                EditorScreen(
                    noteId = if (noteId == -1L) null else noteId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                SettingsScreen(
                    isDarkTheme = isDarkTheme,
                    onThemeChange = { viewModel.setDarkTheme(it) },
                    onExport = { context, uri -> viewModel.exportData(context, uri) },
                    onImport = { context, uri -> viewModel.importData(context, uri) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
