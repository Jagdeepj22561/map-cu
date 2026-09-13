package com.example.shared.repository

import com.example.shared.data.SupabaseClientProvider
import com.example.shared.model.EventListItem
import com.example.shared.model.EventStatus
import com.example.shared.model.EventTimelineItem
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class EventRepository {
    private val client = SupabaseClientProvider.client

    @Serializable
    private data class EventRow(
        val id: String,
        val title: String,
        @SerialName("image_url") val imageUrl: String? = null,
        @SerialName("event_venue") val eventVenue: String? = null,
        @SerialName("event_time") val eventTime: String? = null,
        @SerialName("event_mode") val eventMode: String? = null,
        @SerialName("event_max_members") val eventMaxMembers: Int? = null,
        @SerialName("event_category") val eventCategory: String? = null,
        val type: String = "EVENT"
    )

    @Serializable
    private data class TeamCountRow(@SerialName("announcement_id") val announcementId: String)

    @Serializable
    private data class RegistrationInsert(
        @SerialName("announcement_id") val announcementId: String,
        @SerialName("user_id") val userId: String
    )

    @Serializable
    private data class RegistrationRow(
        val id: String,
        @SerialName("announcement_id") val announcementId: String,
        @SerialName("user_id") val userId: String
    )

    @Serializable
    private data class TimelineRow(
        val id: String,
        @SerialName("announcement_id") val announcementId: String,
        val title: String,
        val description: String = "",
        @SerialName("scheduled_at") val scheduledAt: String? = null,
        @SerialName("sort_order") val sortOrder: Int = 0
    )

    @Serializable
    private data class TimelineInsert(
        @SerialName("announcement_id") val announcementId: String,
        val title: String,
        val description: String = "",
        @SerialName("scheduled_at") val scheduledAt: String? = null,
        @SerialName("sort_order") val sortOrder: Int = 0
    )

    suspend fun getEvents(
        category: String? = null,
        mode: String? = null,
        status: EventStatus? = null,
        searchQuery: String = ""
    ): List<EventListItem> {
        val events = client.from("announcements").select {
            filter {
                eq("type", "EVENT")
                if (!category.isNullOrBlank()) eq("event_category", category)
                if (!mode.isNullOrBlank()) eq("event_mode", mode)
                if (searchQuery.isNotBlank()) {
                    or {
                        ilike("title", "%$searchQuery%")
                        ilike("content", "%$searchQuery%")
                    }
                }
            }
            order("timestamp", Order.DESCENDING)
        }.decodeList<EventRow>()

        if (events.isEmpty()) return emptyList()

        val eventIds = events.map { it.id }

        // Efficient single-query batch fetch for team counts
        val teamCounts: Map<String, Int> = runCatching {
            client.from("event_teams").select {
                filter {
                    isIn("announcement_id", eventIds)
                }
            }.decodeList<TeamCountRow>()
                .groupingBy { it.announcementId }
                .eachCount()
        }.getOrDefault(emptyMap())

        // Efficient single-query batch fetch for participant counts
        val participantCounts: Map<String, Int> = runCatching {
            client.from("event_registrations").select {
                filter {
                    isIn("announcement_id", eventIds)
                }
            }.decodeList<RegistrationRow>()
                .groupingBy { it.announcementId }
                .eachCount()
        }.getOrDefault(emptyMap())

        val items = events.map { row ->
            EventListItem(
                announcementId = row.id,
                title = row.title,
                imageUrl = row.imageUrl,
                eventVenue = row.eventVenue,
                eventTime = row.eventTime,
                eventMode = row.eventMode,
                eventMaxMembers = row.eventMaxMembers,
                eventCategory = row.eventCategory,
                status = EventStatus.fromEventTime(row.eventTime),
                participantCount = participantCounts[row.id] ?: 0,
                teamCount = teamCounts[row.id] ?: 0
            )
        }

        return if (status != null) {
            items.filter { it.status == status }
        } else {
            items
        }
    }

    suspend fun registerForEvent(announcementId: String) {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        client.from("event_registrations").insert(RegistrationInsert(announcementId, uid))
    }

    suspend fun unregisterFromEvent(announcementId: String) {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        client.from("event_registrations").delete {
            filter { eq("announcement_id", announcementId); eq("user_id", uid) }
        }
    }

    suspend fun isRegisteredForEvent(announcementId: String): Boolean {
        val uid = client.auth.currentUserOrNull()?.id ?: return false
        return runCatching {
            client.from("event_registrations").select {
                filter { eq("announcement_id", announcementId); eq("user_id", uid) }
            }.decodeList<RegistrationRow>().isNotEmpty()
        }.getOrDefault(false)
    }

    suspend fun getRegistrationCount(announcementId: String): Int = runCatching {
        client.from("event_registrations").select {
            filter { eq("announcement_id", announcementId) }
        }.decodeList<RegistrationRow>().size
    }.getOrDefault(0)

    suspend fun getEventTimelineItems(announcementId: String): List<EventTimelineItem> = runCatching {
        client.from("event_timeline_items").select {
            filter { eq("announcement_id", announcementId) }
            order("sort_order", Order.ASCENDING)
        }.decodeList<TimelineRow>().map { row ->
            EventTimelineItem(
                id = row.id,
                announcementId = row.announcementId,
                title = row.title,
                description = row.description,
                scheduledAt = row.scheduledAt,
                sortOrder = row.sortOrder
            )
        }
    }.getOrDefault(emptyList())

    suspend fun createTimelineItem(
        announcementId: String,
        title: String,
        description: String,
        scheduledAt: String?,
        sortOrder: Int
    ) {
        client.auth.currentUserOrNull()?.id ?: error("Login required")
        client.from("event_timeline_items").insert(
            TimelineInsert(announcementId, title.trim(), description.trim(), scheduledAt, sortOrder)
        )
    }

    suspend fun deleteTimelineItem(itemId: String) {
        client.auth.currentUserOrNull()?.id ?: error("Login required")
        client.from("event_timeline_items").delete { filter { eq("id", itemId) } }
    }
}
