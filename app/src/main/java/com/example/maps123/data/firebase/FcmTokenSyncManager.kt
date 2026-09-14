package com.example.maps123.data.firebase

import android.content.Context
import com.example.maps123.data.repository.AuthRepository
import com.example.maps123.data.supabase.SupabaseProvider
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object FcmTokenSyncManager {
    private const val prefsName = "fcm_token_sync"
    private const val keyLastUid = "last_uid"
    private const val keyLastToken = "last_token"
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
        if (lastUid == uid && lastToken == token) return
        // The old account owns its token row under RLS. Rotate instead of trying
        // to transfer it while authenticated as a different account.
        if (lastUid != null && lastUid != uid && lastToken == token) {
            val rotationKey = "rotate:$uid"
            synchronized(inFlightKeys) { if (!inFlightKeys.add(rotationKey)) return }
            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
                synchronized(inFlightKeys) { inFlightKeys.remove(rotationKey) }
                if (task.isSuccessful && AuthRepository.currentUserId() == uid) syncCurrentToken(context)
            }
            return
        }

        val inFlightKey = "$uid:$token"
        synchronized(inFlightKeys) {
            if (!inFlightKeys.add(inFlightKey)) return
        }

        scope.launch {
            try {
                if (AuthRepository.currentUserId() != uid) return@launch
                // FCM still delivers Android notifications; only the token's
                // persistence moves from Firestore to Supabase.
                val table = SupabaseProvider.client.from("device_tokens")
                val existing = table.select {
                    filter { eq("token", token) }
                    limit(1)
                }.decodeList<DeviceTokenRow>().isNotEmpty()
                if (existing) {
                    table.update({ set("user_id", uid); set("platform", "android") }) {
                        filter { eq("token", token) }
                    }
                } else {
                    table.insert(DeviceTokenRow(token = token, userId = uid, platform = "android"))
                }
                if (AuthRepository.currentUserId() != uid) return@launch
                prefs.edit()
                    .putString(keyLastUid, uid)
                    .putString(keyLastToken, token)
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

@Serializable
private data class DeviceTokenRow(
    val token: String,
    @SerialName("user_id") val userId: String,
    val platform: String
)
