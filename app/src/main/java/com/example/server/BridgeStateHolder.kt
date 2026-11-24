package com.example.server

import com.example.model.LoadedScript
import com.example.model.LogEntry
import com.example.model.LogLevel
import com.example.model.ServerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

object BridgeStateHolder {

    private const val MAX_LOG_ENTRIES = 300
    private val nextLogId = AtomicLong(1L)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _serverStatus = MutableStateFlow(ServerStatus.STOPPED)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private val _activePort = MutableStateFlow(8080)
    val activePort: StateFlow<Int> = _activePort.asStateFlow()

    private val _activeWsClients = MutableStateFlow(0)
    val activeWsClients: StateFlow<Int> = _activeWsClients.asStateFlow()

    private val _pendingPollCount = MutableStateFlow(0)
    val pendingPollCount: StateFlow<Int> = _pendingPollCount.asStateFlow()

    private val _totalRequestsServed = MutableStateFlow(0)
    val totalRequestsServed: StateFlow<Int> = _totalRequestsServed.asStateFlow()

    private val _loadedScript = MutableStateFlow<LoadedScript?>(null)
    val loadedScript: StateFlow<LoadedScript?> = _loadedScript.asStateFlow()

    private val _autoReloadEnabled = MutableStateFlow(true)
    val autoReloadEnabled: StateFlow<Boolean> = _autoReloadEnabled.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val pollQueue = ConcurrentLinkedQueue<String>()

    @Volatile
    var onReloadRequested: (suspend () -> LoadedScript?)? = null

    @Volatile
    var onBroadcastRequested: (suspend (String) -> Int)? = null

    fun setStatus(status: ServerStatus, error: String? = null) {
        _serverStatus.value = status
        _errorMessage.value = error
    }

    fun setActivePort(port: Int) {
        _activePort.value = port
    }

    fun setWsClientCount(count: Int) {
        _activeWsClients.value = count.coerceAtLeast(0)
    }

    fun incrementRequestsServed() {
        _totalRequestsServed.update { it + 1 }
    }

    fun setLoadedScript(script: LoadedScript?) {
        _loadedScript.value = script
    }

    fun setAutoReloadEnabled(enabled: Boolean) {
        _autoReloadEnabled.value = enabled
    }

    fun enqueuePollCommand(command: String) {
        pollQueue.add(command)
        _pendingPollCount.value = pollQueue.size
    }

    fun drainPollCommands(maxItems: Int = 20): List<String> {
        val drained = mutableListOf<String>()
        while (drained.size < maxItems) {
            val item = pollQueue.poll() ?: break
            drained += item
        }
        _pendingPollCount.value = pollQueue.size
        return drained
    }

    fun clearPollQueue() {
        pollQueue.clear()
        _pendingPollCount.value = 0
    }

    fun appendLog(level: LogLevel, source: String, message: String) {
        val timestamp = synchronized(timeFormat) { timeFormat.format(Date()) }
        val entry = LogEntry(
            id = nextLogId.getAndIncrement(),
            timestamp = timestamp,
            level = level,
            source = source,
            message = message,
        )
        _logs.update { current ->
            val updated = current + entry
            if (updated.size > MAX_LOG_ENTRIES) {
                updated.takeLast(MAX_LOG_ENTRIES)
            } else {
                updated
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
