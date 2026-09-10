package com.example.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun SafeComposeViewController(
    screenName: String,
    content: @Composable () -> Unit
): UIViewController = ComposeUIViewController {
    content()
}
