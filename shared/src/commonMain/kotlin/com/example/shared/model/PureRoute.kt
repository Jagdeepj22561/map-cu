package com.example.shared.model

import com.example.shared.GeoPoint

data class PureRoute(
    val id: String,
    val name: String,
    val points: List<PureRoutePoint>
)

data class PureRoutePoint(
    val position: GeoPoint,
    val instruction: String? = null
)
