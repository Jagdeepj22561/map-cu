package com.example.maps123.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.maps123.data.api.ApiClient
import com.example.shared.data.AuthSessionManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    fun uriToFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open input stream for URI: $uri")

        val file = File(
            context.cacheDir,
            "temp_image_${System.currentTimeMillis()}.jpg"
        )

        inputStream.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    fun compressProfileImage(context: Context, file: File): File {
        val bitmap = BitmapFactory.decodeFile(file.path)
            ?: throw Exception("Failed to decode image")

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)

        val compressedFile = File(
            context.cacheDir,
            "compressed_${file.name}"
        )

        FileOutputStream(compressedFile).use { fos ->
            fos.write(outputStream.toByteArray())
        }

        return compressedFile
    }

    fun compressPostImage(context: Context, file: File): File {
        return compressProfileImage(context, file)
    }

    // 🔥 FINAL FIXED UPLOAD FUNCTION
    suspend fun uploadImage(file: File): String {
        // Retrofit interceptors are synchronous; refresh before entering the
        // upload call so an expired bearer token is never attached.
        AuthSessionManager.requireAccessToken()
        val requestFile = file
            .asRequestBody("image/*".toMediaType())

        // 🚨 MUST BE "file" (matches @Part file: MultipartBody.Part in OtpApi.kt)
        val multipartBody = MultipartBody.Part.createFormData(
            name = "file",
            filename = file.name,
            body = requestFile
        )

        val response = ApiClient.api.uploadImage(multipartBody)

        if (response.isSuccessful) {
            val body = response.body()
            val url = body?.secure_url ?: body?.url
            if (!url.isNullOrBlank()) {
                return url
            }
            throw Exception("Server returned success but no URL: ${body?.error ?: body?.message ?: "Unknown error"}")
        }

        val errorBody = response.errorBody()?.string()
        throw Exception(
            "Image upload failed: ${response.message()} (Code ${response.code()}) - $errorBody"
        )
    }
}
