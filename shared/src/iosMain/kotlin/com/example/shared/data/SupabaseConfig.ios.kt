package com.example.shared.data

import platform.Foundation.NSBundle

actual object SupabaseConfig {
    private val info = NSBundle.mainBundle.infoDictionary

    actual val url: String = info?.get("SUPABASE_URL") as? String ?: ""
    actual val publishableKey: String = info?.get("SUPABASE_PUBLISHABLE_KEY") as? String ?: ""
}
