package com.example.shared.model

data class OtpRequest(val email: String)

data class OtpStatusResponse(
    val status: String,
    val message: String? = null,
    val error: String? = null
)

data class VerifyRequest(
    val email: String,
    val otp: String
)

data class VerifyCreateRequest(
    val email: String,
    val otp: String,
    val password: String,
    val name: String
)

data class TokenResponse(
    val status: String,
    val uid: String? = null,
    val message: String? = null,
    val error: String? = null
)

data class ImageUploadResponse(
    val url: String? = null,
    val secure_url: String? = null,
    val message: String? = null,
    val error: String? = null
)
