package com.example.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BridgePreferences
import com.example.data.LocalScriptLoader
import com.example.model.LogLevel
import com.example.model.NetworkEndpoints
import com.example.model.ServerStatus
import com.example.model.ServerUiState
import com.example.network.TailscaleNetworkHelper
import com.example.server.BridgeStateHolder
import com.example.service.ServerForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferences = BridgePreferences(appContext)

    private val tailscaleFunnelUrl = MutableStateFlow("")
    private val portInput = MutableStateFlow("8080")
    private val networkEndpoints = MutableStateFlow(NetworkEndpoints())
    private val isIgnoringBatteryOptimizations = MutableStateFlow(false)

    val uiState: StateFlow<ServerUiState> = combine(
        BridgeStateHolder.serverStatus,
        BridgeStateHolder.activePort,
        BridgeStateHolder.activeWsClients,
        BridgeStateHolder.pendingPollCount,
        BridgeStateHolder.totalRequestsServed,
        BridgeStateHolder.loadedScript,
        BridgeStateHolder.autoReloadEnabled,
        BridgeStateHolder.logs,
        BridgeStateHolder.errorMessage,
        tailscaleFunnelUrl,
        portInput,
        networkEndpoints,
        isIgnoringBatteryOptimizations,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ServerUiState(
            status = values[0] as ServerStatus,
            port = values[1] as Int,
            activeWsClients = values[2] as Int,
            pendingPollCount = values[3] as Int,
            totalRequestsServed = values[4] as Int,
            loadedScript = values[5] as com.example.model.LoadedScript?,
            autoReloadScript = values[6] as Boolean,
            logs = values[7] as List<com.example.model.LogEntry>,
            errorMessage = values[8] as String?,
            tailscaleFunnelUrl = values[9] as String,
            portInput = values[10] as String,
            networkEndpoints = values[11] as NetworkEndpoints,
            isIgnoringBatteryOptimizations = values[12] as Boolean,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ServerUiState(),
    )

    init {
        BridgeStateHolder.onReloadRequested = {
            val currentUri = BridgeStateHolder.loadedScript.value?.uriString
            if (!currentUri.isNullOrBlank()) {
                val result = LocalScriptLoader.loadFromUri(appContext, Uri.parse(currentUri))
                result.getOrNull()?.also { reloaded ->
                    BridgeStateHolder.setLoadedScript(reloaded)
                }
            } else {
                BridgeStateHolder.loadedScript.value
            }
        }

        viewModelScope.launch {
            val config = preferences.configFlow.first()
            tailscaleFunnelUrl.value = config.tailscaleFunnelUrl
            portInput.value = config.serverPort.toString()
            BridgeStateHolder.setActivePort(config.serverPort)
            BridgeStateHolder.setAutoReloadEnabled(config.autoReloadScript)

            if (!config.scriptUri.isNullOrBlank()) {
                val uri = Uri.parse(config.scriptUri)
                LocalScriptLoader.loadFromUri(appContext, uri)
                    .onSuccess { script ->
                        BridgeStateHolder.setLoadedScript(script)
                        BridgeStateHolder.appendLog(
                            LogLevel.INFO,
                            "Storage",
                            "Restored local script: ${script.fileName} (${script.byteSize} B)",
                        )
                    }
                    .onFailure {
                        BridgeStateHolder.appendLog(
                            LogLevel.WARNING,
                            "Storage",
                            "Previously selected script (${config.scriptName ?: "file"}) is no longer accessible",
                        )
                    }
            } else {
                BridgeStateHolder.appendLog(
                    LogLevel.INFO,
                    "ScriptBridge",
                    "Ready. Pick a .lua file and press Start Server.",
                )
            }
        }

        refreshNetworkEndpoints()
        refreshBatteryOptimizationStatus()
    }

    fun toggleServer() {
        val currentStatus = BridgeStateHolder.serverStatus.value
        if (currentStatus == ServerStatus.RUNNING || currentStatus == ServerStatus.STARTING) {
            appContext.startService(ServerForegroundService.stopIntent(appContext))
            return
        }

        val parsedPort = portInput.value.toIntOrNull()?.takeIf { it in 1024..65535 } ?: 8080
        portInput.value = parsedPort.toString()
        viewModelScope.launch {
            preferences.saveServerPort(parsedPort)
        }
        ContextCompat.startForegroundService(
            appContext,
            ServerForegroundService.startIntent(appContext, parsedPort),
        )
    }

    fun onPortInputChange(raw: String) {
        val digitsOnly = raw.filter { it.isDigit() }.take(5)
        portInput.value = digitsOnly
    }

    fun onTailscaleFunnelUrlChange(url: String) {
        tailscaleFunnelUrl.value = url
        viewModelScope.launch {
            preferences.saveTailscaleFunnelUrl(url)
        }
    }

    fun refreshNetworkEndpoints() {
        viewModelScope.launch {
            val endpoints = TailscaleNetworkHelper.detectEndpoints()
            networkEndpoints.value = endpoints
            val tsMsg = endpoints.tailscaleIp ?: "none"
            val wifiMsg = endpoints.wifiIp ?: "none"
            BridgeStateHolder.appendLog(
                LogLevel.INFO,
                "Network",
                "Interfaces refreshed — Tailscale: $tsMsg, LAN: $wifiMsg",
            )
        }
    }

    fun refreshBatteryOptimizationStatus() {
        val pm = appContext.getSystemService(PowerManager::class.java)
        isIgnoringBatteryOptimizations.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm != null) {
            pm.isIgnoringBatteryOptimizations(appContext.packageName)
        } else {
            true
        }
    }

    fun onScriptUriSelected(uri: Uri) {
        viewModelScope.launch {
            LocalScriptLoader.loadFromUri(appContext, uri)
                .onSuccess { script ->
                    BridgeStateHolder.setLoadedScript(script)
                    preferences.saveScriptSelection(uri.toString(), script.fileName)
                    BridgeStateHolder.appendLog(
                        LogLevel.SUCCESS,
                        "Storage",
                        "Loaded ${script.fileName} (${script.byteSize} B) -> GET /script.lua",
                    )
                }
                .onFailure { err ->
                    BridgeStateHolder.appendLog(
                        LogLevel.ERROR,
                        "Storage",
                        "Failed to read file: ${err.message ?: "unknown error"}",
                    )
                }
        }
    }

    fun reloadCurrentScript() {
        val uriString = BridgeStateHolder.loadedScript.value?.uriString
        if (uriString.isNullOrBlank()) {
            BridgeStateHolder.appendLog(
                LogLevel.WARNING,
                "Storage",
                "No storage file URI linked. Pick a .lua file first.",
            )
            return
        }
        onScriptUriSelected(Uri.parse(uriString))
    }

    fun loadSampleScript() {
        val sample = LocalScriptLoader.createSampleScript()
        BridgeStateHolder.setLoadedScript(sample)
        BridgeStateHolder.appendLog(
            LogLevel.INFO,
            "Script",
            "Loaded built-in sample script (${sample.byteSize} B)",
        )
    }

    fun setAutoReload(enabled: Boolean) {
        BridgeStateHolder.setAutoReloadEnabled(enabled)
        viewModelScope.launch {
            preferences.saveAutoReload(enabled)
        }
    }

    fun sendCommandOrScript(payload: String) {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            BridgeStateHolder.enqueuePollCommand(trimmed)
            val broadcastFn = BridgeStateHolder.onBroadcastRequested
            if (broadcastFn != null && BridgeStateHolder.serverStatus.value == ServerStatus.RUNNING) {
                val deliveredWs = broadcastFn.invoke(trimmed)
                BridgeStateHolder.appendLog(
                    LogLevel.WEBSOCKET,
                    "Broadcast",
                    "Sent to $deliveredWs WS client(s) & queued for /poll: ${trimmed.take(120)}",
                )
            } else {
                BridgeStateHolder.appendLog(
                    LogLevel.WARNING,
                    "Broadcast",
                    "Server stopped — queued command for /poll (${trimmed.take(80)})",
                )
            }
        }
    }

    fun pushLoadedScriptToClients() {
        val script = BridgeStateHolder.loadedScript.value
        if (script == null) {
            BridgeStateHolder.appendLog(
                LogLevel.WARNING,
                "Script",
                "Cannot push: no Lua script loaded yet",
            )
            return
        }
        sendCommandOrScript(script.content)
    }

    fun clearLogs() {
        BridgeStateHolder.clearLogs()
    }
}
