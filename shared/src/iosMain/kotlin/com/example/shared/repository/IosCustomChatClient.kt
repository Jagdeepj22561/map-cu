package com.example.shared.repository

import com.example.shared.data.AuthSessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * iOS counterpart of Android's CustomChatClient. The socket is owned by the
 * repository lifecycle, not by a chat screen, so messages continue arriving
 * while the user is browsing another destination.
 */
internal class IosCustomChatClient {
    private val http = HttpClient(Darwin) { install(WebSockets) }
    private val outbound = Channel<String>(capacity = 256)
    private var job: Job? = null
    private var socket: WebSocketSession? = null

    fun start(scope: CoroutineScope, onEvent: suspend (JsonObject, IosCustomChatClient) -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch {
            var backoff = 1_000L
            while (isActive) {
                try {
                    val token = AuthSessionManager.requireAccessToken()
                    val url = IosPlatformConfig.backendBaseUrl
                        .replaceFirst("https://", "wss://")
                        .replaceFirst("http://", "ws://")
                        .trimEnd('/') + "/chat"
                    http.webSocket(urlString = url) {
                        socket = this
                        send(Frame.Text(buildJsonObject {
                            put("type", JsonPrimitive("auth"))
                            put("token", JsonPrimitive(token))
                        }.toString()))
                        backoff = 1_000L
                        val ready = CompletableDeferred<Unit>()
                        val writer = launch {
                            ready.await()
                            for (packet in outbound) send(Frame.Text(packet))
                        }
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val event = runCatching {
                                        Json.parseToJsonElement(frame.readText()).jsonObject
                                    }.getOrNull() ?: continue
                                    if (event["type"]?.jsonPrimitive?.content == "ready") {
                                        ready.complete(Unit)
                                    }
                                    onEvent(event, this@IosCustomChatClient)
                                }
                            }
                        } finally {
                            writer.cancel()
                            socket = null
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(60_000L)
                }
            }
        }
    }

    fun sendMessage(id: String, recipientId: String, content: String, timestamp: Long, imageUrl: String?) {
        outbound.trySend(buildJsonObject {
            put("type", JsonPrimitive("send"))
            put("id", JsonPrimitive(id))
            put("recipientId", JsonPrimitive(recipientId))
            put("content", JsonPrimitive(content))
            put("timestamp", JsonPrimitive(timestamp))
            if (imageUrl == null) put("imageUrl", kotlinx.serialization.json.JsonNull)
            else put("imageUrl", JsonPrimitive(imageUrl))
        }.toString())
    }

    fun acknowledge(id: String) {
        outbound.trySend(buildJsonObject {
            put("type", JsonPrimitive("ack"))
            put("id", JsonPrimitive(id))
        }.toString())
    }

    fun requestSync() {
        outbound.trySend(buildJsonObject { put("type", JsonPrimitive("sync")) }.toString())
    }

    fun stop() {
        job?.cancel()
        job = null
        socket = null
    }
}
