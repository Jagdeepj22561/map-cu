package com.example.shared.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DatabaseTimestampTest {
    @Test
    fun parsesPostgresTimestampWithSpaceAndShortOffset() {
        assertEquals(
            Instant.parse("2026-09-15T12:34:56.123456Z").toEpochMilliseconds(),
            parseDatabaseTimestampOrNull("2026-09-15 12:34:56.123456+00")
        )
    }

    @Test
    fun parsesIsoTimestamp() {
        assertEquals(
            Instant.parse("2026-09-15T12:34:56.123Z").toEpochMilliseconds(),
            parseDatabaseTimestampOrNull("2026-09-15T12:34:56.123Z")
        )
    }
}
