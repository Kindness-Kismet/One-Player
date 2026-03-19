package one.next.player.settings.screens.medialibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.next.player.core.common.createManageExternalStorageAccessIntent
import one.next.player.core.common.hasManageExternalStorageAccess
import one.next.player.core.model.ThumbnailGenerationStrategy
import one.next.player.core.ui.R
import one.next.player.core.ui.components.ClickablePreferenceItem
import one.next.player.core.ui.components.ListSectionTitle
import one.next.player.core.ui.components.NextTopAppBar
import one.next.player.core.ui.components.PreferenceSwitch
import one.next.player.core.ui.designsystem.NextIcons
import one.next.player.core.ui.extensions.withBottomFallback
import one.next.player.core.ui.theme.OnePlayerTheme

@Composable
fun MediaLibraryPreferencesScreen(
    onNavigateUp: () -> Unit,
    onFolderSettingClick: () -> Unit = {},
    onThumbnailSettingClick: () -> Unit = {},
    viewModel: MediaLibraryPreferencesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var hasAllFilesAccess by remember {
        mutableStateOf(hasManageExternalStorageAccess())
    }

    LifecycleEventEffect(event = Lifecycle.Event.ON_RESUME) {
        hasAllFilesAccess = hasManageExternalStorageAccess()
        if (hasAllFilesAccess) return@LifecycleEventEffect
        if (!uiState.preferences.shouldIgnoreNoMediaFiles && !uiState.preferences.isRecycleBinEnabled) {
            return@LifecycleEventEffect
        }

        viewModel.onEvent(MediaLibraryPreferencesUiEvent.ResetRestrictedFeatures)
    }

    MediaLibraryPreferencesContent(
        uiState = uiState,
        hasAllFilesAccess = hasAllFilesAccess,
        onNavigateUp = onNavigateUp,
        onFolderSettingClick = onFolderSettingClick,
        onThumbnailSettingClick = onThumbnailSettingClick,
        onOpenAllFilesAccessSettings = {
            context.startActivity(createManageExternalStorageAccessIntent(context))
        },
        onToggleIgnoreNoMediaFiles = {
            viewModel.onEvent(
                MediaLibraryPreferencesUiEvent.SetIgnoreNoMediaFiles(
                    shouldIgnoreNoMediaFiles = it,
                ),
            )
        },
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaLibraryPreferencesContent(
    uiState: MediaLibraryPreferencesUiState,
    hasAllFilesAccess: Boolean,
    onNavigateUp: () -> Unit,
    onFolderSettingClick: () -> Unit,
    onThumbnailSettingClick: () -> Unit,
    onOpenAllFilesAccessSettings: () -> Unit,
    onToggleIgnoreNoMediaFiles: (Boolean) -> Unit,
    onEvent: (MediaLibraryPreferencesUiEvent) -> Unit,
) {
    val preferences = uiState.preferences

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.media_library),
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
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
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(horizontal = 16.dp),
        ) {
            ListSectionTitle(text = stringResource(id = R.string.media_library))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSwitch(
                    title = stringResource(id = R.string.mark_last_played_media),
                    description = stringResource(
                        id = R.string.mark_last_played_media_desc,
                    ),
                    icon = NextIcons.Check,
                    isChecked = preferences.shouldMarkLastPlayedMedia,
                    onClick = { onEvent(MediaLibraryPreferencesUiEvent.ToggleMarkLastPlayedMedia) },
                    isFirstItem = true,
                    isLastItem = false,
                )
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.all_files_access_title),
                    description = stringResource(id = R.string.media_library_all_files_access_desc),
                    icon = NextIcons.Settings,
                    onClick = onOpenAllFilesAccessSettings,
                    isFirstItem = false,
                    isLastItem = false,
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.recycle_bin),
                    description = stringResource(id = R.string.recycle_bin_desc),
                    icon = NextIcons.DeleteSweep,
                    isEnabled = hasAllFilesAccess,
                    isChecked = preferences.isRecycleBinEnabled,
                    onClick = { onEvent(MediaLibraryPreferencesUiEvent.ToggleRecycleBinEnabled) },
                    isFirstItem = false,
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.scan))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSwitch(
                    title = stringResource(id = R.string.ignore_nomedia_files),
                    description = stringResource(id = R.string.ignore_nomedia_files_desc),
                    icon = NextIcons.HideSource,
                    isEnabled = hasAllFilesAccess,
                    isChecked = preferences.shouldIgnoreNoMediaFiles,
                    onClick = {
                        onToggleIgnoreNoMediaFiles(!preferences.shouldIgnoreNoMediaFiles)
                    },
                    isFirstItem = true,
                    isLastItem = false,
                )
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.manage_folders),
                    description = stringResource(id = R.string.manage_folders_desc),
                    icon = NextIcons.FolderOff,
                    onClick = onFolderSettingClick,
                    isFirstItem = false,
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.thumbnail))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.thumbnail_generation),
                    description = when (preferences.thumbnailGenerationStrategy) {
                        ThumbnailGenerationStrategy.FIRST_FRAME -> stringResource(id = R.string.first_frame)
                        ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE -> stringResource(R.string.frame_at_position)
                        ThumbnailGenerationStrategy.HYBRID -> stringResource(id = R.string.hybrid)
                    },
                    icon = NextIcons.Image,
                    onClick = onThumbnailSettingClick,
                    isFirstItem = true,
                    isLastItem = true,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun MediaLibraryPreferencesScreenPreview() {
    OnePlayerTheme {
        MediaLibraryPreferencesContent(
            uiState = MediaLibraryPreferencesUiState(),
            hasAllFilesAccess = false,
            onNavigateUp = {},
            onFolderSettingClick = {},
            onThumbnailSettingClick = {},
            onOpenAllFilesAccessSettings = {},
            onToggleIgnoreNoMediaFiles = {},
            onEvent = {},
        )
    }
}
