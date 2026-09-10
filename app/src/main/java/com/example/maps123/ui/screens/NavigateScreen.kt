package com.example.maps123.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.maps123.ui.MainUiState
import com.example.shared.utils.RouteEngine.processRoute
import com.example.maps123.utils.toLatLng
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*

import com.example.shared.ui.PureNavigateStartScreen
import com.example.shared.ui.PureNavigationOverlay

@Composable
fun NavigateScreen(
    uiState: MainUiState,
    cameraPositionState: CameraPositionState,
    campusBounds: LatLngBounds,
    mapStyleOptions: MapStyleOptions?,
    hasLocationPermission: Boolean,
    onShowRoutePicker: () -> Unit,
    onStopNavigation: () -> Unit
) {
    if (!uiState.isNavigating) {
        PureNavigateStartScreen(onShowRoutePicker = onShowRoutePicker)
    } else {
        // Navigation Map
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission,
                    latLngBoundsForCameraTarget = campusBounds,
                    mapStyleOptions = mapStyleOptions
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = true,
                    mapToolbarEnabled = false,
                    compassEnabled = false
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                // Show active route remaining polyline when navigating
                uiState.activeRoute?.let { route ->
                    val start = route.points.first().toLatLng()
                    val dest = route.points.last().toLatLng()
                    
                    val smoothRemaining = processRoute(route.points).map { it.toLatLng() }

                    Polyline(
                        points = smoothRemaining,
                        color = Color.Blue,
                        width = 18f
                    )

                    // start & destination markers
                    Marker(state = MarkerState(start), title = "Start")
                    Marker(state = MarkerState(dest), title = "Destination")
                }
                
                // Campus boundary
                Polygon(
                    points = listOf(
                        LatLng(30.776094, 76.562945),
                        LatLng(30.776094, 76.582640),
                        LatLng(30.763469, 76.582640),
                        LatLng(30.763469, 76.562945)
                    ),
                    fillColor = Color.Transparent,
                    strokeColor = Color.Red,
                    strokeWidth = 3f
                )
            }

            // Common UI Overlay from shared module
            PureNavigationOverlay(
                instructionText = uiState.instructionText,
                distanceLeft = uiState.distanceLeft.toFloat(),
                onStopNavigation = onStopNavigation,
                renderStopIcon = { Icon(Icons.Default.Close, contentDescription = null) }
            )
        }
    }
}
