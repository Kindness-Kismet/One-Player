package one.next.player.feature.videopicker.screens.cloud

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.next.player.core.model.RemoteFile
import one.next.player.core.ui.R
import one.next.player.core.ui.components.NextSegmentedListItem
import one.next.player.core.ui.components.NextTopAppBar
import one.next.player.core.ui.designsystem.NextIcons
import one.next.player.core.ui.extensions.copy
import one.next.player.core.ui.extensions.withBottomFallback

@Composable
fun CloudBrowseRoute(
    viewModel: CloudBrowseViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
    onPlayVideo: (uri: Uri, headers: Map<String, String>, initialSubtitleDirectoryUri: Uri?, playlist: List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 从播放器返回时刷新播放状态
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(CloudBrowseEvent.RefreshPlaybackStates)
        onPauseOrDispose {}
    }

    CloudBrowseScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEvent = viewModel::onEvent,
        onFileClick = { file ->
            val url = viewModel.buildPlayUrl(file) ?: return@CloudBrowseScreen
            val headers = viewModel.buildAuthHeaders(file)
            val initialSubtitleDirectoryUri = viewModel.buildCurrentDirectoryDocumentId()
                ?.let { documentId ->
                    DocumentsContract.buildDocumentUri("${context.packageName}.documents", documentId)
                }
            val playlist = viewModel.buildAllVideoPlayUrls()
            onPlayVideo(Uri.parse(url), headers, initialSubtitleDirectoryUri, playlist)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CloudBrowseScreen(
    uiState: CloudBrowseUiState,
    onNavigateUp: () -> Unit = {},
    onEvent: (CloudBrowseEvent) -> Unit = {},
    onFileClick: (RemoteFile) -> Unit = {},
) {
    val serverName = uiState.server?.name?.takeIf { it.isNotBlank() }
        ?: uiState.server?.host
        ?: stringResource(R.string.browsing)

    val isAtRoot = uiState.server?.let { server ->
        uiState.currentPath.removeSuffix("/") == server.path.removeSuffix("/")
    } ?: true

    // 出错时直接允许返回上级页面，不再反复重试 PROPFIND
    BackHandler(enabled = !isAtRoot && !uiState.isError) {
        onEvent(CloudBrowseEvent.NavigateUp)
    }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = serverName,
                navigationIcon = {
                    FilledTonalIconButton(onClick = {
                        if (isAtRoot || uiState.isError) {
                            onNavigateUp()
                        } else {
                            onEvent(CloudBrowseEvent.NavigateUp)
                        }
                    }) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(start = innerPadding.calculateStartPadding(LocalLayoutDirection.current)),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                val contentPadding = innerPadding.copy(top = 0.dp, start = 0.dp).withBottomFallback()
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.isError -> {
                        CloudBrowseMessageState(
                            contentPadding = contentPadding,
                            icon = NextIcons.Cloud,
                            title = stringResource(R.string.connection_failed),
                            message = uiState.errorMessage,
                            actionText = stringResource(R.string.retry),
                            onActionClick = { onEvent(CloudBrowseEvent.Retry) },
                        )
                    }

                    uiState.files.isEmpty() -> {
                        CloudBrowseMessageState(
                            contentPadding = contentPadding,
                            icon = NextIcons.Folder,
                            title = stringResource(R.string.empty_directory),
                        )
                    }

                    else -> {
                        val mostRecentFilePath = uiState.playbackStates.entries
                            .maxByOrNull { it.value.lastPlayedTime ?: 0L }
                            ?.takeIf { (it.value.lastPlayedTime ?: 0L) > 0L }
                            ?.key

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = contentPadding,
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        ) {
                            items(uiState.files, key = { it.path }) { file ->
                                val index = uiState.files.indexOf(file)
                                val playbackInfo = uiState.playbackStates[file.path]
                                val isRecentlyPlayed = !file.isDirectory && file.path == mostRecentFilePath
                                val hasBeenPlayed = playbackInfo != null && playbackInfo.playbackPosition > 0
                                RemoteFileItem(
                                    file = file,
                                    isFirstItem = index == 0,
                                    isLastItem = index == uiState.files.lastIndex,
                                    isRecentlyPlayed = isRecentlyPlayed,
                                    hasBeenPlayed = hasBeenPlayed,
                                    onClick = {
                                        if (file.isDirectory) {
                                            onEvent(CloudBrowseEvent.NavigateToDirectory(file.path))
                                        } else {
                                            onFileClick(file)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudBrowseMessageState(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.padding(top = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (!actionText.isNullOrBlank() && onActionClick != null) {
                TextButton(onClick = onActionClick) {
                    Text(text = actionText)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemoteFileItem(
    file: RemoteFile,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    isRecentlyPlayed: Boolean = false,
    hasBeenPlayed: Boolean = false,
    onClick: () -> Unit,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    NextSegmentedListItem(
        onClick = onClick,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        modifier = Modifier.testTag("remote_file_${file.name}"),
        leadingContent = {
            Icon(
                imageVector = if (file.isDirectory) NextIcons.Folder else NextIcons.Video,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isRecentlyPlayed) highlightColor else LocalContentColor.current,
            )
        },
        supportingContent = if (!file.isDirectory) {
            {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (file.size > 0) {
                        Text(
                            text = formatFileSize(file.size),
                            color = if (isRecentlyPlayed) highlightColor else Color.Unspecified,
                        )
                    }
                    if (hasBeenPlayed) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(highlightColor, CircleShape),
                        )
                    }
                }
            }
        } else {
            null
        },
        content = {
            Text(
                text = file.name,
                color = if (isRecentlyPlayed) highlightColor else Color.Unspecified,
            )
        },
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
