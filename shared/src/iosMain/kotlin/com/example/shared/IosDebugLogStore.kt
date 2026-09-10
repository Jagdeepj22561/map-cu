package com.example.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

internal object IosInAppDebugLogStore {
    private const val maxEntries = 200
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun log(message: String) {
        val timestamped = "${iosDebugTimestamp()}  $message"
        _entries.value = (_entries.value + timestamped).takeLast(maxEntries)
    }

    fun clear() {
        _entries.value = emptyList()
    }
}

internal fun iosDebugTimestamp(): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = "HH:mm:ss"
    }
    return formatter.stringFromDate(NSDate())
}
