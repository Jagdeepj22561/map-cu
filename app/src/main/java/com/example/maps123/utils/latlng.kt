package com.example.maps123.utils

import com.example.shared.Place
import com.example.shared.GeoPoint
import com.google.android.gms.maps.model.LatLng

fun Place.toLatLng(): LatLng =
    LatLng(latitude, longitude)

fun GeoPoint.toLatLng(): LatLng =
    LatLng(lat, lng)

fun LatLng.toGeoPoint(): GeoPoint =
    GeoPoint(latitude, longitude)
