package com.example.shared.data

actual object SupabaseConfig {
    actual val url: String = BuildConfig.SUPABASE_URL
    actual val publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY
}
