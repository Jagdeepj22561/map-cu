package com.example.shared.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClientProvider {
    val client by lazy {
        require(SupabaseConfig.url.isNotBlank()) { "SUPABASE_URL is not configured" }
        require(SupabaseConfig.publishableKey.isNotBlank()) { "SUPABASE_PUBLISHABLE_KEY is not configured" }

        createSupabaseClient(
            supabaseUrl = SupabaseConfig.url,
            supabaseKey = SupabaseConfig.publishableKey
        ) {
            install(Auth) {
                // Keep the SDK refresher running for the entire app lifetime.
                // Individual requests still use AuthSessionManager so they wait
                // for an in-flight refresh instead of sending an expired JWT.
                alwaysAutoRefresh = true
            }
            install(Postgrest)
            install(Realtime)
        }
    }
}
