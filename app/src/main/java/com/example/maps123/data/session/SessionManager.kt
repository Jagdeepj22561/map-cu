package com.example.maps123.data.session

import android.content.Context
import com.example.maps123.data.local.AppDatabase
import com.example.maps123.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean = AuthRepository.currentUserId() != null

    fun isGuestSession(): Boolean = prefs.getBoolean(KEY_GUEST_SESSION, false)

    fun hasActiveSession(): Boolean = isLoggedIn() || isGuestSession()

    fun saveEmail(email: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putBoolean(KEY_GUEST_SESSION, false)
            .apply()
    }

    fun getSavedEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun saveGuestSession() {
        prefs.edit()
            .remove(KEY_EMAIL)
            .putBoolean(KEY_GUEST_SESSION, true)
            .apply()
    }

    /**
     * Removes device-only data for the signed-in account. Supabase data (posts,
     * messages, friends, and profile) is deliberately left untouched.
     */
    suspend fun clearSession() = withContext(Dispatchers.IO) {
        // The database name contains the current UID, so this must happen before
        // signing out. It clears locally cached chats, messages, announcements,
        // friend requests, and profile data for this account only.
        runCatching {
            AppDatabase.getInstance(context.applicationContext).clearAllTables()
        }

        // Saved announcements were previously kept in an app-wide preference.
        // Clear them on logout so they cannot be displayed for the next account.
        context.getSharedPreferences(SAVED_ANNOUNCEMENTS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        AuthRepository.logout()
        prefs.edit().clear().apply()
        AppDatabase.clearInstance()
    }

    companion object {
        private const val PREFS_NAME = "auth"
        private const val KEY_EMAIL = "email"
        private const val KEY_GUEST_SESSION = "guest_session"
        private const val SAVED_ANNOUNCEMENTS_PREFS = "saved_announcements"
    }
}
