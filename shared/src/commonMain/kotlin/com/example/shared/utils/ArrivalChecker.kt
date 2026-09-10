package com.example.shared.utils

import com.example.shared.GeoPoint

object ArrivalChecker {
    fun hasArrived(currentLoc: GeoPoint, destinationLoc: GeoPoint, thresholdMeters: Double = 15.0): Boolean {
        return GeoUtils.distanceMeters(currentLoc, destinationLoc) < thresholdMeters
    }
}
