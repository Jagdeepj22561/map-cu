package com.example.maps123.data.supabase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Signals that Supabase opened the app with a password-recovery session. */
object PasswordRecoveryManager {
    private val _isPasswordUpdateRequired = MutableStateFlow(false)
    val isPasswordUpdateRequired = _isPasswordUpdateRequired.asStateFlow()

    fun requirePasswordUpdate() {
        _isPasswordUpdateRequired.value = true
    }

    fun completePasswordUpdate() {
        _isPasswordUpdateRequired.value = false
    }
}
