package com.example.server

import com.example.model.LogLevel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

class BridgeHttpServer(private val port: Int) {

    @Volatile
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val wsSessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()
    private var startedAtMs: Long = 0L

    fun start() {
        if (engine != null) return
        startedAtMs = System.currentTimeMillis()

        val server = embeddedServer(
            factory = CIO,
            port = port,
            host = "0.0.0.0",
            watchPaths = emptyList(),
        ) {
            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
            }

            install(WebSockets) {
                pingPeriod = 15.seconds
                timeout = 30.seconds
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            routing {
                get("/") {
                    val clientIp = call.request.origin.remoteAddress
                    BridgeStateHolder.incrementRequestsServed()
                    BridgeStateHolder.appendLog(
                        LogLevel.HTTP,
                        clientIp,
                        "GET / (index)",
                    )
                    val payload = JSONObject().apply {
                        put("service", "ScriptBridge")
                        put("status", "running")
                        put("port", port)
                        put("routes", JSONArray(listOf("/health", "/script.lua", "/poll", "/report", "/ws")))
                    }
                    call.respondText(payload.toString(2), ContentType.Application.Json)
                }

                get("/health") {
                    val clientIp = call.request.origin.remoteAddress
                    val script = BridgeStateHolder.loadedScript.value
                    BridgeStateHolder.incrementRequestsServed()
                    BridgeStateHolder.appendLog(
                        LogLevel.HTTP,
                        clientIp,
                        "GET /health",
                    )
                    val payload = JSONObject().apply {
                        put("status", "ok")
                        put("port", port)
                        put("wsClients", wsSessions.size)
                        put("hasScript", script != null)
                        put("scriptName", script?.fileName ?: JSONObject.NULL)
                        put("uptimeMs", System.currentTimeMillis() - startedAtMs)
                    }
                    call.respondText(payload.toString(), ContentType.Application.Json)
                }

                suspend fun serveScript(clientIp: String, path: String): Pair<HttpStatusCode, String> {
                    if (BridgeStateHolder.autoReloadEnabled.value) {
                        runCatching { BridgeStateHolder.onReloadRequested?.invoke() }
                    }
                    val script = BridgeStateHolder.loadedScript.value
                    BridgeStateHolder.incrementRequestsServed()
                    return if (script == null) {
                        BridgeStateHolder.appendLog(
                            LogLevel.WARNING,
                            clientIp,
                            "GET $path -> 404 (no script loaded)",
                        )
                        HttpStatusCode.NotFound to JSONObject().apply {
                            put("error", "No local Lua script loaded in ScriptBridge")
                        }.toString()
                    } else {
                        BridgeStateHolder.appendLog(
                            LogLevel.SUCCESS,
                            clientIp,
                            "GET $path -> served ${script.fileName} (${script.byteSize} B)",
                        )
                        HttpStatusCode.OK to script.content
                    }
                }

                get("/script") {
                    val clientIp = call.request.origin.remoteAddress
                    val (status, body) = serveScript(clientIp, "/script")
                    val contentType = if (status == HttpStatusCode.OK) {
                        ContentType.Text.Plain
                    } else {
                        ContentType.Application.Json
                    }
                    call.respondText(body, contentType, status)
                }

                get("/script.lua") {
                    val clientIp = call.request.origin.remoteAddress
                    val (status, body) = serveScript(clientIp, "/script.lua")
                    val contentType = if (status == HttpStatusCode.OK) {
                        ContentType.Text.Plain
                    } else {
                        ContentType.Application.Json
                    }
                    call.respondText(body, contentType, status)
                }

                get("/poll") {
                    val clientIp = call.request.origin.remoteAddress
                    val commands = BridgeStateHolder.drainPollCommands()
                    BridgeStateHolder.incrementRequestsServed()
                    if (commands.isNotEmpty()) {
                        BridgeStateHolder.appendLog(
                            LogLevel.HTTP,
                            clientIp,
                            "GET /poll -> delivered ${commands.size} command(s)",
                        )
                    }
                    val payload = JSONObject().apply {
                        put("commands", JSONArray(commands))
                        put("count", commands.size)
                        put("timestamp", System.currentTimeMillis())
                    }
                    call.respondText(payload.toString(), ContentType.Application.Json)
                }

                post("/report") {
                    val clientIp = call.request.origin.remoteAddress
                    val body = runCatching { call.receiveText() }.getOrDefault("")
                    BridgeStateHolder.incrementRequestsServed()
                    BridgeStateHolder.appendLog(
                        LogLevel.HTTP,
                        clientIp,
                        "POST /report: ${body.take(200).ifBlank { "(empty)" }}",
                    )
                    call.respondText(
                        JSONObject().put("received", true).toString(),
                        ContentType.Application.Json,
                    )
                }

                post("/log") {
                    val clientIp = call.request.origin.remoteAddress
                    val body = runCatching { call.receiveText() }.getOrDefault("")
                    BridgeStateHolder.incrementRequestsServed()
                    BridgeStateHolder.appendLog(
                        LogLevel.HTTP,
                        clientIp,
                        "POST /log: ${body.take(200).ifBlank { "(empty)" }}",
                    )
                    call.respondText(
                        JSONObject().put("received", true).toString(),
                        ContentType.Application.Json,
                    )
                }

                webSocket("/ws") {
                    val sessionId = UUID.randomUUID().toString().take(8)
                    val clientIp = call.request.origin.remoteAddress
                    wsSessions[sessionId] = this
                    BridgeStateHolder.setWsClientCount(wsSessions.size)
                    BridgeStateHolder.appendLog(
                        LogLevel.WEBSOCKET,
                        clientIp,
                        "WebSocket connected (session=$sessionId, total=${wsSessions.size})",
                    )

                    val script = BridgeStateHolder.loadedScript.value
                    val welcome = JSONObject().apply {
                        put("type", "welcome")
                        put("sessionId", sessionId)
                        put("scriptAvailable", script != null)
                        put("scriptName", script?.fileName ?: JSONObject.NULL)
                    }
                    runCatching { send(welcome.toString()) }

                    try {
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text = frame.readText()
                                    BridgeStateHolder.appendLog(
                                        LogLevel.WEBSOCKET,
                                        "$clientIp#$sessionId",
                                        "RX: ${text.take(240)}",
                                    )
                                    val trimmed = text.trim()
                                    when {
                                        trimmed.equals("PING", ignoreCase = true) -> {
                                            send("PONG")
                                        }
                                        trimmed.equals("GET_SCRIPT", ignoreCase = true) ||
                                            trimmed.contains("\"get_script\"", ignoreCase = true) -> {
                                            if (BridgeStateHolder.autoReloadEnabled.value) {
                                                runCatching { BridgeStateHolder.onReloadRequested?.invoke() }
                                            }
                                            val current = BridgeStateHolder.loadedScript.value
                                            if (current != null) {
                                                send(current.content)
                                                BridgeStateHolder.appendLog(
                                                    LogLevel.SUCCESS,
                                                    "$clientIp#$sessionId",
                                                    "TX script ${current.fileName} (${current.byteSize} B)",
                                                )
                                            } else {
                                                send(JSONObject().put("error", "No script loaded").toString())
                                            }
                                        }
                                    }
                                }
                                else -> Unit
                            }
                        }
                    } catch (_: ClosedReceiveChannelException) {
                        // Normal close
                    } catch (e: Exception) {
                        BridgeStateHolder.appendLog(
                            LogLevel.WARNING,
                            "$clientIp#$sessionId",
                            "WS error: ${e.message ?: "disconnected"}",
                        )
                    } finally {
                        wsSessions.remove(sessionId)
                        BridgeStateHolder.setWsClientCount(wsSessions.size)
                        BridgeStateHolder.appendLog(
                            LogLevel.WEBSOCKET,
                            clientIp,
                            "WebSocket disconnected (session=$sessionId, remaining=${wsSessions.size})",
                        )
                    }
                }
            }
        }

        server.start(wait = false)
        engine = server
    }

    suspend fun broadcast(message: String): Int {
        var sentCount = 0
        val deadSessions = mutableListOf<String>()
        wsSessions.forEach { (id, session) ->
            val ok = runCatching {
                session.send(message)
            }.isSuccess
            if (ok) {
                sentCount++
            } else {
                deadSessions += id
            }
        }
        deadSessions.forEach { wsSessions.remove(it) }
        BridgeStateHolder.setWsClientCount(wsSessions.size)
        return sentCount
    }

    fun stop() {
        wsSessions.clear()
        BridgeStateHolder.setWsClientCount(0)
        runCatching {
            engine?.stop(gracePeriodMillis = 300, timeoutMillis = 1200)
        }
        engine = null
    }
}
