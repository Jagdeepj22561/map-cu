package com.example.maps123.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import com.example.shared.ui.PureAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.maps123.data.repository.AuthRepository
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun UpdatePasswordDialog(onPasswordUpdated: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Set a new password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your password-reset link has been verified. Choose a new password.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("New password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it; error = null },
                    label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = {
                    when {
                        password.length < 8 -> error = "Password must contain at least 8 characters"
                        password != confirmation -> error = "Passwords do not match"
                        else -> {
                            saving = true
                            scope.launch {
                                AuthRepository.updatePassword(password)
                                    .onSuccess { onPasswordUpdated() }
                                    .onFailure { error = it.message ?: "Could not update password" }
                                saving = false
                            }
                        }
                    }
                }
            ) {
                Text(if (saving) "Saving…" else "Update password")
            }
        }
    )
}
