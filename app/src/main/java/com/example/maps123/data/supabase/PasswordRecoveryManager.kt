package com.example.maps123.data.supabase

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Signals that Supabase opened the app with a password-recovery session. */
object PasswordRecoveryManager {
    private const val PREFERENCES = "password_recovery_state"
    private const val REQUIRED = "password_update_required"
    private var applicationContext: Context? = null
    private val _isPasswordUpdateRequired = MutableStateFlow(false)
    val isPasswordUpdateRequired = _isPasswordUpdateRequired.asStateFlow()

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        _isPasswordUpdateRequired.value = preferences()?.getBoolean(REQUIRED, false) == true
    }

    fun requirePasswordUpdate() {
        preferences()?.edit()?.putBoolean(REQUIRED, true)?.apply()
        _isPasswordUpdateRequired.value = true
    }

    fun completePasswordUpdate() {
        preferences()?.edit()?.remove(REQUIRED)?.apply()
        _isPasswordUpdateRequired.value = false
    }

    fun isPasswordUpdateRequiredNow(): Boolean = _isPasswordUpdateRequired.value

    private fun preferences() = applicationContext?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
