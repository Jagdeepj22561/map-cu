package com.example.maps123.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CustomChatClientTest {
    @Test
    fun parsesPostgresTimestampWithSpaceAndShortUtcOffset() {
        assertEquals(
            Instant.parse("2026-09-15T12:34:56.123456Z").toEpochMilli(),
            CustomChatClient.parseServerTimestamp("2026-09-15 12:34:56.123456+00")
        )
    }

    @Test
    fun parsesStandardIsoTimestamp() {
        assertEquals(
            Instant.parse("2026-09-15T12:34:56.123Z").toEpochMilli(),
            CustomChatClient.parseServerTimestamp("2026-09-15T12:34:56.123Z")
        )
    }
}
