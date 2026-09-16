package com.example.shared.data

import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Single refresh gate for every authenticated transport in the app.
 * Reading currentSessionOrNull() directly is not enough when the JWT has
 * expired; this accessor refreshes it before returning a bearer token.
 */
object AuthSessionManager {
    private val refreshMutex = Mutex()

    suspend fun accessTokenOrNull(): String? = refreshMutex.withLock {
        val auth = SupabaseClientProvider.client.auth
        auth.awaitInitialization()
        val session = auth.currentSessionOrNull() ?: return@withLock null
        // Refresh slightly early so a request cannot cross the expiry boundary
        // while it is waiting on the network.
        if (session.expiresAt <= Clock.System.now() + 30.seconds) {
            runCatching { auth.refreshCurrentSession() }.getOrElse { return@withLock null }
        }
        auth.currentSessionOrNull()?.accessToken
    }

    suspend fun requireAccessToken(): String =
        accessTokenOrNull() ?: error("Session expired. Please sign in again.")

    suspend fun currentUserIdOrNull(): String? {
        accessTokenOrNull() ?: return null
        return SupabaseClientProvider.client.auth.currentUserOrNull()?.id
    }
}
