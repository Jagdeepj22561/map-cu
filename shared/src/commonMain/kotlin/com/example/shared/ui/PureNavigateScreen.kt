package com.example.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PureNavigateStartScreen(
    onShowRoutePicker: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Campus Navigator", fontSize = 28.sp)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onShowRoutePicker,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Start Navigation", fontSize = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("Select your route to begin", color = Color.Gray)
    }
}

@Composable
fun PureNavigationOverlay(
    instructionText: String,
    distanceLeft: Float,
    onStopNavigation: () -> Unit,
    renderStopIcon: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top instruction card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = instructionText, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(text = "${distanceLeft.toInt()} m left", fontSize = 14.sp)
            }
        }

        // Bottom exit button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Button(
                onClick = onStopNavigation,
                modifier = Modifier.fillMaxWidth()
            ) {
                renderStopIcon()
                Spacer(Modifier.width(8.dp))
                Text("Exit Navigation")
            }
        }
    }
}
