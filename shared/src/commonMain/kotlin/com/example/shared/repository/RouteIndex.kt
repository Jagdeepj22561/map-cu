package com.example.shared.repository

import com.example.shared.Route

class RouteIndex(private val routes: List<Route>) {
    fun getByName(name: String): Route? {
        return routes.find { it.name == name }
    }
}
