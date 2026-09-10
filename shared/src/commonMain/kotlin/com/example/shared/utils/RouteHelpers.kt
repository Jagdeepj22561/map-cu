package com.example.shared.utils

import com.example.shared.GeoPoint
import com.example.shared.Route
import kotlin.math.min

object RouteHelpers {
    fun distanceToEnd(currentLoc: GeoPoint, route: Route, currentSegmentIndex: Int): Double {
        if (route.points.isEmpty()) return 0.0
        
        // Distance from current location to next point
        val nextIdx = min(currentSegmentIndex + 1, route.points.size - 1)
        var dist = GeoUtils.distanceMeters(currentLoc, route.points[nextIdx])
        
        // Add remaining segments
        for (i in nextIdx until (route.points.size - 1)) {
            dist += GeoUtils.distanceMeters(route.points[i], route.points[i+1])
        }
        return dist
    }

    fun findNearestSegmentIndex(currentLoc: GeoPoint, route: Route): Int {
        if (route.points.isEmpty()) return 0
        
        var minIdx = 0
        var minDist = Double.MAX_VALUE
        
        for (i in route.points.indices) {
            val d = GeoUtils.distanceMeters(currentLoc, route.points[i])
            if (d < minDist) {
                minDist = d
                minIdx = i
            }
        }
        return minIdx
    }
}
