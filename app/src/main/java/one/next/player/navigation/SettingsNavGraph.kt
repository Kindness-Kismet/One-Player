package one.next.player.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import one.next.player.settings.Setting
import one.next.player.settings.navigation.aboutPreferencesScreen
import one.next.player.settings.navigation.appearancePreferencesScreen
import one.next.player.settings.navigation.audioPreferencesScreen
import one.next.player.settings.navigation.decoderPreferencesScreen
import one.next.player.settings.navigation.folderPreferencesScreen
import one.next.player.settings.navigation.generalPreferencesScreen
import one.next.player.settings.navigation.gesturePreferencesScreen
import one.next.player.settings.navigation.librariesScreen
import one.next.player.settings.navigation.mediaLibraryPreferencesScreen
import one.next.player.settings.navigation.navigateToAboutPreferences
import one.next.player.settings.navigation.navigateToAppearancePreferences
import one.next.player.settings.navigation.navigateToAudioPreferences
import one.next.player.settings.navigation.navigateToDecoderPreferences
import one.next.player.settings.navigation.navigateToFolderPreferencesScreen
import one.next.player.settings.navigation.navigateToGeneralPreferences
import one.next.player.settings.navigation.navigateToGesturePreferences
import one.next.player.settings.navigation.navigateToLibraries
import one.next.player.settings.navigation.navigateToMediaLibraryPreferencesScreen
import one.next.player.settings.navigation.navigateToPlayerPreferences
import one.next.player.settings.navigation.navigateToPrivacyPreferences
import one.next.player.settings.navigation.navigateToSubtitlePreferences
import one.next.player.settings.navigation.navigateToThumbnailPreferencesScreen
import one.next.player.settings.navigation.playerPreferencesScreen
import one.next.player.settings.navigation.privacyPreferencesScreen
import one.next.player.settings.navigation.settingsNavigationRoute
import one.next.player.settings.navigation.settingsScreen
import one.next.player.settings.navigation.subtitlePreferencesScreen
import one.next.player.settings.navigation.thumbnailPreferencesScreen

const val SETTINGS_ROUTE = "settings_nav_route"

fun NavGraphBuilder.settingsNavGraph(
    navController: NavHostController,
) {
    navigation(
        startDestination = settingsNavigationRoute,
        route = SETTINGS_ROUTE,
    ) {
        settingsScreen(
            onNavigateUp = null,
            onItemClick = { setting ->
                when (setting) {
                    Setting.APPEARANCE -> navController.navigateToAppearancePreferences()
                    Setting.MEDIA_LIBRARY -> navController.navigateToMediaLibraryPreferencesScreen()
                    Setting.PLAYER -> navController.navigateToPlayerPreferences()
                    Setting.GESTURES -> navController.navigateToGesturePreferences()
                    Setting.DECODER -> navController.navigateToDecoderPreferences()
                    Setting.AUDIO -> navController.navigateToAudioPreferences()
                    Setting.SUBTITLE -> navController.navigateToSubtitlePreferences()
                    Setting.PRIVACY -> navController.navigateToPrivacyPreferences()
                    Setting.GENERAL -> navController.navigateToGeneralPreferences()
                    Setting.ABOUT -> navController.navigateToAboutPreferences()
                }
            },
        )
        appearancePreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        mediaLibraryPreferencesScreen(
            onNavigateUp = navController::navigateUp,
            onFolderSettingClick = navController::navigateToFolderPreferencesScreen,
            onThumbnailSettingClick = navController::navigateToThumbnailPreferencesScreen,
        )
        thumbnailPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        folderPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        playerPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        gesturePreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        decoderPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        audioPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        subtitlePreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        privacyPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        generalPreferencesScreen(
            onNavigateUp = navController::navigateUp,
        )
        aboutPreferencesScreen(
            onLibrariesClick = navController::navigateToLibraries,
            onNavigateUp = navController::navigateUp,
        )
        librariesScreen(
            onNavigateUp = navController::navigateUp,
        )
    }
}
