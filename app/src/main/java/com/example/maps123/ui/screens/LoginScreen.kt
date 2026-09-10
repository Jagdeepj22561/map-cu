package com.example.maps123.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.unit.dp
import com.example.maps123.data.repository.AuthRepository
import kotlinx.coroutines.launch

import com.example.shared.ui.PureLoginScreen
import com.example.shared.ui.PureForgotPasswordDialog

@Composable
fun LoginScreen(
    onSuccess: (String) -> Unit,
    onContinueAsGuest: () -> Unit,
    onGoRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    
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
            if (email.isNotEmpty() && password.isNotEmpty()) {
                loading = true
                status = "Logging in..."
                scope.launch {
                    AuthRepository.login(email, password)
                        .onSuccess {
                            loading = false
                            onSuccess(email)
                        }
                        .onFailure {
                            loading = false
                            status = it.message ?: "Login failed"
                        }
                }
            } else {
                status = "Please fill all fields"
            }
        },
        onContinueAsGuest = onContinueAsGuest,
        onGoRegister = onGoRegister,
        onForgotPasswordClick = { showForgotPasswordDialog = true },
        renderLoginIcon = {
            Icon(
                imageVector = Icons.Default.Login,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        renderEmailIcon = { Icon(Icons.Default.Email, contentDescription = null) },
        renderLockIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        renderVisibilityIcon = { visible ->
            val icon = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff
            Icon(icon, contentDescription = if (visible) "Hide password" else "Show password")
        }
    )

    if (showForgotPasswordDialog) {
        var resetEmail by remember { mutableStateOf(email) }
        var resetStatus by remember { mutableStateOf("") }
        var isResetting by remember { mutableStateOf(false) }

        PureForgotPasswordDialog(
            email = resetEmail,
            onEmailChange = { resetEmail = it },
            status = resetStatus,
            loading = isResetting,
            onSendClick = {
                if (resetEmail.isNotBlank()) {
                    isResetting = true
                    resetStatus = "Sending..."
                    scope.launch {
                        AuthRepository.sendPasswordResetEmail(resetEmail)
                            .onSuccess {
                                isResetting = false
                                resetStatus = "Link Sent! Check your email."
                            }
                            .onFailure {
                                isResetting = false
                                resetStatus = it.message ?: "Failed to send link"
                            }
                    }
                } else {
                    resetStatus = "Please enter email"
                }
            },
            onDismiss = { showForgotPasswordDialog = false }
        )
    }
}
