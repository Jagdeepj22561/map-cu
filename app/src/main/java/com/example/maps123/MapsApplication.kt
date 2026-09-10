package com.example.maps123

import android.app.Application
import com.google.firebase.FirebaseApp
import com.example.maps123.data.local.RoutePreloader
import com.example.maps123.data.local.PlacePreloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MapsApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        appScope.launch {
            try {
                RoutePreloader.preloadIfNeeded(this@MapsApplication)
                PlacePreloader.preloadIfNeeded(this@MapsApplication)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
