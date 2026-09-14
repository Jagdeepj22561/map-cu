package com.example.shared.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class DirectChatIdTest {
    @Test
    fun matchesRenderAndIsIndependentOfUserOrder() {
        val first = "238b4772-6b4c-440e-bf6f-edec2977bcde"
        val second = "df805d14-ec4a-4664-ad0a-e9b62e8034ba"
        val expected = "d9726c49-6fa6-3837-afaf-ff4a9fb5ce9b"

        assertEquals(expected, directChatId(first, second))
        assertEquals(expected, directChatId(second, first))
    }
}
