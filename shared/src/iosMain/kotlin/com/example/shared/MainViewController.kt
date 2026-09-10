package com.example.shared

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.shared.repository.IosFirebaseRest
import com.example.shared.repository.IosPreferencesStore
import com.example.shared.repository.IosUserStore
import com.example.shared.repository.ChatRepository
import com.example.shared.ui.PureLoginScreen
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

fun LoginViewController(
    onLogin: (String, String) -> Unit,
    onRegister: () -> Unit
): UIViewController = SafeComposeViewController(screenName = "Login") {
    LoginRoute(
        onLogin = onLogin,
        onRegister = onRegister
    )
}

@Composable
internal fun LoginRoute(
    onLogin: (String, String) -> Unit,
    onRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PureLoginScreen(
        email = email,
        onEmailChange = {
            email = it
            if (status.isNotBlank()) status = ""
        },
        password = password,
        onPasswordChange = {
            password = it
            if (status.isNotBlank()) status = ""
        },
        passwordVisible = passwordVisible,
        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
        loading = loading,
        status = status,
        onLoginClick = {
            val normalizedEmail = email.trim().lowercase()
            when {
                email.isBlank() || password.isBlank() -> {
                    status = "Please fill all fields"
                }
                !normalizedEmail.contains("@") -> {
                    status = "Please enter a valid email"
                }
                else -> {
                    loading = true
                    status = ""
                    scope.launch {
                        runCatching {
                            val session = IosFirebaseRest.login(normalizedEmail, password)
                            ChatRepository().resetSessionState()
                            val user = runCatching {
                                IosUserStore.refreshCurrentUser()
                                    ?: IosUserStore.getUser(session.uid)
                            }.getOrNull()
                            IosPreferencesStore.setGhostMode(user?.ghostMode ?: false)
                            loading = false
                            onLogin(normalizedEmail, session.uid)
                        }.onFailure { error ->
                            loading = false
                            status = error.message ?: "Login failed"
                        }
                    }
                }
            }
        },
        onGoRegister = onRegister,
        onForgotPasswordClick = {
            val normalizedEmail = email.trim().lowercase()
            if (normalizedEmail.isBlank()) {
                status = "Enter your email first"
            } else {
                loading = true
                scope.launch {
                    runCatching {
                        IosFirebaseRest.sendPasswordReset(normalizedEmail)
                        status = "Password reset email sent"
                    }.onFailure { error ->
                        status = error.message ?: "Failed to send reset email"
                    }
                    loading = false
                }
            }
        },
        renderLoginIcon = { LoginIcon() },
        renderEmailIcon = { Icon(Icons.Default.Email, contentDescription = null) },
        renderLockIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        renderVisibilityIcon = { visible ->
            val icon = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility
            val description = if (visible) "Hide password" else "Show password"
            Icon(icon, contentDescription = description)
        }
    )
}

@Composable
internal fun LoginIcon() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.Login,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}
