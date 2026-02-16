package com.claw.app.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object WaitingForChallenge : ConnectionState()
    data object Authenticating : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * OpenClaw Gateway WebSocket client.
 *
 * Protocol (from openclaw/src/gateway/protocol):
 *   - Frames: { type:"req", id:"<string>", method:"...", params:{} }
 *   - Responses: { type:"res", id:"<string>", ok:true/false, payload/error }
 *   - Events: { type:"event", event:"...", payload:{}, seq:N }
 *   - PROTOCOL_VERSION = 3
 *
 * Auth handshake:
 *   1. Server sends event "connect.challenge" with { nonce, ts }
 *   2. Client sends req "connect" with ConnectParams including auth.token
 *   3. Server responds with hello-ok containing snapshot, features, policy
 */
class GatewayClient(private val client: OkHttpClient) {

    companion object {
        private const val PROTOCOL_VERSION = 3
        private const val CLIENT_ID = "gateway-client"   // matches GATEWAY_CLIENT_IDS
        private const val CLIENT_MODE = "backend"         // matches GATEWAY_CLIENT_MODES
        private const val CLIENT_VERSION = "1.0.0"
        private const val CLIENT_PLATFORM = "android"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<GatewayEvent>()
    val events: SharedFlow<GatewayEvent> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    private var authToken: String? = null
    private var lastSeq: Int? = null

    /**
     * Connect to the OpenClaw gateway WebSocket.
     *
     * @param url The gateway URL (e.g. "ws://127.0.0.1:18789" or "http://host:port")
     * @param token The gateway auth token (from openclaw.json gateway.auth.token)
     */
    fun connect(url: String, token: String? = null) {
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.WaitingForChallenge ||
            _connectionState.value is ConnectionState.Authenticating ||
            _connectionState.value is ConnectionState.Connected
        ) {
            return
        }

        authToken = token
        _connectionState.value = ConnectionState.Connecting

        // Normalize URL to ws:// or wss://
        val wsUrl = url
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/')

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Do NOT send connect here!
                // Wait for the server's "connect.challenge" event first.
                _connectionState.value = ConnectionState.WaitingForChallenge
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    try {
                        val json = JSONObject(text)
                        handleMessage(webSocket, json)
                    } catch (e: Exception) {
                        // Invalid JSON, ignore
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
                flushPendingErrors(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.Disconnected
                flushPendingErrors(Exception("Connection closed: $code $reason"))
            }
        })
    }

    /**
     * Handle an incoming WebSocket message according to the OpenClaw protocol.
     */
    private suspend fun handleMessage(ws: WebSocket, json: JSONObject) {
        val type = json.optString("type", "")

        when (type) {
            "event" -> handleEvent(ws, json)
            "res" -> handleResponse(json)
            else -> {
                // Unknown frame type, emit as raw event
                _events.emit(GatewayEvent.Unknown(json))
            }
        }
    }

    /**
     * Handle an event frame from the server.
     * Events have: { type:"event", event:"<name>", payload:{}, seq:N }
     */
    private suspend fun handleEvent(ws: WebSocket, json: JSONObject) {
        val eventName = json.optString("event", "")
        val payload = json.optJSONObject("payload")

        // Track sequence for gap detection
        if (json.has("seq")) {
            val seq = json.getInt("seq")
            val prev = lastSeq
            if (prev != null && seq > prev + 1) {
                _events.emit(GatewayEvent.SeqGap(expected = prev + 1, received = seq))
            }
            lastSeq = seq
        }

        when (eventName) {
            "connect.challenge" -> {
                // Server is sending us a challenge nonce.
                // We must respond with the connect request including this nonce.
                val nonce = payload?.optString("nonce")
                if (nonce != null) {
                    _connectionState.value = ConnectionState.Authenticating
                    sendConnectRequest(ws, nonce)
                }
            }
            "tick" -> {
                // Server heartbeat, acknowledged implicitly
                _events.emit(GatewayEvent.Tick)
            }
            "health" -> {
                _events.emit(GatewayEvent.Health(payload ?: JSONObject()))
            }
            "agent" -> {
                _events.emit(GatewayEvent.Agent(payload ?: JSONObject()))
            }
            "shutdown" -> {
                _events.emit(GatewayEvent.Shutdown(
                    reason = payload?.optString("reason") ?: "unknown"
                ))
            }
            else -> {
                _events.emit(GatewayEvent.Generic(eventName, payload))
            }
        }
    }

    /**
     * Handle a response frame from the server.
     * Responses have: { type:"res", id:"<string>", ok:boolean, payload:{}, error:{} }
     */
    private fun handleResponse(json: JSONObject) {
        val id = json.optString("id", "")
        if (id.isEmpty()) return

        val deferred = pendingRequests.remove(id) ?: return

        val ok = json.optBoolean("ok", false)
        if (ok) {
            deferred.complete(json.optJSONObject("payload") ?: JSONObject())
        } else {
            val error = json.optJSONObject("error")
            val message = error?.optString("message") ?: "Unknown error"
            deferred.completeExceptionally(GatewayException(message, error))
        }
    }

    /**
     * Send the connect handshake request with proper ConnectParams.
     * This is called AFTER receiving the connect.challenge event.
     */
    private fun sendConnectRequest(ws: WebSocket, nonce: String) {
        val id = UUID.randomUUID().toString()

        val clientInfo = JSONObject().apply {
            put("id", CLIENT_ID)
            put("displayName", "Talon Mission Control")
            put("version", CLIENT_VERSION)
            put("platform", CLIENT_PLATFORM)
            put("mode", CLIENT_MODE)
        }

        val params = JSONObject().apply {
            put("minProtocol", PROTOCOL_VERSION)
            put("maxProtocol", PROTOCOL_VERSION)
            put("client", clientInfo)
            put("role", "operator")
            put("scopes", JSONArray().apply {
                put("operator.admin")
            })
            put("caps", JSONArray())

            // Auth — token-based, matching openclaw.json gateway.auth.token
            authToken?.let { token ->
                put("auth", JSONObject().apply {
                    put("token", token)
                })
            }
        }

        val frame = JSONObject().apply {
            put("type", "req")       // MUST be "req", not "request"
            put("id", id)            // MUST be a string
            put("method", "connect")
            put("params", params)
        }

        ws.send(frame.toString())

        // Track the connect response
        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[id] = deferred

        scope.launch {
            try {
                val helloOk = deferred.await()
                _connectionState.value = ConnectionState.Connected
                _events.emit(GatewayEvent.HelloOk(helloOk))
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error("Handshake failed: ${e.message}")
            }
        }
    }

    /**
     * Send a method request to the gateway.
     * Can only be called after successful connection.
     *
     * @param method The method name (e.g. "health", "channels.status", "cron.list")
     * @param params Optional parameters for the method
     * @return The response payload as JSONObject
     */
    suspend fun sendMethod(method: String, params: JSONObject = JSONObject()): JSONObject {
        val ws = webSocket ?: throw IllegalStateException("Not connected to gateway")
        if (_connectionState.value !is ConnectionState.Connected) {
            throw IllegalStateException("Gateway not in Connected state")
        }

        val id = UUID.randomUUID().toString()
        val frame = JSONObject().apply {
            put("type", "req")
            put("id", id)
            put("method", method)
            put("params", params)
        }

        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[id] = deferred

        ws.send(frame.toString())
        return deferred.await()
    }

    /**
     * Disconnect from the gateway.
     */
    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        flushPendingErrors(Exception("Disconnected"))
        lastSeq = null
    }

    private fun flushPendingErrors(cause: Throwable) {
        pendingRequests.values.forEach { it.completeExceptionally(cause) }
        pendingRequests.clear()
    }
}

/**
 * Gateway event types emitted via the events SharedFlow.
 */
sealed class GatewayEvent {
    /** Connection established, contains the hello-ok payload with snapshot, features, policy */
    data class HelloOk(val payload: JSONObject) : GatewayEvent()

    /** Health status update */
    data class Health(val payload: JSONObject) : GatewayEvent()

    /** Agent activity (chat messages, tool use, etc.) */
    data class Agent(val payload: JSONObject) : GatewayEvent()

    /** Server is shutting down */
    data class Shutdown(val reason: String) : GatewayEvent()

    /** Server heartbeat */
    data object Tick : GatewayEvent()

    /** Sequence gap detected (missed events) */
    data class SeqGap(val expected: Int, val received: Int) : GatewayEvent()

    /** Any other named event */
    data class Generic(val event: String, val payload: JSONObject?) : GatewayEvent()

    /** Unknown/unparseable frame */
    data class Unknown(val raw: JSONObject) : GatewayEvent()
}

/**
 * Exception from a gateway error response.
 */
class GatewayException(
    message: String,
    val errorPayload: JSONObject? = null
) : Exception(message)
