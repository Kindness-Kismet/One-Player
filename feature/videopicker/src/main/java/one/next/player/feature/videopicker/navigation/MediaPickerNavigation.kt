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
internal const val screenModeArg = "screenMode"

internal class FolderArgs(
    val folderId: String?,
    val screenMode: MediaPickerScreenMode,
) {
    constructor(savedStateHandle: SavedStateHandle) :
        this(
            folderId = when (val rawFolderId = savedStateHandle.get<Any?>(folderIdArg)) {
                is String -> Uri.decode(rawFolderId)
                else -> null
            },
            screenMode = when (val rawScreenMode = savedStateHandle.get<Any?>(screenModeArg)) {
                is MediaPickerScreenMode -> rawScreenMode
                is String -> rawScreenMode.let(MediaPickerScreenMode::valueOf)
                else -> MediaPickerScreenMode.LIBRARY
            },
        )
}

@Serializable
enum class MediaPickerScreenMode {
    LIBRARY,
    RECYCLE_BIN,
}

@Serializable
data class MediaPickerRoute(
    val folderId: String? = null,
    val screenMode: MediaPickerScreenMode = MediaPickerScreenMode.LIBRARY,
)

fun NavController.navigateToMediaPickerScreen(
    folderId: String,
    screenMode: MediaPickerScreenMode = MediaPickerScreenMode.LIBRARY,
    navOptions: NavOptions? = null,
) {
    val encodedFolderId = Uri.encode(folderId)
    this.navigate(MediaPickerRoute(folderId = encodedFolderId, screenMode = screenMode), navOptions)
}

fun NavController.navigateToRecycleBinScreen(navOptions: NavOptions? = null) {
    this.navigate(MediaPickerRoute(screenMode = MediaPickerScreenMode.RECYCLE_BIN), navOptions)
}

fun NavGraphBuilder.mediaPickerScreen(
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onPlayVideo: (uri: Uri) -> Unit,
    onPlayVideos: (uris: List<Uri>) -> Unit,
    onFolderClick: (folderPath: String, screenMode: MediaPickerScreenMode) -> Unit,
    onRecycleBinClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    composable<MediaPickerRoute> {
        MediaPickerRoute(
            onPlayVideo = onPlayVideo,
            onPlayVideos = onPlayVideos,
            onNavigateUp = onNavigateUp,
            onNavigateHome = onNavigateHome,
            onFolderClick = onFolderClick,
            onRecycleBinClick = onRecycleBinClick,
            onSearchClick = onSearchClick,
        )
    }
}
