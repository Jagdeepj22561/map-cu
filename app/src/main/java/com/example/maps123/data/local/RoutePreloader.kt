package com.example.maps123.data.local

import android.content.Context
import com.example.shared.RoutesData
import com.example.maps123.utils.RouteJsonConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RoutePreloader {

    private const val PREF_NAME = "cache_prefs"
    private const val KEY_PRELOADED = "routes_preloaded"

    suspend fun preloadIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val alreadyLoaded = prefs.getBoolean(KEY_PRELOADED, false)

        if (alreadyLoaded) return

        val db = AppDatabase.getInstance(context)
        val dao = db.routeDao()

        val entities = RoutesData.allRoutes.map {
            RouteEntity(
                id = it.id,
                name = it.name,
                pointsJson = RouteJsonConverter.geoPointsToJson(it.points)
            )
        }

        withContext(Dispatchers.IO) {
            dao.insertAll(entities)
        }

        prefs.edit().putBoolean(KEY_PRELOADED, true).apply()
    }
}
