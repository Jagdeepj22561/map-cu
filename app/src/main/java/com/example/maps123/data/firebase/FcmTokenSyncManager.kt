package com.example.maps123.data.firebase

import android.content.Context
import com.example.maps123.data.repository.AuthRepository
import com.example.shared.data.EventNotificationClient
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object FcmTokenSyncManager {
    private const val prefsName = "fcm_token_sync"
    private const val keyLastUid = "last_uid"
    private const val keyLastToken = "last_token"
    private const val keyLastServerSyncAt = "last_server_sync_at"
    private const val serverRefreshMs = 6 * 60 * 60 * 1000L
    private val inFlightKeys = mutableSetOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun syncCurrentToken(context: Context) {
        val uid = AuthRepository.currentUserId() ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            if (AuthRepository.currentUserId() != uid) return@addOnCompleteListener
            val token = task.result?.trim().orEmpty()
            if (token.isBlank()) return@addOnCompleteListener
            syncTokenIfChanged(context, uid, token)
        }
    }

    fun syncTokenIfChanged(context: Context, token: String) {
        val uid = AuthRepository.currentUserId() ?: return
        syncTokenIfChanged(context, uid, token)
    }

    private fun syncTokenIfChanged(context: Context, uid: String, token: String) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val lastUid = prefs.getString(keyLastUid, null)
        val lastToken = prefs.getString(keyLastToken, null)
        val lastServerSyncAt = prefs.getLong(keyLastServerSyncAt, 0L)
        if (lastUid == uid && lastToken == token &&
            System.currentTimeMillis() - lastServerSyncAt < serverRefreshMs
        ) return

        val inFlightKey = "$uid:$token"
        synchronized(inFlightKeys) {
            if (!inFlightKeys.add(inFlightKey)) return
        }

        scope.launch {
            try {
                if (AuthRepository.currentUserId() != uid) return@launch
                val registered = EventNotificationClient.registerDeviceToken(token, "android")
                if (!registered) error("Backend rejected device token")
                if (AuthRepository.currentUserId() != uid) return@launch
                prefs.edit()
                    .putString(keyLastUid, uid)
                    .putString(keyLastToken, token)
                    .putLong(keyLastServerSyncAt, System.currentTimeMillis())
                    .apply()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                android.util.Log.w("FcmTokenSync", "Token registration failed; will retry on next foreground")
            } finally {
                synchronized(inFlightKeys) { inFlightKeys.remove(inFlightKey) }
            }
        }
    }
}
