package one.next.player.feature.videopicker.screens.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.next.player.core.model.RemoteServer
import one.next.player.core.model.ServerProtocol
import one.next.player.core.ui.R
import one.next.player.core.ui.components.CancelButton
import one.next.player.core.ui.components.NextDialog
import one.next.player.core.ui.components.NextSegmentedListItem
import one.next.player.core.ui.components.NextTopAppBar
import one.next.player.core.ui.components.PreferenceSwitch
import one.next.player.core.ui.designsystem.NextIcons
import one.next.player.core.ui.extensions.copy
import one.next.player.core.ui.extensions.withBottomFallback

@Composable
fun CloudHomeRoute(
    viewModel: CloudHomeViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
    onServerClick: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CloudHomeScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onServerClick = onServerClick,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CloudHomeScreen(
    uiState: CloudHomeUiState,
    onNavigateUp: () -> Unit = {},
    onServerClick: (Long) -> Unit = {},
    onEvent: (CloudHomeEvent) -> Unit = {},
) {
    var shouldShowAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingServer: RemoteServer? by remember { mutableStateOf(null) }
    var deletingServer: RemoteServer? by remember { mutableStateOf(null) }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(R.string.cloud_servers),
                fontWeight = FontWeight.Bold,
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
                actions = {},
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { shouldShowAddDialog = true },
                modifier = Modifier
                    .padding(end = 16.dp, bottom = 16.dp)
                    .testTag("cloud_add_server_fab"),
            ) {
                Icon(
                    imageVector = NextIcons.Add,
                    contentDescription = stringResource(R.string.add_server),
                )
            }
        },
        contentWindowInsets = WindowInsets.displayCutout,
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
                if (uiState.servers.isEmpty()) {
                    EmptyCloudHomeContent(contentPadding = contentPadding)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        items(uiState.servers, key = { it.id }) { server ->
                            val index = uiState.servers.indexOf(server)
                            ServerListItem(
                                server = server,
                                isFirstItem = index == 0,
                                isLastItem = index == uiState.servers.lastIndex,
                                onClick = { onServerClick(server.id) },
                                onEditClick = { editingServer = server },
                                onDeleteClick = { deletingServer = server },
                            )
                        }
                    }
                }
            }
        }
    }

    if (shouldShowAddDialog) {
        AddEditServerDialog(
            server = null,
            onDismiss = { shouldShowAddDialog = false },
            onSave = { server ->
                onEvent(CloudHomeEvent.SaveServer(server))
                shouldShowAddDialog = false
            },
        )
    }

    editingServer?.let { server ->
        AddEditServerDialog(
            server = server,
            onDismiss = { editingServer = null },
            onSave = { updated ->
                onEvent(CloudHomeEvent.SaveServer(updated))
                editingServer = null
            },
        )
    }

    deletingServer?.let { server ->
        NextDialog(
            onDismissRequest = { deletingServer = null },
            title = { Text(stringResource(R.string.delete_server)) },
            content = {
                Text(stringResource(R.string.delete_server_confirmation, server.name))
            },
            confirmButton = {
                TextButton(onClick = {
                    onEvent(CloudHomeEvent.DeleteServer(server.id))
                    deletingServer = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { CancelButton(onClick = { deletingServer = null }) },
        )
    }
}

@Composable
private fun EmptyCloudHomeContent(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
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
                imageVector = NextIcons.Cloud,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.no_servers_configured),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ServerListItem(
    server: RemoteServer,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    NextSegmentedListItem(
        onClick = onClick,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        modifier = Modifier.testTag("cloud_server_item_${server.id}"),
        leadingContent = {
            Icon(
                imageVector = NextIcons.Cloud,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        supportingContent = {
            Text(
                text = "${server.protocol.name} · ${server.host}${server.port?.let { ":$it" } ?: ""}",
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = NextIcons.Edit,
                        contentDescription = stringResource(R.string.edit_server),
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = NextIcons.Delete,
                        contentDescription = stringResource(R.string.delete_server),
                    )
                }
            }
        },
        content = {
            Text(text = server.name.ifBlank { server.host })
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditServerDialog(
    server: RemoteServer?,
    onDismiss: () -> Unit,
    onSave: (RemoteServer) -> Unit,
) {
    val isEditing = server != null
    var name by rememberSaveable { mutableStateOf(server?.name ?: "") }
    var protocol by rememberSaveable { mutableStateOf(server?.protocol ?: ServerProtocol.WEBDAV) }
    var host by rememberSaveable { mutableStateOf(server?.host ?: "") }
    var port by rememberSaveable { mutableStateOf(server?.port?.toString() ?: "") }
    var path by rememberSaveable { mutableStateOf(server?.path ?: "/") }
    var username by rememberSaveable { mutableStateOf(server?.username ?: "") }
    var password by rememberSaveable { mutableStateOf(server?.password ?: "") }
    var isProxyEnabled by rememberSaveable { mutableStateOf(server?.isProxyEnabled ?: false) }
    var proxyHost by rememberSaveable { mutableStateOf(server?.proxyHost ?: "") }
    var proxyPort by rememberSaveable { mutableStateOf(server?.proxyPort?.toString() ?: "") }
    var isProtocolExpanded by rememberSaveable { mutableStateOf(false) }

    NextDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (isEditing) R.string.edit_server else R.string.add_server,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExposedDropdownMenuBox(
                    expanded = isProtocolExpanded,
                    onExpandedChange = { isProtocolExpanded = it },
                ) {
                    OutlinedTextField(
                        value = protocol.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.server_protocol)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isProtocolExpanded) },
                        modifier = Modifier
                            .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = isProtocolExpanded,
                        onDismissRequest = { isProtocolExpanded = false },
                    ) {
                        ServerProtocol.entries.forEach { proto ->
                            DropdownMenuItem(
                                text = { Text(proto.name) },
                                onClick = {
                                    protocol = proto
                                    isProtocolExpanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.server_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(stringResource(R.string.server_host)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.server_port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(140.dp),
                    )
                }
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.server_path)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.server_username)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.server_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = stringResource(R.string.proxy_settings),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                )
                PreferenceSwitch(
                    title = stringResource(R.string.proxy_enabled),
                    description = stringResource(R.string.proxy_settings),
                    icon = NextIcons.Link,
                    isChecked = isProxyEnabled,
                    onClick = { isProxyEnabled = !isProxyEnabled },
                    isFirstItem = true,
                    isLastItem = !isProxyEnabled,
                )
                if (isProxyEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = proxyHost,
                            onValueChange = { proxyHost = it },
                            label = { Text(stringResource(R.string.proxy_host)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = proxyPort,
                            onValueChange = { proxyPort = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.proxy_port)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(140.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = host.isNotBlank(),
                onClick = {
                    val result = RemoteServer(
                        id = server?.id ?: 0,
                        name = name.trim(),
                        protocol = protocol,
                        host = host.trim(),
                        port = port.toIntOrNull(),
                        path = path.ifBlank { "/" },
                        username = username,
                        password = password,
                        isProxyEnabled = isProxyEnabled,
                        proxyHost = proxyHost.trim(),
                        proxyPort = proxyPort.toIntOrNull(),
                    )
                    onSave(result)
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}
