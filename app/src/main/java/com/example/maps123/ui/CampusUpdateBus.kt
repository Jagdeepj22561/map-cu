package com.example.maps123.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class CampusUpdateEvent(
    val key: String,
    val message: String,
    val postId: String? = null
)

/** Delivers transient updates above every app screen and deduplicates FCM/poll overlap. */
object CampusUpdateBus {
    private const val DEDUPE_MS = 120_000L
    private val recentKeys = mutableMapOf<String, Long>()
    private val mutableEvents = MutableSharedFlow<CampusUpdateEvent>(extraBufferCapacity = 16)
    val events = mutableEvents.asSharedFlow()

    fun publish(event: CampusUpdateEvent) {
        val now = System.currentTimeMillis()
        synchronized(recentKeys) {
            recentKeys.entries.removeAll { now - it.value > DEDUPE_MS }
            if (recentKeys.containsKey(event.key)) return
            recentKeys[event.key] = now
        }
        mutableEvents.tryEmit(event)
    }
}
