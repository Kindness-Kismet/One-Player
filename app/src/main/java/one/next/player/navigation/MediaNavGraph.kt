package one.next.player.navigation

import android.content.Context
import android.content.Intent
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import one.next.player.feature.player.PlayerActivity
import one.next.player.feature.player.utils.PlayerApi
import one.next.player.feature.videopicker.navigation.MediaPickerRoute
import one.next.player.feature.videopicker.navigation.mediaPickerScreen
import one.next.player.feature.videopicker.navigation.navigateToMediaPickerScreen
import one.next.player.feature.videopicker.navigation.navigateToSearch
import one.next.player.feature.videopicker.navigation.searchScreen
import one.next.player.settings.navigation.navigateToSettings

@Serializable
data object MediaRootRoute

fun NavGraphBuilder.mediaNavGraph(
    context: Context,
    navController: NavHostController,
) {
    navigation<MediaRootRoute>(startDestination = MediaPickerRoute()) {
        mediaPickerScreen(
            onNavigateUp = navController::navigateUp,
            onNavigateHome = {
                navController.popBackStack(MediaPickerRoute(), inclusive = false)
            },
            onPlayVideo = { uri ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                }
                context.startActivity(intent)
            },
            onPlayVideos = { uris ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uris.first()
                    putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, ArrayList(uris))
                }
                context.startActivity(intent)
            },
            onFolderClick = navController::navigateToMediaPickerScreen,
            onSettingsClick = navController::navigateToSettings,
            onSearchClick = navController::navigateToSearch,
        )

        searchScreen(
            onNavigateUp = navController::navigateUp,
            onPlayVideo = { uri ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                }
                context.startActivity(intent)
            },
            onFolderClick = navController::navigateToMediaPickerScreen,
        )
    }
}
