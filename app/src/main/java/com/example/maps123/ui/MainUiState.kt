package com.example.maps123.ui

import com.example.shared.repository.RouteIndex
import com.example.shared.Route
import com.google.android.gms.maps.model.LatLng

data class MainUiState(
    val allRoutes: List<Route> = emptyList(),
    val routeIndex: RouteIndex? = null,
    val endIndex: Map<String, List<Route>> = emptyMap(),
    val userLocation: LatLng? = null,
    val activeRoute: Route? = null,
    val isNavigating: Boolean = false,
    val instructionText: String = "Ready",
    val distanceLeft: Double = 0.0,
    val nearestIndex: Int = 0,
    val isAudioEnabled: Boolean = false,
    val showRoutePicker: Boolean = false,
    val initialRoutePickerDestination: String? = null,
    val lastCameraLocation: LatLng? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val unreadMessageCount: Int = 0,
    val pendingFriendRequestCount: Int = 0,
    val newAnnouncementCount: Int = 0
)
