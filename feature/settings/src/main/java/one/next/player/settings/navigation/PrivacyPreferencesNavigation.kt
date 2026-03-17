package one.next.player.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import one.next.player.settings.screens.privacy.PrivacyProtectionScreen

const val privacyPreferencesNavigationRoute = "privacy_preferences_route"

fun NavController.navigateToPrivacyPreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(privacyPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.privacyPreferencesScreen(onNavigateUp: () -> Unit) {
    composable(route = privacyPreferencesNavigationRoute) {
        PrivacyProtectionScreen(onNavigateUp = onNavigateUp)
    }
}
