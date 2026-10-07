package com.example.shared.repository

/**
 * Parses PostgreSQL / ISO-8601 database timestamp strings to epoch milliseconds.
 * Supports strings like "2026-09-15 12:34:56.123456+00", "2026-09-15T12:34:56.123Z",
 * or raw millisecond strings.
 */
fun parseDatabaseTimestampOrNull(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val raw = value.trim()

    // 1. If it's already an epoch millisecond number
    raw.toLongOrNull()?.let { return it }

    // 2. Simple fallback parsing for ISO-8601 string: YYYY-MM-DD[T| ]HH:MM:SS[.fraction][Z|±offset]
    return runCatching {
        val normalized = raw.replace(' ', 'T')
        // Extract basic date and time components
        val datePart = normalized.substringBefore('T')
        val timeWithOffset = normalized.substringAfter('T', "")
        if (datePart.length < 10) return null

        val dateComponents = datePart.split('-')
        if (dateComponents.size != 3) return null
        val year = dateComponents[0].toInt()
        val month = dateComponents[1].toInt()
        val day = dateComponents[2].toInt()

        var hour = 0
        var minute = 0
        var second = 0
        var millis = 0
        var offsetMillis = 0L

        if (timeWithOffset.isNotBlank()) {
            val tzCharIdx = timeWithOffset.indexOfAny(charArrayOf('Z', '+', '-'), startIndex = 1)
            val timePart = if (tzCharIdx >= 0) timeWithOffset.substring(0, tzCharIdx) else timeWithOffset
            val tzPart = if (tzCharIdx >= 0) timeWithOffset.substring(tzCharIdx) else ""

            val timeParts = timePart.split(':')
            if (timeParts.isNotEmpty()) hour = timeParts[0].toIntOrNull() ?: 0
            if (timeParts.size > 1) minute = timeParts[1].toIntOrNull() ?: 0
            if (timeParts.size > 2) {
                val secParts = timeParts[2].split('.')
                second = secParts[0].toIntOrNull() ?: 0
                if (secParts.size > 1) {
                    val fracStr = secParts[1].take(3).padEnd(3, '0')
                    millis = fracStr.toIntOrNull() ?: 0
                }
            }

            if (tzPart.startsWith("+") || tzPart.startsWith("-")) {
                val sign = if (tzPart.startsWith("+")) -1 else 1 // to adjust to UTC
                val tzContent = tzPart.substring(1)
                val tzHours: Int
                val tzMins: Int
                if (tzContent.contains(':')) {
                    val tzSplit = tzContent.split(':')
                    tzHours = tzSplit[0].toIntOrNull() ?: 0
                    tzMins = tzSplit.getOrNull(1)?.toIntOrNull() ?: 0
                } else {
                    tzHours = tzContent.toIntOrNull() ?: 0
                    tzMins = 0
                }
                offsetMillis = sign * ((tzHours * 60L + tzMins) * 60_000L)
            }
        }

        // Convert UTC date-time to epoch millis (Gregorian calendar calculations)
        daysFromCivil(year, month, day) * 86_400_000L +
            hour * 3_600_000L +
            minute * 60_000L +
            second * 1_000L +
            millis +
            offsetMillis
    }.getOrNull()
}

/**
 * Returns number of days since 1970-01-01 (Unix epoch) for a Gregorian civil date.
 * Algorithmic conversion from Howard Hinnant's date algorithms.
 */
private fun daysFromCivil(y: Int, m: Int, d: Int): Long {
    var year = y
    var month = m
    if (month <= 2) {
        year -= 1
        month += 9
    } else {
        month -= 3
    }
    val era = (if (year >= 0) year else year - 399) / 400
    val yoe = year - era * 400
    val doy = (153 * month + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468L
}
