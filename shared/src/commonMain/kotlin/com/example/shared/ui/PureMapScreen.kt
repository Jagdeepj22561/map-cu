package com.example.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.shared.Place

@Composable
fun PureMapScreen(
    isNavigating: Boolean,
    instructionText: String,
    distanceLeft: Float,
    selectedPlace: Place?,
    onClosePlace: () -> Unit,
    onShowRoutePicker: (String?) -> Unit,
    onRecenter: () -> Unit,
    onStopNavigation: () -> Unit,
    renderMap: @Composable () -> Unit,
    renderDirectionsIcon: @Composable () -> Unit,
    renderMyLocationIcon: @Composable () -> Unit,
    renderCloseIcon: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Map implementation
        renderMap()

        // Selected Place Bottom Sheet / Card
        if (selectedPlace != null && !isNavigating) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 104.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClosePlace) {
                            renderCloseIcon()
                        }
                        Text(
                            text = selectedPlace.name,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onShowRoutePicker(selectedPlace.name)
                            onClosePlace()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        renderDirectionsIcon()
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Navigation")
                    }
                }
            }
        }
        
        // Navigation Info Overlay
        if (isNavigating) {
             Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 104.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
             ) {
                 Row(
                     modifier = Modifier.padding(16.dp).fillMaxWidth(),
                     horizontalArrangement = Arrangement.SpaceBetween,
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                     Column(modifier = Modifier.weight(1f)) {
                         Text(
                             text = instructionText,
                             style = MaterialTheme.typography.bodyLarge,
                             color = MaterialTheme.colorScheme.onPrimaryContainer
                         )
                         Text(
                             text = "${distanceLeft.toInt()} m left",
                             style = MaterialTheme.typography.bodyMedium,
                             color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                         )
                     }
                     IconButton(onClick = onStopNavigation) {
                         renderCloseIcon()
                     }
                 }
             }
        }
        
        val fabBottomOffset = if (isNavigating || selectedPlace != null) 168.dp else 112.dp

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = fabBottomOffset),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            FloatingActionButton(
                onClick = { onShowRoutePicker(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                )
            ) {
                renderDirectionsIcon()
            }

            FloatingActionButton(
                onClick = onRecenter,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 6.dp
                )
            ) {
                renderMyLocationIcon()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
