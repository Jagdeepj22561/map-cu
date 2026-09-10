package com.example.maps123.utils

import com.example.shared.GeoPoint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object RouteJsonConverter {
    private val gson = Gson()

    private data class RoutePointDTO(
        val lat: Double,
        val lng: Double,
        val instruction: String?
    )

    fun geoPointsToJson(points: List<GeoPoint>): String {
        val dtos = points.map {
            RoutePointDTO(
                lat = it.lat,
                lng = it.lng,
                instruction = it.instruction
            )
        }
        return gson.toJson(dtos)
    }

    fun jsonToGeoPoints(json: String): List<GeoPoint> {
        return try {
            val type = object : TypeToken<List<RoutePointDTO>>() {}.type
            val dtos: List<RoutePointDTO> = gson.fromJson(json, type) ?: emptyList()
            dtos.map { dto ->
                GeoPoint(
                    lat = dto.lat,
                    lng = dto.lng,
                    instruction = dto.instruction
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
