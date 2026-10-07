package com.example.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.shared.repository.IosAuthApi
import com.example.shared.repository.IosPreferencesStore
import com.example.shared.repository.IosUserProfile
import com.example.shared.repository.IosUserStore
import com.example.shared.ui.PureRegisterScreen
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

fun RegisterViewController(
    onRegistered: (String) -> Unit,
    onGoLogin: () -> Unit
): UIViewController = SafeComposeViewController(screenName = "Register") {
    RegisterRoute(
        onRegistered = onRegistered,
        onGoLogin = onGoLogin
    )
}

@Composable
internal fun RegisterRoute(
    onRegistered: (String) -> Unit,
    onGoLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var semester by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("01/01/2005") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    PureRegisterScreen(
        email = email,
        onEmailChange = {
            email = it
            if (status.isNotBlank()) status = ""
        },
        otp = otp,
        onOtpChange = {
            otp = it
            if (status.isNotBlank()) status = ""
        },
        otpSent = otpSent,
        onSendOtp = {
            val normalizedEmail = email.trim().lowercase()
            when {
                normalizedEmail.isBlank() -> status = "Enter your university email"
                !normalizedEmail.contains("@") -> status = "Enter a valid email"
                !normalizedEmail.endsWith("@cuchd.in", ignoreCase = true) -> status = "Use @cuchd.in email"
                else -> {
                    loading = true
                    scope.launch {
                        runCatching {
                            IosAuthApi.generateOtp(normalizedEmail)
                            otpSent = true
                            status = "OTP sent successfully"
                        }.onFailure { error ->
                            status = error.message ?: "Failed to send OTP"
                        }
                        loading = false
                    }
                }
            }
        },
        name = name,
        onNameChange = { name = it },
        phoneNumber = phoneNumber,
        onPhoneNumberChange = { phoneNumber = it },
        year = year,
        onYearChange = { year = it },
        semester = semester,
        onSemesterChange = { semester = it },
        course = course,
        onCourseChange = { course = it },
        dob = dob,
        onDobClick = {
            dob = if (dob == "01/01/2005") "15/08/2004" else "01/01/2005"
        },
        password = password,
        onPasswordChange = { password = it },
        passwordVisible = passwordVisible,
        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
        loading = loading,
        status = status,
        onRegisterClick = {
            val normalizedEmail = email.trim().lowercase()
            when {
                normalizedEmail.isBlank() || otp.isBlank() || name.isBlank() || password.isBlank() -> {
                    status = "Fill all required fields"
                }
                !normalizedEmail.endsWith("@cuchd.in", ignoreCase = true) -> status = "Use @cuchd.in email"
                password.length < 6 -> status = "Password must be at least 6 characters"
                else -> {
                    loading = true
                    scope.launch {
                        runCatching {
                            val session = IosAuthApi.verifyAndRegister(
                                email = normalizedEmail,
                                otp = otp.trim(),
                                name = name.trim(),
                                password = password
                            )
                            IosUserStore.saveUser(
                                IosUserProfile(
                                    uid = session.uid,
                                    email = normalizedEmail,
                                    name = name.trim(),
                                    phoneNumber = phoneNumber.trim(),
                                    year = year.trim(),
                                    semester = semester.trim(),
                                    course = course.trim(),
                                    dob = dob.trim()
                                )
                            )
                            IosPreferencesStore.setGhostMode(false)
                            onRegistered(normalizedEmail)
                        }.onFailure { error ->
                            status = error.message ?: "Registration failed"
                        }
                        loading = false
                    }
                }
            }
        },
        onLoginClick = onGoLogin,
        onImageClick = {
            status = "Profile photo upload will be added with the native iOS picker"
        },
        renderProfileImage = { modifier ->
            Box(
                modifier = modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        },
        renderEmailIcon = { Icon(Icons.Default.Email, contentDescription = null) },
        renderCalendarIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
        renderVisibilityIcon = { visible ->
            val icon = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility
            Icon(icon, contentDescription = null)
        },
        renderAddPhotoIcon = { Icon(Icons.Default.AddAPhoto, contentDescription = null) }
    )
}
