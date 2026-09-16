package com.example.maps123.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.maps123.BuildConfig
import com.example.maps123.data.local.AppDatabase
import com.example.maps123.data.local.ChatEntity
import com.example.maps123.data.local.MessageEntity
import com.example.maps123.data.supabase.SupabaseProvider
import com.example.shared.data.AuthSessionManager
import com.example.shared.repository.parseDatabaseTimestampOrNull
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import org.json.JSONObject
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
                    SupabaseProvider.client.auth.awaitInitialization()
                    if (SupabaseProvider.client.auth.currentUserOrNull() == null) {
                        delay(5000L)
                        continue
                    }
                    connect()
                    backoff = 1000L
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // Never log credentials or content, but connection state is
                    // essential for diagnosing queued delivery failures.
                    Log.w(TAG, "Socket disconnected; retrying in ${backoff}ms (${error.javaClass.simpleName})")
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
        val token = AuthSessionManager.requireAccessToken()
        val owner = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: error("No session")
        // Capture this user's database; never resolve a new user's DAO in callbacks.
        val db = AppDatabase.getInstance(context.applicationContext)
        val dao = db.chatDao()
        val incoming = Channel<String>(256)
        val ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Socket opened; authenticating")
                webSocket.send(JSONObject().put("type", "auth").put("token", token).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!incoming.trySend(text).isSuccess) webSocket.cancel()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Socket failure (${response?.code ?: "no-http-response"}, ${t.javaClass.simpleName})")
                incoming.close(t)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Socket closed ($code)")
                incoming.close()
            }
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
                        "ready" -> {
                            ready = true
                            Log.d(TAG, "Chat session ready")
                        }
                        "accepted" -> {
                            val id = event.getString("id")
                            val updated = dao.updateMessageSyncStatus(id, true)
                            inflight.remove(id)
                            if (updated == 0) Log.w(TAG, "Server accepted an unknown local message id")
                        }
                        "error" -> {
                            if (!event.isNull("id") && !event.optBoolean("retryable", true)) {
                                dao.markCustomFailure(
                                    event.getString("id"),
                                    event.optString("message", "Not sent (access denied)")
                                )
                                inflight.remove(event.getString("id"))
                            } else {
                                // A transient database/network failure preserves pending state.
                                error("Server temporarily unavailable: ${event.optString("message", "chat error")}")
                            }
                        }
                        "message" -> {
                            val row = event.getJSONObject("message")
                            check(row.getString("recipient_id") == owner)
                            val id = row.getString("id")
                            val chatId = row.getString("chat_id")
                            val senderId = row.getString("sender_id")
                            val timestamp = parseServerTimestamp(row.getString("created_at"))
                            val content = row.optString("content", "")
                            val image = if (row.isNull("image_url")) null else row.getString("image_url")
                            db.withTransaction {
                                if (dao.getMessageById(id) == null) {
                                    val existingChat = dao.getChat(chatId)
                                    val read = activeChatId == chatId
                                    dao.insertMessage(MessageEntity(id, chatId, senderId, content, timestamp,
                                        isRead = read, isSynced = true, imageUrl = image,
                                        type = row.optString("type", if (image == null) "TEXT" else "IMAGE"), customTransport = true))
                                    val chat = existingChat ?: ChatEntity(chatId, senderId, "Friend", lastMessage = "", lastMessageTime = 0)
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
        private const val TAG = "CustomChat"
        const val DELIVERY_TTL_MS = 72 * 60 * 60 * 1000L
        private val http = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS)
            .connectTimeout(90, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()

        /**
         * PostgREST may return PostgreSQL timestamps with a space separator
         * (for example `2026-09-15 12:34:56.123456+00`).  Instant.parse only
         * accepts the ISO `T`/`Z` form, so one queued message used to tear down
         * the whole socket before either device could send or receive anything.
         */
        internal fun parseServerTimestamp(value: String): Long {
            return parseDatabaseTimestampOrNull(value)
                ?: run {
                    Log.w(TAG, "Server supplied an invalid message timestamp; using receipt time")
                    System.currentTimeMillis()
                }
        }
    }
}
