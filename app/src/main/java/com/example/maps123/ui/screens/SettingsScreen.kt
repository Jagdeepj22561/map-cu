package com.example.maps123.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.remember
import com.example.shared.ui.PureSettingsScreen

import com.example.shared.utils.ExternalNavigator

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    isAudioEnabled: Boolean,
    isGhostMode: Boolean = false,
    onToggleDarkMode: (Boolean) -> Unit,
    onToggleAudio: (Boolean) -> Unit,
    onToggleGhostMode: (Boolean) -> Unit = {},
    onProfileClick: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    val externalNavigator = remember {
        object : ExternalNavigator {
            override fun reportIssue() {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:jagdeepsingh3505j@gmail.com")
                    putExtra(Intent.EXTRA_SUBJECT, "Campus App Issue Report")
                    putExtra(Intent.EXTRA_TEXT, "Describe your issue here...")
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }

            override fun callHelpline(number: String) {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$number")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    PureSettingsScreen(
        isDarkMode = isDarkMode,
        isAudioEnabled = isAudioEnabled,
        isGhostMode = isGhostMode,
        onToggleDarkMode = onToggleDarkMode,
        onToggleAudio = onToggleAudio,
        onToggleGhostMode = onToggleGhostMode,
        onProfileClick = onProfileClick,
        onLogout = onLogout,
        onBack = onBack,
        externalNavigator = externalNavigator
    )
}
