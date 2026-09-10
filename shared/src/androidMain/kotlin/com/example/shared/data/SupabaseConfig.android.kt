package com.example.shared.data

actual object SupabaseConfig {
    // Keep the project URL in source so Android cannot accidentally use a stale
    // SUPABASE_URL value from local.properties.
    actual val url: String = "https://yytytngfxdssdhozrfwo.supabase.co"
    actual val publishableKey: String = BuildKonfig.SUPABASE_PUBLISHABLE_KEY
}
