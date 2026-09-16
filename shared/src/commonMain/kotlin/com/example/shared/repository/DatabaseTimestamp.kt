package com.example.shared.repository

import kotlin.time.Instant

/** Accepts ISO-8601 and PostgreSQL/PostgREST timestamp spellings. */
fun parseDatabaseTimestampOrNull(value: String): Long? {
    var normalized = value.trim().replace(' ', 'T')
    normalized = normalized.replace(Regex("([+-]\\d{2})$"), "$1:00")
    normalized = normalized.replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")
    return runCatching { Instant.parse(normalized).toEpochMilliseconds() }.getOrNull()
}
