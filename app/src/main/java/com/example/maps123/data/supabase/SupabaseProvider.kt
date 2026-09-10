package com.example.maps123.data.supabase

import com.example.maps123.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Shared entry point for all Supabase features in the Android app.
 *
 * The publishable key is intended for mobile clients. Never add a service-role
 * key here; Render is the only trusted place for that key.
 */
object SupabaseProvider {
    val client: SupabaseClient by lazy {
        val url = BuildConfig.SUPABASE_URL.trim()
        val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()

        check(url.isNotEmpty() && publishableKey.isNotEmpty()) {
            "Supabase is not configured. Add SUPABASE_URL and " +
                "SUPABASE_PUBLISHABLE_KEY to local.properties."
        }

        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = publishableKey
        ) {
            install(Auth) {
                // Must match the Android intent-filter and Supabase redirect URL.
                scheme = "maps123"
                host = "auth"
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }
}
