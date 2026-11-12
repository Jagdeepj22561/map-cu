package com.example.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIPasteboard
import kotlin.native.setUnhandledExceptionHook

data class AppCrashInfo(
    val title: String,
    val details: String,
    val timestamp: String,
    val logs: List<String>
)

object IosInAppDebugLogStore {
    private const val maxEntries = 300
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun log(message: String) {
        val timestamped = "${iosDebugTimestamp()}  $message"
        println("[APP_LOG] $timestamped")
        _entries.value = (_entries.value + timestamped).takeLast(maxEntries)
    }

    fun getAllLogs(): List<String> = _entries.value

    fun getAllLogsText(): String = _entries.value.joinToString("\n")

    fun clear() {
        _entries.value = emptyList()
    }
}

object IosGlobalErrorState {
    private val _currentError = MutableStateFlow<AppCrashInfo?>(null)
    val currentError: StateFlow<AppCrashInfo?> = _currentError.asStateFlow()

    fun reportError(title: String, details: String) {
        IosInAppDebugLogStore.log("ERROR REPORTED: $title - $details")
        val info = AppCrashInfo(
            title = title,
            details = details,
            timestamp = iosDebugTimestamp(),
            logs = IosInAppDebugLogStore.getAllLogs()
        )
        _currentError.value = info

        val persisted = buildString {
            appendLine("=== CRASH / ERROR REPORT ===")
            appendLine("Time: ${info.timestamp}")
            appendLine("Title: ${info.title}")
            appendLine("Details:")
            appendLine(info.details)
            appendLine("\n=== RECENT IN-APP LOGS ===")
            info.logs.forEach { appendLine(it) }
        }
        iosSetLastCrashLog(persisted)
    }

    fun clear() {
        _currentError.value = null
    }
}

fun iosCopyTextToClipboard(text: String): Boolean {
    return runCatching {
        UIPasteboard.generalPasteboard.string = text
        true
    }.getOrDefault(false)
}

fun iosGetLastCrashLog(): String? {
    return runCatching {
        NSUserDefaults.standardUserDefaults.stringForKey("last_crash_log")
    }.getOrNull()
}

fun iosSetLastCrashLog(log: String) {
    runCatching {
        NSUserDefaults.standardUserDefaults.setObject(log, forKey = "last_crash_log")
        NSUserDefaults.standardUserDefaults.synchronize()
    }
}

fun iosClearLastCrashLog() {
    runCatching {
        NSUserDefaults.standardUserDefaults.removeObjectForKey("last_crash_log")
        NSUserDefaults.standardUserDefaults.synchronize()
    }
}

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
fun initGlobalExceptionHandler() {
    runCatching {
        setUnhandledExceptionHook { throwable ->
            val trace = throwable.stackTraceToString()
            val msg = throwable.message ?: throwable::class.simpleName ?: "Unknown error"
            IosGlobalErrorState.reportError("Uncaught Exception: $msg", trace)
        }
    }
}

internal fun iosDebugTimestamp(): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = "HH:mm:ss"
    }
    return formatter.stringFromDate(NSDate())
}
