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

        val inFlightKey = "$uid:$token"
        synchronized(inFlightKeys) {
            if (!inFlightKeys.add(inFlightKey)) return
        }

        scope.launch {
            try {
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
                prefs.edit()
                    .putString(keyLastUid, uid)
                    .putString(keyLastToken, token)
                    .apply()
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
