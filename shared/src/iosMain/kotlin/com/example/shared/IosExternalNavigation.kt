@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.shared

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/** Open a URL with the user's configured iOS application. */
internal fun openIosExternalUrl(urlString: String): Boolean {
    val value = urlString.trim()
    if (value.isBlank()) return false
    val url = NSURL(string = value) ?: return false
    return UIApplication.sharedApplication.openURL(url)
}

/** Convert a profile handle or URL into a safe web URL for the selected service. */
internal fun iosSocialUrl(kind: String, value: String): String? {
    val raw = value.trim()
    if (raw.isBlank()) return null
    if (raw.startsWith("https://", ignoreCase = true) || raw.startsWith("http://", ignoreCase = true)) {
        return raw
    }
    val handle = raw.removePrefix("@").trim('/').takeIf { it.isNotBlank() } ?: return null
    return when (kind.lowercase()) {
        "instagram" -> "https://instagram.com/$handle"
        "snapchat" -> "https://snapchat.com/add/$handle"
        "linkedin" -> "https://linkedin.com/in/$handle"
        else -> null
    }
}

/** Dial the first number in a comma/semicolon-separated helpline entry. */
internal fun iosHelplineUrl(number: String): String? {
    val first = number.split(',', ';').firstOrNull()?.trim().orEmpty()
    val digits = first.filter { it.isDigit() || it == '+' }
    return digits.takeIf { it.length >= 3 }?.let { "tel:$it" }
}
