package one.next.player.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import kotlinx.serialization.Serializable
import one.next.player.feature.videopicker.screens.cloud.CloudBrowseRoute as CloudBrowseScreenRoute
import one.next.player.feature.videopicker.screens.cloud.CloudHomeRoute as CloudHomeScreenRoute

@Serializable
data object CloudHomeRoute

@Serializable
data class CloudBrowseRoute(
    val serverId: Long,
    val initialPath: String = "/",
)

fun NavController.navigateToCloudHome(
    navOptions: NavOptions? = navOptions { launchSingleTop = true },
) {
    this.navigate(CloudHomeRoute, navOptions)
}

fun NavController.navigateToCloudBrowse(
    serverId: Long,
    initialPath: String = "/",
    navOptions: NavOptions? = null,
) {
    val encodedInitialPath = Uri.encode(initialPath)
    this.navigate(CloudBrowseRoute(serverId = serverId, initialPath = encodedInitialPath), navOptions)
}

fun NavGraphBuilder.cloudHomeScreen(
    onNavigateUp: () -> Unit,
    onServerClick: (Long) -> Unit,
) {
    composable<CloudHomeRoute> {
        CloudHomeScreenRoute(
            onNavigateUp = onNavigateUp,
            onServerClick = onServerClick,
        )
    }
}

fun NavGraphBuilder.cloudBrowseScreen(
    onNavigateUp: () -> Unit,
    onDirectoryClick: (serverId: Long, path: String) -> Unit,
    onPlayVideo: (uri: Uri, headers: Map<String, String>, initialSubtitleDirectoryUri: Uri?, playlist: List<Uri>) -> Unit,
) {
    composable<CloudBrowseRoute> {
        CloudBrowseScreenRoute(
            onNavigateUp = onNavigateUp,
            onDirectoryClick = onDirectoryClick,
            onPlayVideo = onPlayVideo,
        )
    }
}
