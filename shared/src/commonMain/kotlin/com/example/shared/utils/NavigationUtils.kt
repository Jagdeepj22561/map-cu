package com.example.shared.utils

import com.example.shared.GeoPoint
import kotlin.math.*

object NavigationUtils {
    fun getTurnInstruction(prev: GeoPoint, current: GeoPoint, next: GeoPoint): String {
        val bearing1 = calculateBearing(prev, current)
        val bearing2 = calculateBearing(current, next)
        
        var diff = bearing2 - bearing1
        while (diff < -180) diff += 360
        while (diff > 180) diff -= 360
        
        return when {
            diff > 45 -> "Turn right"
            diff < -45 -> "Turn left"
            else -> "Continue straight"
        }
    }

    private fun calculateBearing(start: GeoPoint, end: GeoPoint): Double {
        val lat1 = (start.lat * PI) / 180.0
        val lon1 = (start.lng * PI) / 180.0
        val lat2 = (end.lat * PI) / 180.0
        val lon2 = (end.lng * PI) / 180.0

        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (atan2(y, x) * 180.0) / PI
    }
}
