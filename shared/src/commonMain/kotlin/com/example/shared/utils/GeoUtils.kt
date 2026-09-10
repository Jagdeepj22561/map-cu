package com.example.shared.utils

import com.example.shared.GeoPoint
import com.example.shared.Route
import kotlin.math.*

object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the distance between two points in meters using the Haversine formula.
     */
    fun distanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1 = (p1.lat * PI) / 180.0
        val lon1 = (p1.lng * PI) / 180.0
        val lat2 = (p2.lat * PI) / 180.0
        val lon2 = (p2.lng * PI) / 180.0

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    fun minDistanceToRoute(point: GeoPoint, route: Route): Double {
        if (route.points.isEmpty()) return Double.MAX_VALUE
        var minD = Double.MAX_VALUE
        for (p in route.points) {
            val d = distanceMeters(point, p)
            if (d < minD) minD = d
        }
        return minD
    }
}
