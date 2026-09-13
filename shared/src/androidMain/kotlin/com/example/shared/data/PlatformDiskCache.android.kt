package com.example.shared.data

import android.content.Context
import android.content.SharedPreferences

actual object PlatformDiskCache {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("app_shared_disk_cache", Context.MODE_PRIVATE)
    }

    actual fun get(key: String): String? {
        return prefs?.getString(key, null)
    }

    actual fun set(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    actual fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }
}