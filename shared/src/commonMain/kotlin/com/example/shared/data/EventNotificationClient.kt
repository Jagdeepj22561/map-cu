package com.example.shared.data

import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Requests an FCM delivery after a successful Supabase write. The Render
 * service verifies the actor and rereads the resource before choosing a
 * recipient, so this client never gets to select who receives a notification.
 * Delivery failure is deliberately best-effort: it must not undo a post or a
 * team decision that was already saved successfully.
 */
object EventNotificationClient {
    private val httpClient by lazy { HttpClient() }

    suspend fun dispatch(kind: String, resourceId: String) {
        val baseUrl = NotificationBackendConfig.url.trimEnd('/')
        if (baseUrl.isBlank() || resourceId.isBlank()) return
        val accessToken = SupabaseClientProvider.client.auth.currentSessionOrNull()?.accessToken ?: return

        runCatching {
            httpClient.post("$baseUrl/notifications/event") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("kind", kind)
                    put("resourceId", resourceId)
                }.toString())
            }
        }
    }
}
