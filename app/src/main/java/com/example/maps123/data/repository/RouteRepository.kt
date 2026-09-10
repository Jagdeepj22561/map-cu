package com.example.maps123.data.repository


import com.example.shared.RoutesData

class RouteRepository {

    fun getAllRoutes(): List<com.example.shared.Route> {
        return RoutesData.allRoutes
    }

    fun getRouteById(id: String): com.example.shared.Route? {
        return RoutesData.allRoutes
            .firstOrNull { it.id == id }
    }
}

