package com.example.shared.data

expect object PlatformDiskCache {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
}