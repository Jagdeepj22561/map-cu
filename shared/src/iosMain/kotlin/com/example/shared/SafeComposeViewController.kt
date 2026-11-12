package com.example.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun SafeComposeViewController(
    screenName: String,
    content: @Composable () -> Unit
): UIViewController = ComposeUIViewController {
    initGlobalExceptionHandler()
    SafeComposeContainer(screenName = screenName, content = content)
}

@Composable
internal fun SafeComposeContainer(
    screenName: String,
    content: @Composable () -> Unit
) {
    val globalError by IosGlobalErrorState.currentError.collectAsState()
    var localError by remember { mutableStateOf<Throwable?>(null) }
    var copiedToClipboard by remember { mutableStateOf(false) }

    val activeError = globalError ?: localError?.let {
        AppCrashInfo(
            title = "Error in $screenName: ${it.message ?: it::class.simpleName}",
            details = it.stackTraceToString(),
            timestamp = iosDebugTimestamp(),
            logs = IosInAppDebugLogStore.getAllLogs()
        )
    }

    if (activeError != null) {
        CrashLogScreen(
            info = activeError,
            screenName = screenName,
            copied = copiedToClipboard,
            onCopy = {
                val fullText = buildString {
                    appendLine("=== APP ERROR & CRASH LOG ===")
                    appendLine("Screen: $screenName")
                    appendLine("Time: ${activeError.timestamp}")
                    appendLine("Title: ${activeError.title}")
                    appendLine("\n--- Stack Trace ---")
                    appendLine(activeError.details)
                    appendLine("\n--- In-App Debug Logs ---")
                    if (activeError.logs.isEmpty()) {
                        appendLine("(No debug logs recorded)")
                    } else {
                        activeError.logs.forEach { appendLine(it) }
                    }
                }
                iosCopyTextToClipboard(fullText)
                copiedToClipboard = true
            },
            onDismiss = {
                IosGlobalErrorState.clear()
                localError = null
                copiedToClipboard = false
            }
        )
    } else {
        var contentFailed by remember { mutableStateOf(false) }
        if (!contentFailed) {
            try {
                content()
            } catch (t: Throwable) {
                contentFailed = true
                localError = t
                IosInAppDebugLogStore.log("Caught exception in $screenName Compose tree: ${t.message}")
            }
        }
    }
}

@Composable
internal fun CrashLogScreen(
    info: AppCrashInfo,
    screenName: String,
    copied: Boolean,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Warning Icon Header
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(32.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "Something Went Wrong",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Screen: $screenName  •  Time: ${info.timestamp}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Primary Action Buttons: COPY & DISMISS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (copied) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (copied) "Copied!" else "Copy Log",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dismiss")
                }
            }

            // Error Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Error Information",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = info.details.take(3000),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // In-App Debug Log Trace
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Recent App Activity (${info.logs.size} events)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (info.logs.isEmpty()) {
                        Text(
                            text = "No recent in-app log events.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val logText = info.logs.takeLast(30).joinToString("\n")
                        Text(
                            text = logText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
