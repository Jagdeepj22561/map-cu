package com.example.shared.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PureRegisterScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    otp: String,
    onOtpChange: (String) -> Unit,
    otpSent: Boolean,
    onSendOtp: () -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    year: String,
    onYearChange: (String) -> Unit,
    semester: String,
    onSemesterChange: (String) -> Unit,
    course: String,
    onCourseChange: (String) -> Unit,
    dob: String,
    onDobClick: () -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    loading: Boolean,
    status: String,
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit,
    onImageClick: () -> Unit,
    renderProfileImage: @Composable (Modifier) -> Unit,
    renderEmailIcon: @Composable () -> Unit,
    renderCalendarIcon: @Composable () -> Unit,
    renderVisibilityIcon: @Composable (Boolean) -> Unit,
    renderAddPhotoIcon: @Composable () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(32.dp))
            Text("Create Account", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            // -------- PROFILE IMAGE --------
            Box(contentAlignment = Alignment.BottomEnd) {
                val imgModifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { onImageClick() }

                renderProfileImage(imgModifier)

                SmallFloatingActionButton(
                    onClick = onImageClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    renderAddPhotoIcon()
                }
            }

            Spacer(Modifier.height(24.dp))

            // -------- EMAIL --------
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("University Email") },
                enabled = !otpSent,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = renderEmailIcon,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            if (!otpSent) {
                Button(
                    onClick = onSendOtp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(50.dp),
                    enabled = !loading
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Send OTP")
                    }
                }
            } else {
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = otp,
                    onValueChange = onOtpChange,
                    label = { Text("OTP") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = onPhoneNumberChange,
                    label = { Text("Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = onYearChange,
                        label = { Text("Year") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = semester,
                        onValueChange = onSemesterChange,
                        label = { Text("Sem") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = course,
                    onValueChange = onCourseChange,
                    label = { Text("Course") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = dob,
                    onValueChange = {},
                    label = { Text("DOB") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDobClick() },
                    enabled = false,
                    leadingIcon = renderCalendarIcon
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onTogglePasswordVisibility) {
                            renderVisibilityIcon(passwordVisible)
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Button(
                    onClick = onRegisterClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .height(50.dp),
                    enabled = !loading
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Verify & Register")
                    }
                }
            }

            if (status.isNotEmpty()) {
                Text(
                    text = status, 
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            TextButton(onClick = onLoginClick) {
                Text("Already have an account? Login")
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}
