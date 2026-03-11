package one.next.player.feature.videopicker.screens.mediapicker

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.next.player.core.common.storagePermission
import one.next.player.core.media.services.MediaService
import one.next.player.core.model.ApplicationPreferences
import one.next.player.core.model.Folder
import one.next.player.core.model.MediaLayoutMode
import one.next.player.core.model.MediaViewMode
import one.next.player.core.model.Video
import one.next.player.core.ui.R
import one.next.player.core.ui.base.DataState
import one.next.player.core.ui.components.CancelButton
import one.next.player.core.ui.components.DoneButton
import one.next.player.core.ui.components.NextDialog
import one.next.player.core.ui.components.NextTopAppBar
import one.next.player.core.ui.composables.PermissionMissingView
import one.next.player.core.ui.composables.rememberRuntimePermissionState
import one.next.player.core.ui.designsystem.NextIcons
import one.next.player.core.ui.extensions.copy
import one.next.player.core.ui.preview.DayNightPreview
import one.next.player.core.ui.preview.VideoPickerPreviewParameterProvider
import one.next.player.core.ui.theme.NextPlayerTheme
import one.next.player.feature.videopicker.composables.CenterCircularProgressBar
import one.next.player.feature.videopicker.composables.MediaView
import one.next.player.feature.videopicker.composables.NoVideosFound
import one.next.player.feature.videopicker.composables.QuickSettingsDialog
import one.next.player.feature.videopicker.composables.RenameDialog
import one.next.player.feature.videopicker.composables.TextIconToggleButton
import one.next.player.feature.videopicker.composables.VideoInfoDialog
import one.next.player.feature.videopicker.navigation.MediaPickerScreenMode
import one.next.player.feature.videopicker.state.SelectedFolder
import one.next.player.feature.videopicker.state.SelectedVideo
import one.next.player.feature.videopicker.state.rememberSelectionManager

@Composable
fun MediaPickerRoute(
    viewModel: MediaPickerViewModel = hiltViewModel(),
    onPlayVideo: (uri: Uri) -> Unit,
    onPlayVideos: (uris: List<Uri>) -> Unit,
    onFolderClick: (folderPath: String, screenMode: MediaPickerScreenMode) -> Unit,
    onRecycleBinClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MediaPickerScreen(
        uiState = uiState,
        onPlayVideo = onPlayVideo,
        onPlayVideos = onPlayVideos,
        onNavigateUp = onNavigateUp,
        onNavigateHome = onNavigateHome,
        onFolderClick = onFolderClick,
        onRecycleBinClick = onRecycleBinClick,
        onSettingsClick = onSettingsClick,
        onSearchClick = onSearchClick,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaPickerScreen(
    uiState: MediaPickerUiState,
    onNavigateUp: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onPlayVideo: (Uri) -> Unit = {},
    onPlayVideos: (List<Uri>) -> Unit = {},
    onFolderClick: (String, MediaPickerScreenMode) -> Unit = { _, _ -> },
    onRecycleBinClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onEvent: (MediaPickerUiEvent) -> Unit = {},
) {
    val selectionManager = rememberSelectionManager()
    val permissionState = rememberRuntimePermissionState(permission = storagePermission)
    val lazyGridState = rememberLazyGridState()
    val selectVideoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { it?.let { onPlayVideo(it) } },
    )

    var isFabExpanded by rememberSaveable { mutableStateOf(false) }
    var showQuickSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }

    var showRenameActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var showInfoActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var showDeleteVideosConfirmation by rememberSaveable { mutableStateOf(false) }

    val isLibraryMode = uiState.screenMode == MediaPickerScreenMode.LIBRARY
    val isRecycleBinMode = uiState.screenMode == MediaPickerScreenMode.RECYCLE_BIN
    val showRecycleBinIcon = isLibraryMode &&
        uiState.folderName == null &&
        uiState.preferences.recycleBinEnabled &&
        uiState.preferences.showRecycleBinIcon
    val showRecycleBinLongPressEntry = isLibraryMode &&
        uiState.folderName == null &&
        uiState.preferences.recycleBinEnabled &&
        !uiState.preferences.showRecycleBinIcon
    val deleteAction = when {
        isRecycleBinMode -> MediaPickerDeleteAction.PermanentlyDelete
        uiState.preferences.recycleBinEnabled -> MediaPickerDeleteAction.MoveToRecycleBin
        else -> MediaPickerDeleteAction.PermanentlyDelete
    }
    val selectedItemsSize = selectionManager.selectedFolders.size + selectionManager.selectedVideos.size
    val totalItemsSize = (uiState.mediaDataState as? DataState.Success)?.value?.run { folderList.size + mediaList.size } ?: 0

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = (
                    uiState.folderName ?: stringResource(
                        if (isRecycleBinMode) {
                            R.string.recycle_bin
                        } else {
                            R.string.app_name
                        },
                    )
                    ).takeIf { !selectionManager.isInSelectionMode } ?: "",
                fontWeight = FontWeight.Bold.takeIf { uiState.folderName == null },
                onTitleLongClick = when {
                    selectionManager.isInSelectionMode -> null
                    uiState.folderName != null -> onNavigateHome
                    showRecycleBinLongPressEntry -> onRecycleBinClick
                    else -> null
                },
                navigationIcon = {
                    if (selectionManager.isInSelectionMode) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { selectionManager.exitSelectionMode() }
                                .padding(8.dp)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = NextIcons.Close,
                                contentDescription = stringResource(id = R.string.navigate_up),
                            )
                            Text(
                                text = stringResource(R.string.m_n_selected, selectedItemsSize, totalItemsSize),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else if (uiState.folderName != null || isRecycleBinMode) {
                        FilledTonalIconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = NextIcons.ArrowBack,
                                contentDescription = stringResource(id = R.string.navigate_up),
                            )
                        }
                    }
                },
                actions = {
                    if (selectionManager.isInSelectionMode) {
                        FilledTonalIconButton(
                            onClick = {
                                if (selectedItemsSize != totalItemsSize) {
                                    (uiState.mediaDataState as? DataState.Success)?.value?.let { folder ->
                                        folder.folderList.forEach { selectionManager.selectFolder(it) }
                                        folder.mediaList.forEach { selectionManager.selectVideo(it) }
                                    }
                                } else {
                                    selectionManager.clearSelection()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (selectedItemsSize != totalItemsSize) {
                                    NextIcons.SelectAll
                                } else {
                                    NextIcons.DeselectAll
                                },
                                contentDescription = if (selectedItemsSize != totalItemsSize) {
                                    stringResource(R.string.select_all)
                                } else {
                                    stringResource(R.string.deselect_all)
                                },
                            )
                        }
                    } else {
                        if (isLibraryMode) {
                            IconButton(onClick = onSearchClick) {
                                Icon(
                                    imageVector = NextIcons.Search,
                                    contentDescription = stringResource(id = R.string.search),
                                )
                            }
                            if (showRecycleBinIcon) {
                                IconButton(onClick = onRecycleBinClick) {
                                    Icon(
                                        imageVector = NextIcons.DeleteSweep,
                                        contentDescription = stringResource(id = R.string.recycle_bin),
                                    )
                                }
                            }
                            IconButton(onClick = { showQuickSettingsDialog = true }) {
                                Icon(
                                    imageVector = NextIcons.DashBoard,
                                    contentDescription = stringResource(id = R.string.menu),
                                )
                            }
                            IconButton(onClick = onSettingsClick) {
                                Icon(
                                    imageVector = NextIcons.Settings,
                                    contentDescription = stringResource(id = R.string.settings),
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            SelectionActionsSheet(
                show = selectionManager.isInSelectionMode &&
                    (selectionManager.allSelectedVideos.isNotEmpty() || selectionManager.selectedFolders.isNotEmpty()),
                deleteAction = deleteAction,
                showRestoreAction = isRecycleBinMode,
                showRenameAction = selectionManager.isSingleVideoSelected && isLibraryMode,
                showInfoAction = selectionManager.isSingleVideoSelected,
                showExcludeAction = selectionManager.selectedFolders.isNotEmpty() && isLibraryMode,
                onPlayAction = {
                    val videoUris = selectionManager.allSelectedVideos.map { it.uriString.toUri() }
                    onPlayVideos(videoUris)
                    selectionManager.clearSelection()
                },
                onRenameAction = {
                    val selectedVideo = selectionManager.selectedVideos.firstOrNull() ?: return@SelectionActionsSheet
                    val video = (uiState.mediaDataState as? DataState.Success)?.value?.mediaList
                        ?.find { it.uriString == selectedVideo.uriString } ?: return@SelectionActionsSheet
                    showRenameActionFor = video
                },
                onInfoAction = {
                    val selectedVideo = selectionManager.selectedVideos.firstOrNull() ?: return@SelectionActionsSheet
                    val video = (uiState.mediaDataState as? DataState.Success)?.value?.mediaList
                        ?.find { it.uriString == selectedVideo.uriString } ?: return@SelectionActionsSheet
                    showInfoActionFor = video
                    selectionManager.clearSelection()
                },
                onShareAction = {
                    onEvent(MediaPickerUiEvent.ShareVideos(selectionManager.allSelectedVideos.map { it.uriString }))
                },
                onRestoreAction = {
                    onEvent(MediaPickerUiEvent.RestoreVideos(selectionManager.allSelectedVideos.map { it.uriString }))
                    selectionManager.clearSelection()
                },
                onDeleteAction = {
                    if (
                        deleteAction == MediaPickerDeleteAction.PermanentlyDelete &&
                        MediaService.willSystemAsksForDeleteConfirmation()
                    ) {
                        onEvent(MediaPickerUiEvent.PermanentlyDeleteVideos(selectionManager.allSelectedVideos.map { it.uriString }))
                        selectionManager.clearSelection()
                    } else {
                        showDeleteVideosConfirmation = true
                    }
                },
                onExcludeAction = {
                    val paths = selectionManager.selectedFolders.map { it.path }
                    onEvent(MediaPickerUiEvent.ExcludeFolders(paths))
                    selectionManager.exitSelectionMode()
                },
            )
        },
        floatingActionButton = {
            if (selectionManager.isInSelectionMode || isRecycleBinMode) return@Scaffold

            FloatingActionButtonMenu(
                expanded = isFabExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = isFabExpanded,
                        onCheckedChange = { isFabExpanded = !isFabExpanded },
                    ) {
                        val icon by remember {
                            derivedStateOf {
                                if (checkedProgress > 0.5f) NextIcons.Close else NextIcons.Play
                            }
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.animateIcon(checkedProgress = { checkedProgress }),
                        )
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        showUrlDialog = true
                    },
                    icon = {
                        Icon(
                            imageVector = NextIcons.Link,
                            contentDescription = null,
                        )
                    },
                    text = {
                        Text(text = stringResource(id = R.string.open_network_stream))
                    },
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        isFabExpanded = false
                        selectVideoFileLauncher.launch("video/*")
                    },
                    icon = {
                        Icon(
                            imageVector = NextIcons.FileOpen,
                            contentDescription = null,
                        )
                    },
                    text = {
                        Text(text = stringResource(id = R.string.open_local_video))
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.displayCutout,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding())
                .padding(start = scaffoldPadding.calculateStartPadding(LocalLayoutDirection.current)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.background),
            ) {
                PermissionMissingView(
                    isGranted = permissionState.isGranted,
                    showRationale = permissionState.shouldShowRationale,
                    permission = permissionState.permission,
                    launchPermissionRequest = { permissionState.launchPermissionRequest() },
                ) {
                    when (uiState.mediaDataState) {
                        DataState.Loading -> {
                            CenterCircularProgressBar(modifier = Modifier.fillMaxSize())
                        }

                        is DataState.Error -> {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background,
                            ) {
                                Text(
                                    text = stringResource(id = R.string.unknown_error),
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        is DataState.Success -> {
                            PullToRefreshBox(
                                modifier = Modifier.fillMaxSize(),
                                isRefreshing = uiState.refreshing,
                                onRefresh = { onEvent(MediaPickerUiEvent.Refresh) },
                            ) {
                                val updatedScaffoldPadding = scaffoldPadding.copy(top = 0.dp, start = 0.dp)
                                val rootFolder = uiState.mediaDataState.value
                                if (rootFolder == null || rootFolder.folderList.isEmpty() && rootFolder.mediaList.isEmpty()) {
                                    NoVideosFound(contentPadding = updatedScaffoldPadding)
                                } else {
                                    MediaView(
                                        rootFolder = rootFolder,
                                        preferences = uiState.preferences,
                                        onFolderClick = {
                                            onEvent(MediaPickerUiEvent.CacheFolderSnapshot(it))
                                            onFolderClick(it.path, uiState.screenMode)
                                        },
                                        onVideoClick = { onPlayVideo(it) },
                                        selectionManager = selectionManager,
                                        lazyGridState = lazyGridState,
                                        contentPadding = updatedScaffoldPadding,
                                        onVideoLoaded = { onEvent(MediaPickerUiEvent.AddToSync(it)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(lazyGridState.isScrollInProgress) {
        if (isFabExpanded && lazyGridState.isScrollInProgress) {
            isFabExpanded = false
        }
    }

    LaunchedEffect(selectionManager.isInSelectionMode) {
        if (selectionManager.isInSelectionMode) {
            isFabExpanded = false
        }
    }

    BackHandler(enabled = isFabExpanded) {
        isFabExpanded = false
    }

    BackHandler(enabled = selectionManager.isInSelectionMode) {
        selectionManager.exitSelectionMode()
    }

    if (showQuickSettingsDialog) {
        QuickSettingsDialog(
            applicationPreferences = uiState.preferences,
            onDismiss = { showQuickSettingsDialog = false },
            updatePreferences = { onEvent(MediaPickerUiEvent.UpdateMenu(it)) },
        )
    }

    if (showUrlDialog) {
        NetworkUrlDialog(
            onDismiss = { showUrlDialog = false },
            onDone = { onPlayVideo(it.toUri()) },
        )
    }

    showRenameActionFor?.let { video ->
        RenameDialog(
            name = video.displayName,
            onDismiss = { showRenameActionFor = null },
            onDone = {
                onEvent(MediaPickerUiEvent.RenameVideo(video.uriString.toUri(), it))
                showRenameActionFor = null
                selectionManager.clearSelection()
            },
        )
    }

    showInfoActionFor?.let { video ->
        VideoInfoDialog(
            video = video,
            onDismiss = { showInfoActionFor = null },
        )
    }

    if (showDeleteVideosConfirmation) {
        DeleteConfirmationDialog(
            selectedVideos = selectionManager.selectedVideos,
            selectedFolders = selectionManager.selectedFolders,
            deleteAction = deleteAction,
            onConfirm = {
                when (deleteAction) {
                    MediaPickerDeleteAction.MoveToRecycleBin -> {
                        onEvent(MediaPickerUiEvent.MoveVideosToRecycleBin(selectionManager.allSelectedVideos.map { it.uriString }))
                    }

                    MediaPickerDeleteAction.PermanentlyDelete -> {
                        onEvent(MediaPickerUiEvent.PermanentlyDeleteVideos(selectionManager.allSelectedVideos.map { it.uriString }))
                    }
                }
                selectionManager.clearSelection()
                showDeleteVideosConfirmation = false
            },
            onCancel = { showDeleteVideosConfirmation = false },
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    modifier: Modifier = Modifier,
    selectedVideos: Set<SelectedVideo>,
    selectedFolders: Set<SelectedFolder>,
    deleteAction: MediaPickerDeleteAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    NextDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = if (deleteAction == MediaPickerDeleteAction.MoveToRecycleBin) {
                    stringResource(R.string.move_to_recycle_bin)
                } else {
                    when {
                        selectedVideos.isEmpty() -> when (selectedFolders.size) {
                            1 -> stringResource(R.string.delete_one_folder)
                            else -> stringResource(R.string.delete_folders, selectedFolders.size)
                        }

                        selectedFolders.isEmpty() -> when (selectedVideos.size) {
                            1 -> stringResource(R.string.delete_one_video)
                            else -> stringResource(R.string.delete_videos, selectedVideos.size)
                        }

                        else -> stringResource(R.string.delete_items, selectedFolders.size + selectedVideos.size)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = modifier,
            ) {
                Text(
                    text = stringResource(
                        if (deleteAction == MediaPickerDeleteAction.MoveToRecycleBin) {
                            R.string.move_to_recycle_bin
                        } else {
                            R.string.delete_permanently
                        },
                    ),
                )
            }
        },
        dismissButton = { CancelButton(onClick = onCancel) },
        modifier = modifier,
        content = {
            Text(
                text = if (deleteAction == MediaPickerDeleteAction.MoveToRecycleBin) {
                    stringResource(R.string.move_to_recycle_bin_info)
                } else if ((selectedFolders.size + selectedVideos.size) == 1) {
                    stringResource(R.string.delete_item_info)
                } else {
                    stringResource(R.string.delete_items_info)
                },
                style = MaterialTheme.typography.titleSmall,
            )
        },
    )
}

private enum class MediaPickerDeleteAction {
    MoveToRecycleBin,
    PermanentlyDelete,
}

@Composable
private fun SelectionActionsSheet(
    show: Boolean,
    deleteAction: MediaPickerDeleteAction,
    showRestoreAction: Boolean,
    showRenameAction: Boolean,
    showInfoAction: Boolean,
    showExcludeAction: Boolean,
    onPlayAction: () -> Unit,
    onRestoreAction: () -> Unit,
    onRenameAction: () -> Unit,
    onInfoAction: () -> Unit,
    onShareAction: () -> Unit,
    onDeleteAction: () -> Unit,
    onExcludeAction: () -> Unit,
) {
    AnimatedVisibility(
        visible = show,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionActionItem(
                imageVector = NextIcons.Play,
                text = stringResource(id = R.string.play),
                onClick = onPlayAction,
            )
            if (showRestoreAction) {
                SelectionActionItem(
                    imageVector = NextIcons.ArrowUpward,
                    text = stringResource(id = R.string.restore),
                    onClick = onRestoreAction,
                )
            }
            if (showRenameAction) {
                SelectionActionItem(
                    imageVector = NextIcons.Edit,
                    text = stringResource(id = R.string.rename),
                    onClick = onRenameAction,
                )
            }
            if (showInfoAction) {
                SelectionActionItem(
                    imageVector = NextIcons.Info,
                    text = stringResource(id = R.string.info),
                    onClick = onInfoAction,
                )
            }
            SelectionActionItem(
                imageVector = NextIcons.Share,
                text = stringResource(id = R.string.share),
                onClick = onShareAction,
            )
            if (showExcludeAction) {
                SelectionActionItem(
                    imageVector = NextIcons.FolderOff,
                    text = stringResource(id = R.string.exclude),
                    onClick = onExcludeAction,
                )
            }
            SelectionActionItem(
                imageVector = NextIcons.Delete,
                text = stringResource(
                    id = when (deleteAction) {
                        MediaPickerDeleteAction.MoveToRecycleBin -> R.string.move_to_recycle_bin
                        MediaPickerDeleteAction.PermanentlyDelete -> {
                            if (showRestoreAction) {
                                R.string.delete_permanently
                            } else {
                                R.string.delete
                            }
                        }
                    },
                ),
                onClick = onDeleteAction,
            )
        }
    }
}

@Composable
private fun SelectionActionItem(
    imageVector: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(
                imageVector = imageVector,
                contentDescription = text,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NetworkUrlDialog(
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }

    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_stream)) },
        content = {
            Text(text = stringResource(R.string.enter_a_network_url))
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = stringResource(R.string.example_url)) },
            )
        },
        confirmButton = {
            DoneButton(
                enabled = url.isNotBlank(),
                onClick = { onDone(url) },
            )
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}

@PreviewScreenSizes
@PreviewLightDark
@Composable
private fun MediaPickerScreenPreview(
    @PreviewParameter(VideoPickerPreviewParameterProvider::class)
    videos: List<Video>,
) {
    NextPlayerTheme {
        MediaPickerScreen(
            uiState = MediaPickerUiState(
                folderName = null,
                mediaDataState = DataState.Success(
                    value = Folder(
                        name = "Root Folder",
                        path = "/root",
                        dateModified = System.currentTimeMillis(),
                        folderList = listOf(
                            Folder(name = "Folder 1", path = "/root/folder1", dateModified = System.currentTimeMillis()),
                            Folder(name = "Folder 2", path = "/root/folder2", dateModified = System.currentTimeMillis()),
                        ),
                        mediaList = videos,
                    ),
                ),
                preferences = ApplicationPreferences().copy(
                    mediaViewMode = MediaViewMode.FOLDER_TREE,
                    mediaLayoutMode = MediaLayoutMode.GRID,
                ),
            ),
        )
    }
}

@Preview
@Composable
private fun ButtonPreview() {
    Surface {
        TextIconToggleButton(
            text = "Title",
            icon = NextIcons.Title,
            onClick = {},
        )
    }
}

@DayNightPreview
@Composable
private fun MediaPickerNoVideosFoundPreview() {
    NextPlayerTheme {
        Surface {
            MediaPickerScreen(
                uiState = MediaPickerUiState(
                    folderName = null,
                    mediaDataState = DataState.Success(null),
                    preferences = ApplicationPreferences(),
                ),
            )
        }
    }
}

@DayNightPreview
@Composable
private fun MediaPickerLoadingPreview() {
    NextPlayerTheme {
        Surface {
            MediaPickerScreen(
                uiState = MediaPickerUiState(
                    folderName = null,
                    mediaDataState = DataState.Loading,
                    preferences = ApplicationPreferences(),
                ),
            )
        }
    }
}
