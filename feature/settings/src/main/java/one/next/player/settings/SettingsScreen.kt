package one.next.player.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import one.next.player.core.ui.R
import one.next.player.core.ui.components.ClickablePreferenceItem
import one.next.player.core.ui.components.NextTopAppBar
import one.next.player.core.ui.designsystem.NextIcons
import one.next.player.core.ui.extensions.withBottomFallback

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onNavigateUp: (() -> Unit)? = null,
    onItemClick: (Setting) -> Unit,
) {
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // resolve 标题、描述和子设置项文本，全部用于搜索匹配
    val resolvedRows = SettingRow.entries.map { row ->
        val subTexts = row.subSettingResIds.map { stringResource(it) }
        ResolvedSettingRow(
            row = row,
            title = stringResource(row.titleResId),
            description = stringResource(row.descriptionResId),
            searchableTexts = subTexts,
        )
    }

    val filteredRows = remember(searchQuery, resolvedRows) {
        if (searchQuery.isBlank()) {
            resolvedRows
        } else {
            val query = searchQuery.lowercase()
            resolvedRows.filter { it.matches(query) }
        }
    }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                label = "settings_top_bar",
            ) { isSearching ->
                if (isSearching) {
                    SettingsSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = {
                            isSearchActive = false
                            searchQuery = ""
                        },
                    )
                } else {
                    NextTopAppBar(
                        title = stringResource(id = R.string.settings),
                        navigationIcon = if (onNavigateUp != null) {
                            {
                                FilledTonalIconButton(onClick = onNavigateUp) {
                                    Icon(
                                        imageVector = NextIcons.ArrowBack,
                                        contentDescription = stringResource(id = R.string.navigate_up),
                                    )
                                }
                            }
                        } else {
                            {}
                        },
                        actions = {
                            FilledTonalIconButton(onClick = { isSearchActive = true }) {
                                Icon(
                                    imageVector = NextIcons.Search,
                                    contentDescription = stringResource(R.string.search),
                                )
                            }
                        },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            filteredRows.forEachIndexed { index, resolved ->
                ClickablePreferenceItem(
                    title = resolved.title,
                    description = resolved.description,
                    icon = resolved.row.icon,
                    onClick = { onItemClick(resolved.row.setting) },
                    isFirstItem = index == 0,
                    isLastItem = index == filteredRows.lastIndex,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    NextTopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_settings)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("settings_search_field"),
            )
        },
        navigationIcon = {
            FilledTonalIconButton(onClick = onClose) {
                Icon(
                    imageVector = NextIcons.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_up),
                )
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = NextIcons.Close,
                        contentDescription = null,
                    )
                }
            }
        },
    )
}

private data class ResolvedSettingRow(
    val row: SettingRow,
    val title: String,
    val description: String,
    val searchableTexts: List<String>,
) {
    fun matches(query: String): Boolean = title.lowercase().contains(query) ||
        description.lowercase().contains(query) ||
        searchableTexts.any { it.lowercase().contains(query) }
}

enum class Setting {
    APPEARANCE,
    MEDIA_LIBRARY,
    PLAYER,
    GESTURES,
    DECODER,
    AUDIO,
    SUBTITLE,
    PRIVACY,
    GENERAL,
    ABOUT,
}

// 子设置项的字符串资源 ID，用于搜索索引
internal enum class SettingRow(
    val titleResId: Int,
    val descriptionResId: Int,
    val icon: ImageVector,
    val setting: Setting,
    val subSettingResIds: List<Int> = emptyList(),
) {
    APPEARANCE(
        titleResId = R.string.appearance_name,
        descriptionResId = R.string.appearance_description,
        icon = NextIcons.Appearance,
        setting = Setting.APPEARANCE,
        subSettingResIds = listOf(
            R.string.dark_theme,
            R.string.dynamic_theme,
            R.string.high_contrast_dark_theme,
            R.string.app_language,
            R.string.app_language_description,
            R.string.show_cloud_tab,
        ),
    ),
    MEDIA_LIBRARY(
        titleResId = R.string.media_library,
        descriptionResId = R.string.media_library_description,
        icon = NextIcons.Movie,
        setting = Setting.MEDIA_LIBRARY,
        subSettingResIds = listOf(
            R.string.manage_folders,
            R.string.force_rescan_storage,
            R.string.ignore_nomedia_files,
            R.string.thumbnail_generation,
            R.string.mark_last_played_media,
            R.string.recycle_bin,
        ),
    ),
    PLAYER(
        titleResId = R.string.player_name,
        descriptionResId = R.string.player_description,
        icon = NextIcons.Player,
        setting = Setting.PLAYER,
        subSettingResIds = listOf(
            R.string.resume,
            R.string.default_playback_speed,
            R.string.autoplay_settings,
            R.string.pip_settings,
            R.string.background_play,
            R.string.fast_seek,
            R.string.screen_rotation,
            R.string.player_screen_orientation,
            R.string.controller_timeout,
            R.string.seek_increment,
            R.string.loop_mode,
            R.string.skip_silence,
        ),
    ),
    GESTURES(
        titleResId = R.string.gestures_name,
        descriptionResId = R.string.gestures_description,
        icon = NextIcons.SwipeHorizontal,
        setting = Setting.GESTURES,
        subSettingResIds = listOf(
            R.string.double_tap,
            R.string.long_press_gesture,
            R.string.seek_gesture,
            R.string.zoom_gesture,
            R.string.volume_gesture,
            R.string.brightness_gesture,
            R.string.pan_gesture,
        ),
    ),
    DECODER(
        titleResId = R.string.decoder,
        descriptionResId = R.string.decoder_desc,
        icon = NextIcons.Decoder,
        setting = Setting.DECODER,
        subSettingResIds = listOf(
            R.string.decoder_priority,
            R.string.video_software_decoders,
            R.string.prefer_device_decoders,
            R.string.prefer_app_decoders,
        ),
    ),
    AUDIO(
        titleResId = R.string.audio,
        descriptionResId = R.string.audio_desc,
        icon = NextIcons.Audio,
        setting = Setting.AUDIO,
        subSettingResIds = listOf(
            R.string.preferred_audio_lang,
            R.string.require_audio_focus,
            R.string.pause_on_headset_disconnect,
            R.string.system_volume_panel,
            R.string.volume_boost,
        ),
    ),
    SUBTITLE(
        titleResId = R.string.subtitle,
        descriptionResId = R.string.subtitle_desc,
        icon = NextIcons.Subtitle,
        setting = Setting.SUBTITLE,
        subSettingResIds = listOf(
            R.string.preferred_subtitle_lang,
            R.string.subtitle_auto_load,
            R.string.subtitle_font,
            R.string.subtitle_text_size,
            R.string.subtitle_text_bold,
            R.string.subtitle_text_encoding,
            R.string.subtitle_background,
            R.string.embedded_styles,
            R.string.system_caption_style,
        ),
    ),
    PRIVACY(
        titleResId = R.string.privacy_protection,
        descriptionResId = R.string.privacy_protection_description,
        icon = NextIcons.HideSource,
        setting = Setting.PRIVACY,
        subSettingResIds = listOf(
            R.string.prevent_screenshots,
            R.string.hide_in_recents,
        ),
    ),
    GENERAL(
        titleResId = R.string.general_name,
        descriptionResId = R.string.general_description,
        icon = NextIcons.ExtraSettings,
        setting = Setting.GENERAL,
        subSettingResIds = listOf(
            R.string.delete_thumbnail_cache,
            R.string.reset_settings,
            R.string.backup_settings,
            R.string.restore_settings,
        ),
    ),
    ABOUT(
        titleResId = R.string.about_name,
        descriptionResId = R.string.about_description,
        icon = NextIcons.Info,
        setting = Setting.ABOUT,
        subSettingResIds = listOf(
            R.string.device_info,
            R.string.libraries,
            R.string.check_for_updates,
        ),
    ),
}
