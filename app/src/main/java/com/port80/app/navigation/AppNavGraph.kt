package com.port80.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.port80.app.ui.settings.AudioSettingsScreen
import com.port80.app.ui.settings.EndpointScreen
import com.port80.app.ui.settings.GeneralSettingsScreen
import com.port80.app.ui.settings.SettingsHubScreen
import com.port80.app.ui.settings.VideoSettingsScreen
import com.port80.app.ui.stream.StreamScreen

/**
 * Navigation routes for the app.
 * Using string constants keeps navigation type-safe and avoids typos.
 */
object Routes {
    const val STREAM = "stream"
    const val SETTINGS = "settings"
    const val VIDEO_SETTINGS = "settings/video"
    const val AUDIO_SETTINGS = "settings/audio"
    const val GENERAL_SETTINGS = "settings/general"
    const val ENDPOINTS = "settings/endpoints"
}

/**
 * The app's navigation graph using Jetpack Compose Navigation.
 *
 * Navigation flow:
 *   Stream Screen (home)
 *     ├── Settings Hub
 *     │   ├── Video Settings
 *     │   ├── Audio Settings
 *     │   ├── General Settings
 *     │   └── Endpoints
 *     └── (streaming happens here)
 *
 * The Stream Screen is the start destination — it's the first thing
 * users see when they open the app.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.STREAM
    ) {
        composable(Routes.STREAM) {
            StreamScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToEndpoints = { navController.navigate(Routes.ENDPOINTS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsHubScreen(
                onNavigateToVideo = { navController.navigate(Routes.VIDEO_SETTINGS) },
                onNavigateToAudio = { navController.navigate(Routes.AUDIO_SETTINGS) },
                onNavigateToGeneral = { navController.navigate(Routes.GENERAL_SETTINGS) },
                onNavigateToEndpoints = { navController.navigate(Routes.ENDPOINTS) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.VIDEO_SETTINGS) {
            VideoSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.AUDIO_SETTINGS) {
            AudioSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.GENERAL_SETTINGS) {
            GeneralSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.ENDPOINTS) {
            EndpointScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
