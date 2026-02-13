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
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class GatewayClient(private val client: OkHttpClient) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _messages = MutableSharedFlow<JSONObject>()
    val messages: SharedFlow<JSONObject> = _messages.asSharedFlow()
    
    private var webSocket: WebSocket? = null
    private var requestId = 0
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    
    private val deviceId = "talon-android-${UUID.randomUUID().toString().take(8)}"
    
    fun connect(url: String, token: String? = null) {
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected) {
            return
        }
        
        _connectionState.value = ConnectionState.Connecting
        
        // OpenClaw gateway uses raw WS on the main port, no /ws suffix
        val wsUrl = url.replace("http://", "ws://").replace("https://", "wss://")
        
        val request = Request.Builder()
            .url(wsUrl)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Send OpenClaw connect handshake as first frame
                sendConnectHandshake(webSocket, token)
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    try {
                        val json = JSONObject(text)
                        handleMessage(json)
                    } catch (e: Exception) {
                        // Invalid JSON, ignore
                    }
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
                pendingRequests.values.forEach { it.completeExceptionally(t) }
                pendingRequests.clear()
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.Disconnected
                pendingRequests.values.forEach { it.completeExceptionally(Exception("Connection closed")) }
                pendingRequests.clear()
            }
        })
    }
    
    private fun sendConnectHandshake(ws: WebSocket, token: String?) {
        val id = nextRequestId()
        val params = JSONObject().apply {
            put("deviceId", deviceId)
            token?.let {
                put("auth", JSONObject().put("token", it))
            }
        }
        
        val handshake = JSONObject().apply {
            put("type", "req")
            put("id", id)
            put("method", "connect")
            put("params", params)
        }
        
        ws.send(handshake.toString())
        
        // Wait for connect response to confirm connected state
        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[id] = deferred
        
        scope.launch {
            try {
                val response = deferred.await()
                _connectionState.value = ConnectionState.Connected
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error("Handshake failed: ${e.message}")
            }
        }
    }
    
    private fun handleMessage(json: JSONObject) {
        val type = json.optString("type")
        
        when (type) {
            "res" -> {
                // Response to a request
                val id = json.optInt("id", -1)
                pendingRequests.remove(id)?.complete(json)
            }
            "evt" -> {
                // Event broadcast
                scope.launch { _messages.emit(json) }
            }
            else -> {
                scope.launch { _messages.emit(json) }
            }
        }
    }
    
    suspend fun sendMethod(method: String, params: JSONObject = JSONObject()): JSONObject {
        val ws = webSocket ?: throw IllegalStateException("Not connected")
        
        val id = nextRequestId()
        val message = JSONObject().apply {
            put("type", "req")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        
        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[id] = deferred
        
        ws.send(message.toString())
        return deferred.await()
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        pendingRequests.values.forEach { it.completeExceptionally(Exception("Disconnected")) }
        pendingRequests.clear()
    }
    
    @Synchronized
    private fun nextRequestId(): Int {
        return ++requestId
    }
}
