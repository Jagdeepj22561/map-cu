package com.example.maps123.ui.screens
import androidx.compose.foundation.background
import com.example.maps123.utils.toLatLng

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.shared.campusPlaces
import com.example.maps123.ui.MainUiState
import com.example.maps123.utils.BitmapUtils.createPinWithLabel
import com.example.shared.Place
import com.example.shared.Route
import com.example.maps123.data.local.AppDatabase
import com.example.maps123.data.local.PlaceEntity
import com.example.shared.PlaceCategory
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

import com.example.shared.ui.PureMapScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    uiState: MainUiState,
    cameraPositionState: CameraPositionState,
    hasLocationPermission: Boolean,
    searchQuery: String = "",
    selectedCategory: PlaceCategory? = null,
    onCameraMoved: (LatLng) -> Unit,
    onShowRoutePicker: (String?) -> Unit,
    onStartNavigation: (Route) -> Unit,
    onStopNavigation: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddFriendClick: () -> Unit,
    onMapTap: () -> Unit = {}
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val campusBounds = remember {
        LatLngBounds(
            LatLng(30.763469, 76.562945),
            LatLng(30.776094, 76.582640)
        )
    }

    val campusMapStyle = remember {
        MapStyleOptions(
            """
            [
              { "featureType": "all", "elementType": "labels", "stylers": [{ "visibility": "off" }] },
              { "featureType": "poi", "stylers": [{ "visibility": "off" }] }
            ]
            """.trimIndent()
        )
    }

    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var localPlaces by remember { mutableStateOf<List<Place>>(emptyList()) }

    LaunchedEffect(Unit) {
        runCatching {
            val dao = AppDatabase.getInstance(context).placeDao()
            val rows = dao.getAllPlaces()
            localPlaces = rows.map { r ->
                Place(
                    name = r.name,
                    latitude = r.latitude,
                    longitude = r.longitude,
                    category = PlaceCategory.values().firstOrNull { it.name.equals(r.category, ignoreCase = true) }
                        ?: PlaceCategory.OTHER
                )
            }
        }
    }
    var isMapLoaded by remember { mutableStateOf(false) }

    // Follow navigation camera
    LaunchedEffect(uiState.lastCameraLocation) {
        uiState.lastCameraLocation?.let { loc ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(loc, 17f)
            )
        }
    }

    // ---------- OPTIMIZED ZOOM LISTENER ----------
    val zoomState = remember { mutableFloatStateOf(17f) }

    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.isMoving }
            .distinctUntilChanged()
            .collectLatest { moving ->
                if (!moving) {
                    zoomState.floatValue =
                        (cameraPositionState.position.zoom * 10)
                            .roundToInt() / 10f
                }
            }
    }

    // ---------- OPTIMIZED FILTERING ----------
    val filteredPlaces by remember(
        searchQuery,
        selectedCategory,
        zoomState.floatValue
    ) {
        derivedStateOf {

            var places = if (localPlaces.isNotEmpty()) localPlaces else campusPlaces

            if (selectedCategory != null) {
                places = places.filter { it.category == selectedCategory }
            }

            if (searchQuery.isNotBlank()) {
                places = places.filter {
                    it.name.contains(searchQuery, ignoreCase = true)
                }
            }

            // Only apply zoom-based filtering when no category is explicitly selected
            if (selectedCategory == null) {
                when {
                    zoomState.floatValue < 15f ->
                        places.filter {
                            it.category.name in listOf("GATE", "HOSTEL")
                        }

                    zoomState.floatValue < 17f ->
                        places.filter {
                            it.category.name in listOf("GATE", "HOSTEL", "BLOCK")
                        }

                    else -> places
                }
            } else {
                places
            }
        }
    }

    PureMapScreen(
        isNavigating = uiState.isNavigating,
        instructionText = uiState.instructionText,
        distanceLeft = uiState.distanceLeft.toFloat(),
        selectedPlace = selectedPlace,
        onClosePlace = { selectedPlace = null },
        onShowRoutePicker = onShowRoutePicker,
        onRecenter = {
            scope.launch {
                val target =
                    if (hasLocationPermission && uiState.userLocation != null)
                        uiState.userLocation
                    else campusBounds.center

                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(target!!, 17f)
                )
            }
        },
        onStopNavigation = onStopNavigation,
        renderMap = {

            GoogleMap(
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission,
                    latLngBoundsForCameraTarget = campusBounds,
                    mapStyleOptions = campusMapStyle
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false,
                    compassEnabled = false
                ),
                modifier = Modifier.fillMaxSize(),
                onMapLoaded = {
                    isMapLoaded = true
                },
                onMapClick = {
                    selectedPlace = null
                    onMapTap()
                }
            ) {

                if (isMapLoaded) {

                    filteredPlaces.forEach { place ->

                        val markerIcon = remember(place.name, place.category) {
                            createPinWithLabel(
                                context,
                                place.name,
                                place.category
                            )
                        }

                        Marker(
                            state = remember(place.name) {
                                MarkerState(place.toLatLng())
                            },
                            icon = markerIcon,
                            anchor = Offset(0.5f, 1f),
                            onClick = {
                                selectedPlace = place
                                onMapTap()
                                true
                            }
                        )
                    }
                }

                if (uiState.isNavigating && uiState.activeRoute != null) {
                    uiState.activeRoute?.let { route ->
                        val fromIndex = uiState.nearestIndex.coerceIn(0, route.points.lastIndex)
                        val remainingPoints = route.points
                            .subList(fromIndex, route.points.size)
                            .map { it.toLatLng() }
                        Polyline(
                            // Only draw the route still ahead of the user. Since
                            // nearestIndex can move in either direction, backing
                            // up naturally restores the removed section.
                            points = remainingPoints,
                            color = MaterialTheme.colorScheme.primary,
                            width = 12f
                        )
                    }
                }
            }
        },
        renderDirectionsIcon = {
            Icon(Icons.Default.Directions, contentDescription = "Start Navigation")
        },
        renderMyLocationIcon = {
            Icon(Icons.Default.MyLocation, contentDescription = "Recenter")
        },
        renderCloseIcon = {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    )
}
