package com.example.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ImagePreviewDialog(
    imageUrl: String,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    var isSending by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { if (!isSending) onDismiss() }) {
                            Icon(
                                Icons.Default.Close,
                                "Cancel",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.weight(1f))
                        Spacer(Modifier.width(48.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    renderImage(
                        imageUrl,
                        Modifier.fillMaxWidth().padding(16.dp),
                        ContentScale.Fit
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = caption,
                            onValueChange = onCaptionChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Add a caption...") },
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                isSending = true
                                onSend(caption)
                            },
                            enabled = !isSending,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isSending) "Sending..." else "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            renderImage(
                imageUrl,
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                ContentScale.Fit
            )

            AnimatedVisibility(
                visible = scale == 1f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        "Close",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
