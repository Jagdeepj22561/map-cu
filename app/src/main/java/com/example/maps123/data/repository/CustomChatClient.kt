package com.example.maps123.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.maps123.BuildConfig
import com.example.maps123.data.local.AppDatabase
import com.example.maps123.data.local.ChatEntity
import com.example.maps123.data.local.MessageEntity
import com.example.maps123.data.supabase.SupabaseProvider
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

/** One foreground socket, independent of the selected screen. Room is the outbox/history. */
class CustomChatClient(private val context: Context) {
    private var job: Job? = null
    private var socket: WebSocket? = null
    @Volatile var activeChatId: String? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            var backoff = 1000L
            while (isActive) {
                try {
                    connect()
                    backoff = 1000L
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Credentials and message content must never enter logs.
                }
                delay(backoff + kotlin.random.Random.nextLong(500))
                backoff = (backoff * 2).coerceAtMost(60000)
            }
        }
    }

    fun stop() { job?.cancel(); job = null; socket?.cancel(); socket = null }

    private suspend fun connect() = coroutineScope {
        val url = BuildConfig.CUSTOM_CHAT_SERVER_URL
        require(url.startsWith("wss://")) { "Custom chat requires a wss:// URL" }
        val auth = SupabaseProvider.client.auth
        auth.awaitInitialization()
        val owner = auth.currentUserOrNull()?.id ?: error("No session")
        val token = auth.currentSessionOrNull()?.accessToken ?: error("No token")
        // Capture this user's database; never resolve a new user's DAO in callbacks.
        val db = AppDatabase.getInstance(context.applicationContext)
        val dao = db.chatDao()
        val incoming = Channel<String>(256)
        val ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("type", "auth").put("token", token).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!incoming.trySend(text).isSuccess) webSocket.cancel()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { incoming.close(t) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { incoming.close() }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
        })
        socket = ws
        var ready = false
        val connectingSince = System.currentTimeMillis()
        val inflight = mutableMapOf<String, Long>()
        try {
            while (isActive) {
                check(auth.currentUserOrNull()?.id == owner) { "Account changed" }
                check(ready || System.currentTimeMillis() - connectingSince < 120000) { "Chat connection timed out" }
                val text = withTimeoutOrNull(1000) { incoming.receiveCatching().getOrThrow() }
                if (text != null) {
                    val event = JSONObject(text)
                    when (event.getString("type")) {
                        "ready" -> ready = true
                        "accepted" -> {
                            dao.updateMessageSyncStatus(event.getString("id"), true)
                            inflight.remove(event.getString("id"))
                        }
                        "error" -> {
                            if (!event.isNull("id") && !event.optBoolean("retryable", true)) {
                                dao.markCustomFailure(event.getString("id"), "Not sent (access denied)")
                                inflight.remove(event.getString("id"))
                            } else {
                                // A transient database/network failure preserves pending state.
                                error("Server temporarily unavailable")
                            }
                        }
                        "message" -> {
                            val row = event.getJSONObject("message")
                            check(row.getString("recipient_id") == owner)
                            val id = row.getString("id")
                            val chatId = row.getString("chat_id")
                            val senderId = row.getString("sender_id")
                            val timestamp = Instant.parse(row.getString("created_at")).toEpochMilli()
                            val content = row.getString("content")
                            val image = if (row.isNull("image_url")) null else row.getString("image_url")
                            db.withTransaction {
                                if (dao.getMessageById(id) == null) {
                                    val old = dao.getChat(chatId)
                                    val read = activeChatId == chatId
                                    dao.insertMessage(MessageEntity(id, chatId, senderId, content, timestamp,
                                        isRead = read, isSynced = true, imageUrl = image,
                                        type = row.getString("type"), customTransport = true))
                                    val chat = old ?: ChatEntity(chatId, senderId, "Friend", lastMessage = "", lastMessageTime = 0)
                                    dao.insertChat(chat.copy(
                                        lastMessage = if (timestamp >= chat.lastMessageTime) (if (image == null) content else "Image") else chat.lastMessage,
                                        lastMessageTime = maxOf(timestamp, chat.lastMessageTime),
                                        unreadCount = chat.unreadCount + if (read) 0 else 1))
                                }
                            }
                            // ACK only after both history and unread count are committed.
                            ws.send(JSONObject().put("type", "ack").put("id", id).toString())
                        }
                        "synced" -> Unit
                        "acked" -> Unit
                    }
                }
                if (ready) {
                    val now = System.currentTimeMillis()
                    for (message in dao.getCustomOutbox(owner, now - DELIVERY_TTL_MS)) {
                        if (now - (inflight[message.messageId] ?: 0) < 15000) continue
                        val chat = dao.getChat(message.chatId) ?: continue
                        val packet = JSONObject().put("type", "send").put("id", message.messageId)
                            .put("recipientId", chat.friendUid).put("content", message.content)
                            .put("timestamp", message.timestamp).put("imageUrl", message.imageUrl ?: JSONObject.NULL)
                        if (ws.send(packet.toString())) inflight[message.messageId] = now
                    }
                    // Fetch subsequent pages after ACKs, but avoid per-second database reads.
                    if (now - lastSync > 30000) {
                        ws.send(JSONObject().put("type", "sync").toString())
                        lastSync = now
                    }
                }
            }
        } finally { ws.cancel(); incoming.cancel() }
    }

    private var lastSync = 0L
    companion object {
        const val DELIVERY_TTL_MS = 72 * 60 * 60 * 1000L
        private val http = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS)
            .connectTimeout(90, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    }
}
