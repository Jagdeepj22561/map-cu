package com.example.maps123.data.local

import android.content.Context
import com.example.shared.campusPlaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PlacePreloader {
    private const val PREF_NAME = "cache_prefs"
    private const val KEY_PRELOADED_PLACES = "places_preloaded"

    suspend fun preloadIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val alreadyLoaded = prefs.getBoolean(KEY_PRELOADED_PLACES, false)
        if (alreadyLoaded) return

        val db = AppDatabase.getInstance(context)
        val dao = db.placeDao()

        val entities = campusPlaces.map {
            PlaceEntity(
                name = it.name,
                latitude = it.latitude,
                longitude = it.longitude,
                category = it.category.name
            )
        }

        withContext(Dispatchers.IO) {
            dao.insertAll(entities)
        }

        prefs.edit().putBoolean(KEY_PRELOADED_PLACES, true).apply()
    }
}
