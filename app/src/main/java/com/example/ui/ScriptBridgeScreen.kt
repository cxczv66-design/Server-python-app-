package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.LogEntry
import com.example.model.LogLevel
import com.example.model.ServerStatus
import com.example.model.ServerUiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.launch

private enum class BridgeTab(val labelRes: Int) {
    CONSOLE(R.string.tab_console),
    SCRIPT(R.string.tab_script),
    GUIDE(R.string.tab_guide),
}

@Composable
fun ScriptBridgeScreen(
    uiState: ServerUiState,
    onToggleServer: () -> Unit,
    onPortChange: (String) -> Unit,
    onTailscaleUrlChange: (String) -> Unit,
    onRefreshNetwork: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onPickLuaFile: () -> Unit,
    onReloadLuaFile: () -> Unit,
    onLoadSampleScript: () -> Unit,
    onAutoReloadToggle: (Boolean) -> Unit,
    onSendWsCommand: (String) -> Unit,
    onPushScriptToClients: () -> Unit,
    onClearLogs: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(BridgeTab.CONSOLE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        scope.launch {
            snackbarHostState.showSnackbar("Copied $label")
        }
    }

    val glowColor = if (uiState.status == ServerStatus.RUNNING) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = Offset(size.width * 0.25f, 0f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            },
    ) {
        val isWideScreen = maxWidth >= 800.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        tonalElevation = 4.dp,
                    ) {
                        BridgeTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                modifier = Modifier.semantics {
                                    contentDescription = "Navigate to ${tab.name.lowercase()} tab"
                                },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        BridgeTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = stringResource(tab.labelRes),
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    when (selectedTab) {
                        BridgeTab.CONSOLE -> {
                            if (isWideScreen) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(0.48f)
                                            .fillMaxHeight()
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        AppHeaderBar(uiState = uiState)
                                        ServerControlCard(
                                            uiState = uiState,
                                            onToggleServer = onToggleServer,
                                            onPortChange = onPortChange,
                                            onOpenBatterySettings = onOpenBatterySettings,
                                        )
                                        TailscaleCard(
                                            uiState = uiState,
                                            onTailscaleUrlChange = onTailscaleUrlChange,
                                            onRefreshNetwork = onRefreshNetwork,
                                            onCopy = ::copyToClipboard,
                                        )
                                        ScriptQuickBar(
                                            uiState = uiState,
                                            onPickLuaFile = onPickLuaFile,
                                            onReloadLuaFile = onReloadLuaFile,
                                            onPushScriptToClients = onPushScriptToClients,
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(0.52f)
                                            .fillMaxHeight(),
                                    ) {
                                        LiveLogPanel(
                                            logs = uiState.logs,
                                            isRunning = uiState.status == ServerStatus.RUNNING,
                                            onSendWsCommand = onSendWsCommand,
                                            onClearLogs = onClearLogs,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = 640.dp)
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    AppHeaderBar(uiState = uiState)
                                    ServerControlCard(
                                        uiState = uiState,
                                        onToggleServer = onToggleServer,
                                        onPortChange = onPortChange,
                                        onOpenBatterySettings = onOpenBatterySettings,
                                    )
                                    TailscaleCard(
                                        uiState = uiState,
                                        onTailscaleUrlChange = onTailscaleUrlChange,
                                        onRefreshNetwork = onRefreshNetwork,
                                        onCopy = ::copyToClipboard,
                                    )
                                    ScriptQuickBar(
                                        uiState = uiState,
                                        onPickLuaFile = onPickLuaFile,
                                        onReloadLuaFile = onReloadLuaFile,
                                        onPushScriptToClients = onPushScriptToClients,
                                    )
                                    LiveLogPanel(
                                        logs = uiState.logs,
                                        isRunning = uiState.status == ServerStatus.RUNNING,
                                        onSendWsCommand = onSendWsCommand,
                                        onClearLogs = onClearLogs,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(340.dp),
                                    )
                                }
                            }
                        }

                        BridgeTab.SCRIPT -> {
                            ScriptInspectorTab(
                                uiState = uiState,
                                onPickLuaFile = onPickLuaFile,
                                onReloadLuaFile = onReloadLuaFile,
                                onLoadSampleScript = onLoadSampleScript,
                                onAutoReloadToggle = onAutoReloadToggle,
                                onPushScriptToClients = onPushScriptToClients,
                                onCopy = ::copyToClipboard,
                                modifier = Modifier
                                    .widthIn(max = 800.dp)
                                    .fillMaxSize()
                                    .padding(vertical = 12.dp),
                            )
                        }

                        BridgeTab.GUIDE -> {
                            ConnectionGuideTab(
                                uiState = uiState,
                                onCopy = ::copyToClipboard,
                                onOpenBatterySettings = onOpenBatterySettings,
                                modifier = Modifier
                                    .widthIn(max = 800.dp)
                                    .fillMaxSize()
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: BridgeTab, selected: Boolean) = when (tab) {
    BridgeTab.CONSOLE -> if (selected) Icons.Filled.Terminal else Icons.Outlined.Terminal
    BridgeTab.SCRIPT -> if (selected) Icons.Filled.Code else Icons.Outlined.Code
    BridgeTab.GUIDE -> if (selected) Icons.Filled.Hub else Icons.Outlined.Hub
}

@Composable
private fun AppHeaderBar(uiState: ServerUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Ktor CIO • WebSocket + HTTP Bridge",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusBadge(status = uiState.status, wsClients = uiState.activeWsClients)
    }
}

@Composable
private fun StatusBadge(status: ServerStatus, wsClients: Int) {
    val isRunning = status == ServerStatus.RUNNING
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val containerColor = when (status) {
        ServerStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
        ServerStatus.STARTING -> MaterialTheme.colorScheme.tertiaryContainer
        ServerStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        ServerStatus.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val dotColor = when (status) {
        ServerStatus.RUNNING -> MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
        ServerStatus.STARTING -> MaterialTheme.colorScheme.tertiary
        ServerStatus.ERROR -> MaterialTheme.colorScheme.error
        ServerStatus.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textColor = when (status) {
        ServerStatus.RUNNING -> MaterialTheme.colorScheme.onPrimaryContainer
        ServerStatus.STARTING -> MaterialTheme.colorScheme.onTertiaryContainer
        ServerStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        ServerStatus.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = if (isRunning) "LIVE • $wsClients WS" else status.name,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
            )
        }
    }
}

@Composable
private fun ServerControlCard(
    uiState: ServerUiState,
    onToggleServer: () -> Unit,
    onPortChange: (String) -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val isRunning = uiState.status == ServerStatus.RUNNING || uiState.status == ServerStatus.STARTING
    val buttonColor by animateColorAsState(
        targetValue = if (isRunning) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "serverButtonColor",
    )
    val buttonContentColor = if (isRunning) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isRunning) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.portInput,
                    onValueChange = onPortChange,
                    enabled = !isRunning,
                    singleLine = true,
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .width(108.dp)
                        .testTag("port_input"),
                )

                Button(
                    onClick = onToggleServer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonContentColor,
                    ),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("toggle_server_button"),
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (isRunning) R.string.action_stop_server else R.string.action_start_server,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricPill(label = "Requests", value = uiState.totalRequestsServed.toString())
                MetricPill(label = "Poll Queue", value = uiState.pendingPollCount.toString())
                MetricPill(
                    label = "Battery",
                    value = if (uiState.isIgnoringBatteryOptimizations) "Unrestricted" else "Optimized",
                )
            }

            if (!uiState.isIgnoringBatteryOptimizations) {
                Surface(
                    onClick = onOpenBatterySettings,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BatteryAlert,
                            contentDescription = "Battery optimization active",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Tap to set Battery to Unrestricted so Android won't sleep the server.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            uiState.errorMessage?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TailscaleCard(
    uiState: ServerUiState,
    onTailscaleUrlChange: (String) -> Unit,
    onRefreshNetwork: () -> Unit,
    onCopy: (String, String) -> Unit,
) {
    val port = uiState.port
    val tailscaleIp = uiState.networkEndpoints.tailscaleIp
    val wifiIp = uiState.networkEndpoints.wifiIp

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Hub,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Tailscale & Endpoints",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                IconButton(
                    onClick = onRefreshNetwork,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_network_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.action_refresh_ip),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            EndpointRow(
                label = "Tailscale IP (100.64/10)",
                url = if (tailscaleIp != null) "http://$tailscaleIp:$port/script.lua" else "Not detected (enable Tailscale VPN)",
                isAvailable = tailscaleIp != null,
                onCopy = { if (tailscaleIp != null) onCopy("Tailscale Script URL", "http://$tailscaleIp:$port/script.lua") },
            )

            EndpointRow(
                label = "Same-Device Localhost",
                url = "http://127.0.0.1:$port/script.lua",
                isAvailable = true,
                onCopy = { onCopy("Localhost Script URL", "http://127.0.0.1:$port/script.lua") },
            )

            if (wifiIp != null) {
                EndpointRow(
                    label = "LAN / Wi-Fi",
                    url = "http://$wifiIp:$port/script.lua",
                    isAvailable = true,
                    onCopy = { onCopy("LAN Script URL", "http://$wifiIp:$port/script.lua") },
                )
            }

            OutlinedTextField(
                value = uiState.tailscaleFunnelUrl,
                onValueChange = onTailscaleUrlChange,
                singleLine = true,
                label = { Text(stringResource(R.string.label_tailscale_funnel)) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.hint_tailscale_funnel),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                textStyle = MaterialTheme.typography.labelMedium,
                trailingIcon = {
                    if (uiState.tailscaleFunnelUrl.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val normalized = uiState.tailscaleFunnelUrl.trimEnd('/')
                                onCopy("Funnel URL", "$normalized/script.lua")
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.action_copy),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tailscale_url_input"),
            )
        }
    }
}

@Composable
private fun EndpointRow(
    label: String,
    url: String,
    isAvailable: Boolean,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = url,
                style = MaterialTheme.typography.labelMedium,
                color = if (isAvailable) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isAvailable) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ScriptQuickBar(
    uiState: ServerUiState,
    onPickLuaFile: () -> Unit,
    onReloadLuaFile: () -> Unit,
    onPushScriptToClients: () -> Unit,
) {
    val script = uiState.loadedScript
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = script?.fileName ?: "No Lua file selected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (script != null) {
                            "${script.byteSize} bytes • loaded at ${script.lastLoadedTime} • /script.lua"
                        } else {
                            "Pick a local .lua file via Android Storage Access Framework"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (script?.uriString != null) {
                    IconButton(
                        onClick = onReloadLuaFile,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_reload_lua),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onPickLuaFile,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pick_lua_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_pick_lua))
                }

                OutlinedButton(
                    onClick = onPushScriptToClients,
                    enabled = script != null,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("push_script_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_push_script))
                }
            }
        }
    }
}

@Composable
private fun LiveLogPanel(
    logs: List<LogEntry>,
    isRunning: Boolean,
    onSendWsCommand: (String) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var commandInput by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Incoming Connections & Logs",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("clear_logs_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = stringResource(R.string.action_clear_logs),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
            ) {
                if (logs.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isRunning) {
                                "Listening for HTTP & WebSocket traffic..."
                            } else {
                                "Start the server to monitor incoming connections."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(logs, key = { it.id }) { entry ->
                            LogRow(entry = entry)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    singleLine = true,
                    placeholder = { Text("Send WS / /poll message...", style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ws_command_input"),
                )
                FilledTonalButton(
                    onClick = {
                        if (commandInput.isNotBlank()) {
                            onSendWsCommand(commandInput)
                            commandInput = ""
                        }
                    },
                    enabled = commandInput.isNotBlank(),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("ws_send_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send command",
                    )
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val badgeColor = when (entry.level) {
        LogLevel.SUCCESS -> MaterialTheme.colorScheme.primary
        LogLevel.WEBSOCKET -> MaterialTheme.colorScheme.secondary
        LogLevel.HTTP -> Color(0xFF8BE9FD)
        LogLevel.WARNING -> MaterialTheme.colorScheme.tertiary
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = entry.timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "[${entry.level.badge}]",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = badgeColor,
        )
        Text(
            text = "${entry.source}: ${entry.message}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ScriptInspectorTab(
    uiState: ServerUiState,
    onPickLuaFile: () -> Unit,
    onReloadLuaFile: () -> Unit,
    onLoadSampleScript: () -> Unit,
    onAutoReloadToggle: (Boolean) -> Unit,
    onPushScriptToClients: () -> Unit,
    onCopy: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val script = uiState.loadedScript
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = script?.fileName ?: "Lua Script Loader",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = if (script != null) {
                        "${script.byteSize} bytes • served at GET /script.lua"
                    } else {
                        "Select any local .lua file from device storage"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (script != null) {
                IconButton(onClick = { onCopy("Lua Script", script.content) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy script")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onPickLuaFile,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pick .lua")
            }
            OutlinedButton(
                onClick = onLoadSampleScript,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Text("Load Sample")
            }
            if (script?.uriString != null) {
                IconButton(
                    onClick = onReloadLuaFile,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload file")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-reload from storage on request",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Reads fresh bytes from disk whenever GET /script.lua is called",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.autoReloadScript,
                    onCheckedChange = onAutoReloadToggle,
                )
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            if (script == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No script loaded yet. Tap 'Pick .lua' to select script.lua from storage or 'Load Sample' to test.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = script.content,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                )
            }
        }

        Button(
            onClick = onPushScriptToClients,
            enabled = script != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Broadcast Script to Connected WS / Poll Clients")
        }
    }
}

@Composable
private fun ConnectionGuideTab(
    uiState: ServerUiState,
    onCopy: (String, String) -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val port = uiState.port
    val funnelBase = uiState.tailscaleFunnelUrl.trim().trimEnd('/')
    val activeBase = when {
        funnelBase.isNotBlank() -> funnelBase
        uiState.networkEndpoints.tailscaleIp != null -> "http://${uiState.networkEndpoints.tailscaleIp}:$port"
        else -> "http://127.0.0.1:$port"
    }
    val wsBase = when {
        funnelBase.startsWith("https://") -> funnelBase.replaceFirst("https://", "wss://")
        funnelBase.startsWith("http://") -> funnelBase.replaceFirst("http://", "ws://")
        uiState.networkEndpoints.tailscaleIp != null -> "ws://${uiState.networkEndpoints.tailscaleIp}:$port"
        else -> "ws://127.0.0.1:$port"
    }

    val luaLoaderSnippet = """
        -- 1. Load script via HTTP
        local src = game:HttpGet("http://127.0.0.1:$port/script.lua")
        loadstring(src)()

        -- 2. Or connect WebSocket (with HTTP /poll fallback)
        -- WS URL: $wsBase/ws
        -- Poll URL: $activeBase/poll
    """.trimIndent()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Client & Tailscale Guide",
            style = MaterialTheme.typography.titleLarge,
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Active HTTP & WebSocket Routes", style = MaterialTheme.typography.titleMedium)
                RouteSpec("GET /script.lua", "Serves your selected local .lua file as plain text")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RouteSpec("WS /ws", "Real-time bidirectional commands (send PING or GET_SCRIPT)")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RouteSpec("GET /poll", "HTTP Polling fallback returning queued commands JSON")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RouteSpec("POST /report", "Accepts client logs/status and prints them in Console")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RouteSpec("GET /health", "JSON health check with active WS client count")
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Lua Client Snippet", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { onCopy("Lua Client Snippet", luaLoaderSnippet) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy snippet")
                    }
                }
                Text(
                    text = luaLoaderSnippet,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp),
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (uiState.isIgnoringBatteryOptimizations) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier.size(20.dp),
                    )
                    Text("Background & Tailscale Notes", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "• Same-device clients can connect directly to 127.0.0.1:$port without leaving the phone.\n" +
                        "• When Tailscale VPN is connected on Android, tap Refresh IP to detect your 100.x.y.z tailnet IP.\n" +
                        "• For public HTTPS/WSS access via Tailscale Funnel, run `tailscale funnel $port` on your peer/serve node or paste your Funnel URL in the Console tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onOpenBatterySettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Open Android Battery Optimization Settings")
                }
            }
        }
    }
}

@Composable
private fun RouteSpec(route: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = route,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
