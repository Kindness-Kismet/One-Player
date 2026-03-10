package one.next.player.feature.videopicker.navigation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import one.next.player.feature.videopicker.screens.mediapicker.MediaPickerRoute

internal const val folderIdArg = "folderId"

internal class FolderArgs(val folderId: String?) {
    constructor(savedStateHandle: SavedStateHandle) :
        this(savedStateHandle.get<String>(folderIdArg)?.let { Uri.decode(it) })
}

@Serializable
data class MediaPickerRoute(
    val folderId: String? = null,
)

fun NavController.navigateToMediaPickerScreen(
    folderId: String,
    navOptions: NavOptions? = null,
) {
    val encodedFolderId = Uri.encode(folderId)
    this.navigate(MediaPickerRoute(encodedFolderId), navOptions)
}

fun NavGraphBuilder.mediaPickerScreen(
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onPlayVideos: (uris: List<Uri>) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    composable<MediaPickerRoute> {
        MediaPickerRoute(
            onPlayVideo = onPlayVideo,
            onPlayVideos = onPlayVideos,
            onNavigateUp = onNavigateUp,
            onNavigateHome = onNavigateHome,
            onFolderClick = onFolderClick,
            onSettingsClick = onSettingsClick,
            onSearchClick = onSearchClick,
        )
    }
}
