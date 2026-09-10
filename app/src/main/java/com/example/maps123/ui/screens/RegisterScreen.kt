package com.example.maps123.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import android.widget.DatePicker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.maps123.data.local.UserEntity
import com.example.maps123.data.repository.AuthRepository
import com.example.maps123.data.repository.UserRepository
import com.example.maps123.utils.ImageUtils
import kotlinx.coroutines.launch
import java.util.Calendar
import com.example.shared.ui.PureRegisterScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onRegistered: (String) -> Unit, onLoginClick: () -> Unit) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val userRepository = remember { UserRepository(context) }

    // ---------------- STATE ----------------

    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var semester by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    var imageUri by remember { mutableStateOf<Uri?>(null) }

    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    // ---------------- IMAGE PICKER ----------------

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { imageUri = it }

    // ---------------- DOB PICKER ----------------

    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, y, m, d -> dob = "$d/${m + 1}/$y" },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    PureRegisterScreen(
        email = email,
        onEmailChange = { email = it },
        otp = otp,
        onOtpChange = { otp = it },
        otpSent = otpSent,
        onSendOtp = {
            if (!email.endsWith("@cuchd.in")) {
                status = "Use @cuchd.in email"
            } else {
                loading = true
                scope.launch {
                    try {
                        AuthRepository.generateOtp(email)
                            .onSuccess {
                                otpSent = true
                                status = "OTP Sent! Check your college email."
                            }
                            .onFailure {
                                status = it.message ?: "OTP failed"
                            }
                    } finally {
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
        onDobClick = { datePickerDialog.show() },
        password = password,
        onPasswordChange = { password = it },
        passwordVisible = passwordVisible,
        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
        loading = loading,
        status = status,
        onRegisterClick = {
            if (name.isBlank() || password.isBlank()) {
                status = "Fill all fields"
            } else {
                loading = true
                scope.launch {
                    AuthRepository.verifyAndRegister(email, otp, name, password)
                        .onSuccess {
                            val uid = AuthRepository.login(email, password).getOrThrow()
                            var profileUrl = ""
                            if (imageUri != null) {
                                val file = ImageUtils.uriToFile(context, imageUri!!)
                                profileUrl = ImageUtils.uploadImage(
                                    ImageUtils.compressProfileImage(context, file)
                                )
                            }
                            userRepository.saveUser(
                                UserEntity(
                                    uid = uid,
                                    email = email,
                                    name = name,
                                    phoneNumber = phoneNumber,
                                    year = year,
                                    semester = semester,
                                    course = course,
                                    dob = dob,
                                    profilePicUrl = profileUrl,
                                    lastUpdated = System.currentTimeMillis()
                                )
                            )
                            onRegistered(email)
                        }
                        .onFailure {
                            status = it.message ?: "Registration failed"
                            loading = false
                        }
                }
            }
        },
        onLoginClick = onLoginClick,
        onImageClick = { imagePicker.launch("image/*") },
        renderProfileImage = { modifier ->
            if (imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = null,
                    modifier = modifier,
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, Modifier.size(60.dp))
                }
            }
        },
        renderEmailIcon = { Icon(Icons.Default.Email, null) },
        renderCalendarIcon = { Icon(Icons.Default.CalendarToday, null) },
        renderVisibilityIcon = { visible ->
            val icon = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff
            Icon(icon, contentDescription = if (visible) "Hide password" else "Show password")
        },
        renderAddPhotoIcon = { Icon(Icons.Default.AddAPhoto, null, Modifier.size(18.dp)) }
    )
}
