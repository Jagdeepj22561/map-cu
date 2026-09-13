package com.example.maps123

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.maps123.ui.MainViewModel
import com.example.maps123.ui.navigation.RootNavigation
import com.example.maps123.utils.VoiceGuideWrapper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.maps123.data.firebase.FcmTokenSyncManager
import com.example.maps123.data.repository.AuthRepository
import com.example.maps123.data.supabase.PasswordRecoveryManager
import com.example.maps123.data.supabase.SupabaseProvider
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {

    private var ttsEngine: TextToSpeech? = null

    // ViewModel
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        }
    }

    private var pendingDeepLinkChatId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("DEBUG_MAIN", "onCreate CALLED")

        askNotificationPermission()

        pendingDeepLinkChatId = intent.getStringExtra("deep_link_chat_id")

        ttsEngine = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                viewModel.setVoiceGuide(VoiceGuideWrapper(ttsEngine))
            }
        }

        handleSupabaseDeepLink(intent)

        setContent {
            RootNavigation(viewModel = viewModel, pendingChatId = pendingDeepLinkChatId)
        }
    }

    fun consumeDeepLinkChatId() {
        pendingDeepLinkChatId = null
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsEngine?.stop()
        ttsEngine?.shutdown()
    }

    override fun onStart() {
        super.onStart()
        Log.d("DEBUG_MAIN", "onStart CALLED")
        if (AuthRepository.currentUserId() != null) {
            updateFcmToken()
        }
        viewModel.resumeRealtimeSync()
    }

    override fun onStop() {
        super.onStop()
        viewModel.pauseRealtimeSync()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSupabaseDeepLink(intent)
    }

    private fun handleSupabaseDeepLink(intent: android.content.Intent) {
        SupabaseProvider.client.handleDeeplinks(
            intent = intent,
            onSessionSuccess = {
                runOnUiThread {
                    if (it.type.equals("recovery", ignoreCase = true)) {
                        PasswordRecoveryManager.requirePasswordUpdate()
                    }
                    updateFcmToken()
                    viewModel.initializeChat()
                }
            },
            onError = { error -> Log.e("SupabaseAuth", "Deep-link authentication failed", error) }
        )
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun updateFcmToken() {
        FcmTokenSyncManager.syncCurrentToken(this)
    }
}
