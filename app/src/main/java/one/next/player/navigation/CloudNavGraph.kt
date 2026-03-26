package one.next.player.navigation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import one.next.player.feature.player.PlayerActivity
import one.next.player.feature.videopicker.navigation.CloudHomeRoute
import one.next.player.feature.videopicker.navigation.cloudBrowseScreen
import one.next.player.feature.videopicker.navigation.cloudHomeScreen
import one.next.player.feature.videopicker.navigation.navigateToCloudBrowse

@Serializable
data object CloudRootRoute

fun NavGraphBuilder.cloudNavGraph(
    context: Context,
    navController: NavHostController,
) {
    navigation<CloudRootRoute>(startDestination = CloudHomeRoute) {
        cloudHomeScreen(
            onNavigateUp = navController::navigateUp,
            onServerClick = { serverId ->
                navController.navigateToCloudBrowse(serverId)
            },
        )

        cloudBrowseScreen(
            onNavigateUp = navController::navigateUp,
            onPlayVideo = { uri, headers, initialSubtitleDirectoryUri ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                    if (headers.isNotEmpty()) {
                        val headerBundle = Bundle().apply {
                            headers.forEach { (key, value) -> putString(key, value) }
                        }
                        putExtra("headers", headerBundle)
                    }
                    putExtra("initial_subtitle_directory_uri", initialSubtitleDirectoryUri)
                }
                context.startActivity(intent)
            },
        )
    }
}
