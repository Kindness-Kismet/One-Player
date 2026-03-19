package one.next.player.settings.screens.privacy

import android.os.Build
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.next.player.core.ui.R
import one.next.player.core.ui.components.ListSectionTitle
import one.next.player.core.ui.components.NextTopAppBar
import one.next.player.core.ui.components.PreferenceSwitch
import one.next.player.core.ui.designsystem.NextIcons
import one.next.player.core.ui.extensions.withBottomFallback
import one.next.player.core.ui.theme.OnePlayerTheme

@Composable
fun PrivacyProtectionScreen(
    onNavigateUp: () -> Unit,
    viewModel: PrivacyProtectionViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    PrivacyProtectionContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PrivacyProtectionContent(
    uiState: PrivacyProtectionUiState,
    onNavigateUp: () -> Unit,
    onEvent: (PrivacyProtectionUiEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.privacy_protection),
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
            ListSectionTitle(text = stringResource(id = R.string.privacy_protection))
            val isHideInRecentsAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSwitch(
                    title = stringResource(id = R.string.prevent_screenshots),
                    description = stringResource(id = R.string.prevent_screenshots_description),
                    icon = NextIcons.HideSource,
                    isChecked = uiState.preferences.shouldPreventScreenshots,
                    onClick = { onEvent(PrivacyProtectionUiEvent.TogglePreventScreenshots) },
                    isFirstItem = true,
                    isLastItem = !isHideInRecentsAvailable,
                    modifier = Modifier.testTag("privacy_prevent_screenshots"),
                )
                if (isHideInRecentsAvailable) {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.hide_in_recents),
                        description = stringResource(id = R.string.hide_in_recents_description),
                        icon = NextIcons.Background,
                        isChecked = uiState.preferences.shouldHideInRecents,
                        onClick = { onEvent(PrivacyProtectionUiEvent.ToggleHideInRecents) },
                        isFirstItem = false,
                        isLastItem = true,
                        modifier = Modifier.testTag("privacy_hide_in_recents"),
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PrivacyProtectionScreenPreview() {
    OnePlayerTheme {
        PrivacyProtectionContent(
            uiState = PrivacyProtectionUiState(),
            onNavigateUp = {},
            onEvent = {},
        )
    }
}
