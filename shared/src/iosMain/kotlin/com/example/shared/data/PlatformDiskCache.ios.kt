package com.example.shared.data

import platform.Foundation.NSUserDefaults

actual object PlatformDiskCache {
    actual fun get(key: String): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey(key)
    }

    actual fun set(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
    }

    actual fun remove(key: String) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
    }
}