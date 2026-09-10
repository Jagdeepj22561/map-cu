package com.example.shared.data

actual object SupabaseConfig {
    actual val url: String = BuildKonfig.SUPABASE_URL
    actual val publishableKey: String = BuildKonfig.SUPABASE_PUBLISHABLE_KEY
}
