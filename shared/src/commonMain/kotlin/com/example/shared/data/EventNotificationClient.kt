package com.example.shared.data

import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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

    suspend fun dispatch(kind: String, resourceId: String): Boolean {
        val baseUrl = NotificationBackendConfig.url.trimEnd('/')
        if (baseUrl.isBlank() || resourceId.isBlank()) return false
        val accessToken = SupabaseClientProvider.client.auth.currentSessionOrNull()?.accessToken ?: return false

        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val response = httpClient.post("$baseUrl/notifications/event") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("kind", kind)
                        put("resourceId", resourceId)
                    }.toString())
                }
                if (response.status.value in 200..299) return true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // A later attempt can recover from a Render cold start or a brief network outage.
            }
            if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS * (attempt + 1))
        }
        return false
    }

    /**
     * Temporary authenticated backend path for projects where the original
     * comment table was deployed without an owner-delete RLS policy. The
     * backend verifies the JWT and author before its service-role delete.
     */
    suspend fun deleteOwnAnnouncementComment(commentId: String) {
        val baseUrl = NotificationBackendConfig.url.trimEnd('/')
        require(baseUrl.isNotBlank()) { "Backend URL is unavailable" }
        val accessToken = SupabaseClientProvider.client.auth.currentSessionOrNull()?.accessToken
            ?: error("Login required")
        val response = httpClient.delete("$baseUrl/announcements/comments/$commentId") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        check(response.status.value in 200..299) { "Unable to delete comment" }
    }

    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 1_000L
}
