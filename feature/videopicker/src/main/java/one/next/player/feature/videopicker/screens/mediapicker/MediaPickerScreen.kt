package one.next.player.feature.videopicker.screens.mediapicker

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
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
import one.next.player.core.ui.theme.OnePlayerTheme
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
    onFolderClick: (folderPath: String, screenMode: MediaPickerScreenMode) -> Unit,
    onRecycleBinClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCloudClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onExitAppClick: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MediaPickerScreen(
        uiState = uiState,
        onPlayVideo = onPlayVideo,
        onNavigateUp = onNavigateUp,
        onNavigateHome = onNavigateHome,
        onFolderClick = onFolderClick,
        onRecycleBinClick = onRecycleBinClick,
        onSearchClick = onSearchClick,
        onCloudClick = onCloudClick,
        onSettingsClick = onSettingsClick,
        onExitAppClick = onExitAppClick,
        onEvent = viewModel::onEvent,
    )
}

internal fun shouldEnableTitleLongPressHomeNavigation(
    isInSelectionMode: Boolean,
    folderName: String?,
    shouldNavigateHomeOnTitleLongPress: Boolean,
): Boolean {
    if (isInSelectionMode) return false
    if (folderName == null) return false
    return shouldNavigateHomeOnTitleLongPress
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaPickerScreen(
    uiState: MediaPickerUiState,
    onNavigateUp: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onPlayVideo: (Uri) -> Unit = {},
    onFolderClick: (String, MediaPickerScreenMode) -> Unit = { _, _ -> },
    onRecycleBinClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onCloudClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onExitAppClick: () -> Unit = {},
    onEvent: (MediaPickerUiEvent) -> Unit = {},
) {
    val selectionManager = rememberSelectionManager()
    val permissionState = rememberRuntimePermissionState(permission = storagePermission)
    val lazyGridState = rememberLazyGridState()
    var restoredFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
    val selectVideoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { it?.let { onPlayVideo(it) } },
    )

    var shouldShowQuickSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var shouldShowMainMenu by rememberSaveable { mutableStateOf(false) }
    var shouldShowUrlDialog by rememberSaveable { mutableStateOf(false) }

    var showRenameActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var showInfoActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var shouldShowDeleteVideosConfirmation by rememberSaveable { mutableStateOf(false) }

    val isLibraryMode = uiState.screenMode == MediaPickerScreenMode.LIBRARY
    val isTitleLongPressHomeNavigationEnabled = shouldEnableTitleLongPressHomeNavigation(
        isInSelectionMode = selectionManager.isInSelectionMode,
        folderName = uiState.folderName,
        shouldNavigateHomeOnTitleLongPress = uiState.preferences.shouldNavigateHomeOnTitleLongPress,
    )

    val isRecycleBinMode = uiState.screenMode == MediaPickerScreenMode.RECYCLE_BIN
    val shouldShowRecycleBinEntry = isLibraryMode &&
        uiState.folderName == null &&
        uiState.preferences.isRecycleBinEnabled
    val deleteAction = when {
        isRecycleBinMode -> MediaPickerDeleteAction.PermanentlyDelete
        uiState.preferences.isRecycleBinEnabled -> MediaPickerDeleteAction.MoveToRecycleBin
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
                onTitleLongClick = if (isTitleLongPressHomeNavigationEnabled) {
                    { onNavigateHome() }
                } else {
                    null
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
                            if (shouldShowRecycleBinEntry) {
                                IconButton(onClick = onRecycleBinClick) {
                                    Icon(
                                        imageVector = NextIcons.DeleteSweep,
                                        contentDescription = stringResource(id = R.string.recycle_bin),
                                    )
                                }
                            }
                            IconButton(
                                onClick = { shouldShowQuickSettingsDialog = true },
                                modifier = Modifier.testTag("btn_quick_settings"),
                            ) {
                                Icon(
                                    imageVector = NextIcons.DashBoard,
                                    contentDescription = stringResource(id = R.string.quick_settings),
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { shouldShowMainMenu = true },
                                    modifier = Modifier.testTag("btn_main_menu"),
                                ) {
                                    Icon(
                                        imageVector = NextIcons.ExpandMore,
                                        contentDescription = stringResource(id = R.string.menu),
                                    )
                                }
                                DropdownMenu(
                                    expanded = shouldShowMainMenu,
                                    onDismissRequest = { shouldShowMainMenu = false },
                                    modifier = Modifier.testTag("menu_main_actions"),
                                    shape = RoundedCornerShape(10.dp),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    ),
                                ) {
                                    MainMenuItem(
                                        text = stringResource(id = R.string.open_network_stream),
                                        icon = NextIcons.Link,
                                        testTag = "item_main_menu_network_stream",
                                        onClick = {
                                            shouldShowMainMenu = false
                                            shouldShowUrlDialog = true
                                        },
                                    )
                                    MainMenuItem(
                                        text = stringResource(id = R.string.open_local_video),
                                        icon = NextIcons.FileOpen,
                                        testTag = "item_main_menu_local_video",
                                        onClick = {
                                            shouldShowMainMenu = false
                                            selectVideoFileLauncher.launch("video/*")
                                        },
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                                    )
                                    MainMenuItem(
                                        text = stringResource(id = R.string.cloud_servers),
                                        icon = NextIcons.Cloud,
                                        testTag = "item_main_menu_cloud",
                                        onClick = {
                                            shouldShowMainMenu = false
                                            onCloudClick()
                                        },
                                    )
                                    MainMenuItem(
                                        text = stringResource(id = R.string.settings),
                                        icon = NextIcons.Settings,
                                        testTag = "item_main_menu_settings",
                                        onClick = {
                                            shouldShowMainMenu = false
                                            onSettingsClick()
                                        },
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                                    )
                                    MainMenuItem(
                                        text = stringResource(id = R.string.exit),
                                        icon = NextIcons.Close,
                                        testTag = "item_main_menu_exit_app",
                                        onClick = {
                                            shouldShowMainMenu = false
                                            onExitAppClick()
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            SelectionActionsSheet(
                shouldShowSelectionActionsSheet = selectionManager.isInSelectionMode &&
                    (selectionManager.allSelectedVideos.isNotEmpty() || selectionManager.selectedFolders.isNotEmpty()),
                deleteAction = deleteAction,
                shouldShowRestoreAction = isRecycleBinMode,
                shouldShowRenameAction = selectionManager.isSingleVideoSelected && isLibraryMode,
                shouldShowInfoAction = selectionManager.isSingleVideoSelected,
                shouldShowExcludeAction = selectionManager.selectedFolders.isNotEmpty() && isLibraryMode,
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
                        MediaService.shouldAskSystemForDeleteConfirmation()
                    ) {
                        onEvent(MediaPickerUiEvent.PermanentlyDeleteVideos(selectionManager.allSelectedVideos.map { it.uriString }))
                        selectionManager.exitSelectionMode()
                    } else {
                        shouldShowDeleteVideosConfirmation = true
                    }
                },
                onExcludeAction = {
                    val paths = selectionManager.selectedFolders.map { it.path }
                    onEvent(MediaPickerUiEvent.ExcludeFolders(paths))
                    selectionManager.exitSelectionMode()
                },
            )
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
                    shouldShowRationale = permissionState.shouldShowRationale,
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
                                isRefreshing = uiState.isRefreshing,
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

    LaunchedEffect(uiState.folderPath) {
        restoredFolderPath = null
    }

    LaunchedEffect(
        uiState.folderPath,
        uiState.preferences.shouldRestoreLastPlayedMediaInFolders,
        uiState.mediaDataState,
    ) {
        if (!uiState.preferences.shouldRestoreLastPlayedMediaInFolders) return@LaunchedEffect
        val folderPath = uiState.folderPath ?: return@LaunchedEffect
        if (restoredFolderPath == folderPath) return@LaunchedEffect
        val rootFolder = (uiState.mediaDataState as? DataState.Success)?.value ?: return@LaunchedEffect
        val lastPlayedMediaUri = uiState.preferences.localFolderLastPlayedMediaUris[folderPath] ?: return@LaunchedEffect
        val targetIndex = rootFolder.mediaList.indexOfFirst { video -> video.uriString == lastPlayedMediaUri }
        if (targetIndex < 0) return@LaunchedEffect

        val scrollIndex = when (uiState.preferences.mediaViewMode) {
            MediaViewMode.VIDEOS -> targetIndex
            MediaViewMode.FOLDERS,
            MediaViewMode.FOLDER_TREE,
            -> {
                val folderSectionSize = rootFolder.folderList.size
                val folderHeaderOffset = if (rootFolder.folderList.isNotEmpty()) 1 else 0
                val spacerOffset = if (
                    uiState.preferences.mediaViewMode == MediaViewMode.FOLDER_TREE &&
                    rootFolder.folderList.isNotEmpty()
                ) {
                    1
                } else {
                    0
                }
                val videoHeaderOffset = if (rootFolder.mediaList.isNotEmpty()) 1 else 0
                folderHeaderOffset + folderSectionSize + spacerOffset + videoHeaderOffset + targetIndex
            }
        }
        lazyGridState.scrollToItem(scrollIndex)
        restoredFolderPath = folderPath
    }

    LaunchedEffect(selectionManager.isInSelectionMode) {
        if (selectionManager.isInSelectionMode) {
            shouldShowMainMenu = false
        }
    }

    BackHandler(enabled = selectionManager.isInSelectionMode) {
        selectionManager.exitSelectionMode()
    }

    if (shouldShowQuickSettingsDialog) {
        QuickSettingsDialog(
            applicationPreferences = uiState.preferences,
            onDismiss = { shouldShowQuickSettingsDialog = false },
            updatePreferences = { onEvent(MediaPickerUiEvent.UpdateMenu(it)) },
        )
    }

    if (shouldShowUrlDialog) {
        NetworkUrlDialog(
            onDismiss = { shouldShowUrlDialog = false },
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

    if (shouldShowDeleteVideosConfirmation) {
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
                selectionManager.exitSelectionMode()
                shouldShowDeleteVideosConfirmation = false
            },
            onCancel = { shouldShowDeleteVideosConfirmation = false },
        )
    }
}

@Composable
private fun MainMenuItem(
    text: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .testTag(testTag),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    )
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
    shouldShowSelectionActionsSheet: Boolean,
    deleteAction: MediaPickerDeleteAction,
    shouldShowRestoreAction: Boolean,
    shouldShowRenameAction: Boolean,
    shouldShowInfoAction: Boolean,
    shouldShowExcludeAction: Boolean,
    onRestoreAction: () -> Unit,
    onRenameAction: () -> Unit,
    onInfoAction: () -> Unit,
    onShareAction: () -> Unit,
    onDeleteAction: () -> Unit,
    onExcludeAction: () -> Unit,
) {
    // 退出动画期间冻结按钮可见性，避免按钮先于操作栏消失
    val frozenShowRestore = rememberUpdatedStateWhenVisible(shouldShowSelectionActionsSheet, shouldShowRestoreAction)
    val frozenShowRename = rememberUpdatedStateWhenVisible(shouldShowSelectionActionsSheet, shouldShowRenameAction)
    val frozenShowInfo = rememberUpdatedStateWhenVisible(shouldShowSelectionActionsSheet, shouldShowInfoAction)
    val frozenShowExclude = rememberUpdatedStateWhenVisible(shouldShowSelectionActionsSheet, shouldShowExcludeAction)

    AnimatedVisibility(
        visible = shouldShowSelectionActionsSheet,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 40.dp, vertical = 6.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.96f),
                shape = RoundedCornerShape(999.dp),
                shadowElevation = 2.dp,
                tonalElevation = 0.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (frozenShowRestore) {
                        SelectionActionItem(
                            imageVector = NextIcons.ArrowUpward,
                            text = stringResource(id = R.string.restore),
                            onClick = onRestoreAction,
                        )
                    }
                    if (frozenShowRename) {
                        SelectionActionItem(
                            imageVector = NextIcons.Edit,
                            text = stringResource(id = R.string.rename),
                            onClick = onRenameAction,
                        )
                    }
                    if (frozenShowInfo) {
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
                    if (frozenShowExclude) {
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
                                    if (frozenShowRestore) {
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
    }
}

@Composable
private fun RowScope.SelectionActionItem(
    imageVector: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = text,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
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
                isEnabled = url.isNotBlank(),
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
    OnePlayerTheme {
        MediaPickerScreen(
            uiState = MediaPickerUiState(
                folderPath = null,
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
    OnePlayerTheme {
        Surface {
            MediaPickerScreen(
                uiState = MediaPickerUiState(
                    folderPath = null,
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
    OnePlayerTheme {
        Surface {
            MediaPickerScreen(
                uiState = MediaPickerUiState(
                    folderPath = null,
                    folderName = null,
                    mediaDataState = DataState.Loading,
                    preferences = ApplicationPreferences(),
                ),
            )
        }
    }
}

// 仅在 visible=true 时跟踪 value，退出动画期间保持最后值
@Composable
private fun rememberUpdatedStateWhenVisible(isVisible: Boolean, value: Boolean): Boolean {
    var frozen by remember { mutableStateOf(value) }
    if (isVisible) frozen = value
    return frozen
}
