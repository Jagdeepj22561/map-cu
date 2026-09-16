package com.example.maps123.data.repository

import android.util.Log
import com.example.maps123.data.api.ApiClient
import com.example.maps123.data.supabase.SupabaseProvider
import com.example.shared.model.OtpRequest
import com.example.shared.model.VerifyCreateRequest
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val AUTH_REQUEST_TIMEOUT_MS = 30_000L

object AuthRepository {
    suspend fun login(email: String, pass: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val auth = SupabaseProvider.client.auth
                Log.d("AuthRepository", "Starting Supabase login")
                withTimeout(AUTH_REQUEST_TIMEOUT_MS) {
                    auth.signInWith(Email) {
                        this.email = email.trim().lowercase()
                        password = pass
                    }
                }
                val uid = auth.currentUserOrNull()?.id
                    ?: throw Exception("Login failed: No user returned")
                Log.d("AuthRepository", "Supabase login succeeded")
                Result.success(uid)
            } catch (e: Exception) {
                Log.e("AuthRepository", "Supabase login failed", e)
                Result.failure(Exception(loginErrorMessage(e), e))
            }
        }

    suspend fun generateOtp(email: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val res = withTimeout(AUTH_REQUEST_TIMEOUT_MS) {
                    ApiClient.api.generateOtp(OtpRequest(email.trim().lowercase()))
                }
                if (res.isSuccessful && res.body()?.status == "OTP_SENT") {
                    Result.success(Unit)
                } else {
                    val errorBody = res.errorBody()?.string()
                    val errorMsg = res.body()?.error ?: res.body()?.message ?: errorBody ?: "Failed to send OTP"
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Legacy endpoint retained for API compatibility only.
     * The current registration flow is verifyAndRegister(), which creates the
     * Supabase user and then the app signs in through Supabase Auth. Never
     * synthesize a user ID from an email address.
     */
    suspend fun verifyOtp(email: String, otp: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val res = ApiClient.api.verifyOtp(com.example.shared.model.VerifyRequest(email, otp))
                if (res.isSuccessful && res.body()?.status == "VERIFIED") {
                    val uid = res.body()?.uid
                    if (uid.isNullOrBlank()) {
                        Result.failure(Exception("Verification succeeded but no Supabase user ID was returned"))
                    } else {
                        Result.success(uid)
                    }
                } else {
                    val errorBody = res.errorBody()?.string()
                    val errorMsg = res.body()?.error ?: res.body()?.message ?: errorBody ?: "Verification failed"
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun verifyAndRegister(
        email: String,
        otp: String,
        name: String,
        pass: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val res = ApiClient.api.verifyOtpCreate(
                VerifyCreateRequest(email, otp, pass, name)
            )
            if (res.isSuccessful && res.body()?.status == "USER_CREATED") {
                Result.success(Unit)
            } else {
                val errorBody = res.errorBody()?.string()
                Result.failure(Exception(res.body()?.message ?: res.body()?.error ?: errorBody ?: "Registration failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(AUTH_REQUEST_TIMEOUT_MS) {
                    SupabaseProvider.client.auth.resetPasswordForEmail(
                        email = email.trim(),
                        redirectUrl = "https://campus-map-backend-fpz8.onrender.com/auth/callback"
                    )
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun currentUserId(): String? = SupabaseProvider.client.auth.currentUserOrNull()?.id

    /**
     * Waits for the persisted Supabase session to be restored before reading the
     * user ID. Compose screens can start before Auth has finished initializing,
     * so a one-time synchronous read can incorrectly remain null for the whole
     * lifetime of the screen.
     */
    suspend fun awaitCurrentUserId(): String? {
        val auth = SupabaseProvider.client.auth
        auth.awaitInitialization()
        return auth.currentUserOrNull()?.id
    }

    fun currentUserEmail(): String? = SupabaseProvider.client.auth.currentUserOrNull()?.email

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { SupabaseProvider.client.auth.signOut() }
    }

    suspend fun updatePassword(newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(newPassword.length >= 8) { "Password must contain at least 8 characters" }
            SupabaseProvider.client.auth.updateUser {
                password = newPassword
            }
            Unit
        }
    }

    private fun loginErrorMessage(error: Throwable): String = when (error) {
        is AuthRestException -> when (error.errorCode) {
            AuthErrorCode.InvalidCredentials,
            AuthErrorCode.UserNotFound -> "Incorrect email or password"
            AuthErrorCode.EmailNotConfirmed -> "Please confirm your email before logging in"
            AuthErrorCode.UserBanned -> "This account has been disabled"
            AuthErrorCode.OverRequestRateLimit -> "Too many login attempts. Please try again later"
            else -> error.errorDescription.ifBlank { "Login failed. Please try again" }
        }
        is TimeoutCancellationException ->
            "Login timed out. Check your internet connection and try again"
        else -> {
            val message = error.message.orEmpty()
            when {
                message.contains("invalid_credentials", ignoreCase = true) ||
                    message.contains("invalid login credentials", ignoreCase = true) ||
                    message.contains("invalid password", ignoreCase = true) ->
                    "Incorrect email or password"
                else -> message.ifBlank { "Login failed. Please try again" }
            }
        }
    }
}
