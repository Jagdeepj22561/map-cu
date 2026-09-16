package com.example.maps123.data.api

import com.example.maps123.data.supabase.SupabaseProvider
import com.example.shared.model.ImageUploadResponse
import com.example.shared.model.OtpRequest
import com.example.shared.model.OtpStatusResponse
import com.example.shared.model.TokenResponse
import com.example.shared.model.VerifyCreateRequest
import io.github.jan.supabase.auth.auth
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

interface OtpApi {

    @POST("generate-otp")
    suspend fun generateOtp(
        @Body request: OtpRequest
    ): Response<OtpStatusResponse>

    @POST("verify-otp")
    suspend fun verifyOtp(
        @Body request: com.example.shared.model.VerifyRequest
    ): Response<TokenResponse>

    @POST("verify-otp-create")
    suspend fun verifyOtpCreate(
        @Body request: VerifyCreateRequest
    ): Response<TokenResponse>

    @Multipart
    @POST("upload-image")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part
    ): Response<ImageUploadResponse>
}

object ApiClient {

    private const val BASE_URL = "https://campus-map-backend-fpz8.onrender.com/"
    /**
     * Render endpoints no longer use a static API key embedded in the APK.
     * Authenticated requests carry the current Supabase access token instead.
     * OTP registration endpoints intentionally work without a user session.
     */
    private val interceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
            .addHeader("Accept", "application/json")

        SupabaseProvider.client.auth.currentSessionOrNull()?.accessToken?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        chain.proceed(requestBuilder.build())
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .addInterceptor(interceptor)
        .build()

    val api: OtpApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OtpApi::class.java)
}
