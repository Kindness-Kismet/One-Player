package one.next.player.settings.screens.about

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import one.next.player.core.common.Logger
import one.next.player.core.ui.R
import one.next.player.core.ui.components.NextSegmentedListItem
import one.next.player.core.ui.components.NextTopAppBar
import one.next.player.core.ui.designsystem.NextIcons
import one.next.player.core.ui.extensions.plus
import one.next.player.core.ui.extensions.withBottomFallback
import one.next.player.feature.settings.R as SettingsR

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibrariesScreen(
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val libs = remember(context) { loadLibraries(context) }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.libraries),
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
        if (libs == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding.withBottomFallback())
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(id = R.string.unknown_error),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding.withBottomFallback() + PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            itemsIndexed(libs.libraries, key = { _, library -> library.uniqueId }) { index, library ->
                NextSegmentedListItem(
                    content = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = library.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            library.artifactVersion?.let {
                                Text(text = it)
                            }
                        }
                    },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = library.developers.takeIf { it.isNotEmpty() }
                                    ?.mapNotNull { it.name }
                                    ?.joinToString(", ")
                                    ?: library.organization?.name ?: "",
                            )
                            FlowRow(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                library.licenses.forEach {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(text = it.name, modifier = Modifier.padding(horizontal = 2.dp))
                                    }
                                }
                            }
                        }
                    },
                    isFirstItem = index == 0,
                    isLastItem = index == libs.libraries.lastIndex,
                    onClick = {
                        library.website?.takeIf { it.isNotBlank() }?.let {
                            uriHandler.openUriOrShowToast(uri = it, context = context)
                        }
                    },
                )
            }
        }
    }
}

private fun loadLibraries(context: Context): Libs? = try {
    val librariesJson = context.resources.openRawResource(SettingsR.raw.aboutlibraries)
        .bufferedReader()
        .use { it.readText() }

    Libs.Builder()
        .withJson(librariesJson)
        .build()
} catch (throwable: Throwable) {
    Logger.error(TAG, "Failed to load libraries metadata", throwable)
    null
}

private const val TAG = "LibrariesScreen"
