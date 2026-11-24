package com.example.model

enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
}

enum class LogLevel(val badge: String) {
    INFO("SYS"),
    HTTP("HTTP"),
    WEBSOCKET("WS"),
    SUCCESS("OK"),
    WARNING("WARN"),
    ERROR("ERR"),
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val source: String,
    val message: String,
)

data class NetworkInterfaceInfo(
    val name: String,
    val ipAddress: String,
    val isTailscale: Boolean,
)

data class NetworkEndpoints(
    val tailscaleIp: String? = null,
    val wifiIp: String? = null,
    val interfaces: List<NetworkInterfaceInfo> = emptyList(),
)

data class LoadedScript(
    val uriString: String? = null,
    val fileName: String,
    val content: String,
    val byteSize: Int,
    val lastLoadedTime: String,
)

data class ServerUiState(
    val status: ServerStatus = ServerStatus.STOPPED,
    val port: Int = 8080,
    val portInput: String = "8080",
    val activeWsClients: Int = 0,
    val pendingPollCount: Int = 0,
    val totalRequestsServed: Int = 0,
    val tailscaleFunnelUrl: String = "",
    val networkEndpoints: NetworkEndpoints = NetworkEndpoints(),
    val loadedScript: LoadedScript? = null,
    val autoReloadScript: Boolean = true,
    val logs: List<LogEntry> = emptyList(),
    val isIgnoringBatteryOptimizations: Boolean = false,
    val errorMessage: String? = null,
)
