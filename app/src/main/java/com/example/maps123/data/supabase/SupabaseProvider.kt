package com.example.maps123.data.supabase

import io.github.jan.supabase.SupabaseClient
import com.example.shared.data.SupabaseClientProvider

/**
 * Android compatibility facade. The shared KMP client is now the single
 * Supabase client so authentication/session state is shared with repositories.
 */
object SupabaseProvider {
    val client: SupabaseClient
        get() = SupabaseClientProvider.client
}
