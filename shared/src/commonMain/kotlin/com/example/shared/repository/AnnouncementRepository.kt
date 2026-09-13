package com.example.shared.repository

import com.example.shared.data.SupabaseClientProvider
import com.example.shared.model.Announcement
import com.example.shared.model.AnnouncementType
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val FEED_REFRESH_MS = 15_000L

@Serializable
private data class AnnouncementRow(
    val id: String,
    @SerialName("author_id") val authorUid: String,
    val title: String,
    val content: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val type: String,
    @SerialName("item_name") val itemName: String? = null,
    val place: String? = null,
    val time: String? = null,
    val reward: String? = null,
    @SerialName("event_venue") val eventVenue: String? = null,
    @SerialName("event_time") val eventTime: String? = null,
    @SerialName("event_purpose") val eventPurpose: String? = null,
    @SerialName("event_dl_type") val eventDlType: String? = null,
    @SerialName("event_mode") val eventMode: String? = null,
    @SerialName("event_max_members") val eventMaxMembers: Int? = null,
    @SerialName("event_departments") val eventDepartments: String? = null,
    @SerialName("event_link") val eventLink: String? = null,
    @SerialName("share_count") val shareCount: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
    val timestamp: Long = 0L,
    val author: String = "",
    @SerialName("author_profile_pic_url") val authorProfilePicUrl: String? = null
)

@Serializable
private data class AnnouncementReportRow(
    @SerialName("announcement_id") val announcementId: String,
    @SerialName("reporter_id") val reporterUid: String,
    @SerialName("author_id") val authorUid: String?,
    val reason: String
)

@Serializable
private data class IncrementViewParams(
    @SerialName("p_announcement_id") val announcementId: String
)

class AnnouncementRepository : IAnnouncementRepository {
    private val client = SupabaseClientProvider.client

    override fun getAnnouncementsFlow(): Flow<List<Announcement>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(loadAnnouncements())
            delay(FEED_REFRESH_MS)
        }
    }

    override suspend fun getAnnouncement(id: String): Announcement? {
        return client.from("announcements")
            .select {
                filter { eq("id", id) }
            }
            .decodeSingleOrNull<AnnouncementRow>()
            ?.toModel()
    }

    override fun getAnnouncementFlow(id: String): Flow<Announcement?> = flow {
        while (currentCoroutineContext().isActive) {
            emit(getAnnouncement(id))
            delay(FEED_REFRESH_MS)
        }
    }

    override suspend fun createAnnouncement(announcement: Announcement) {
        val authorUid = announcement.authorUid?.trim()
            ?: error("Authenticated author id is required")

        client.from("announcements").insert(
            AnnouncementRow(
                id = announcement.id,
                authorUid = authorUid,
                title = announcement.title,
                content = announcement.content,
                imageUrl = announcement.imageUrl,
                type = announcement.type.name,
                itemName = announcement.itemName,
                place = announcement.place,
                time = announcement.time,
                reward = announcement.reward,
                eventVenue = announcement.eventVenue,
                eventTime = announcement.eventTime,
                eventPurpose = announcement.eventPurpose,
                eventDlType = announcement.eventDlType,
                eventMode = announcement.eventMode,
                eventMaxMembers = announcement.eventMaxMembers,
                eventDepartments = announcement.eventDepartments,
                eventLink = announcement.eventLink,
                shareCount = announcement.shareCount,
                viewCount = announcement.viewCount,
                timestamp = announcement.timestamp,
                author = announcement.author,
                authorProfilePicUrl = announcement.authorProfilePicUrl
            )
        )
    }

    override suspend fun incrementViewCount(announcementId: String) {
        client.postgrest.rpc(
            "increment_announcement_view",
            IncrementViewParams(announcementId)
        )
    }

    override suspend fun reportAnnouncement(
        announcementId: String,
        reporterUid: String,
        reason: String
    ) {
        val authorUid = getAnnouncement(announcementId)?.authorUid
        client.from("reports").insert(
            AnnouncementReportRow(
                announcementId = announcementId,
                reporterUid = reporterUid,
                authorUid = authorUid,
                reason = reason.trim()
            )
        )
    }

    override suspend fun deleteAnnouncement(announcementId: String) {
        client.from("announcements").delete {
            filter { eq("id", announcementId) }
        }
    }

    private suspend fun loadAnnouncements(): List<Announcement> =
        client.from("announcements")
            .select {
                order("timestamp", Order.DESCENDING)
                limit(50)
            }
            .decodeList<AnnouncementRow>()
            .map { it.toModel() }

    private fun AnnouncementRow.toModel(): Announcement {
        val announcementType = runCatching { AnnouncementType.valueOf(type) }
            .getOrDefault(AnnouncementType.NEWS)

        return Announcement(
            id = id,
            title = title,
            content = content,
            imageUrl = imageUrl,
            timestamp = timestamp,
            author = author,
            authorUid = authorUid,
            authorProfilePicUrl = authorProfilePicUrl,
            type = announcementType,
            likes = emptyMap(),
            comments = emptyMap(),
            shareCount = shareCount,
            viewCount = viewCount,
            itemName = itemName,
            place = place,
            time = time,
            reward = reward,
            eventVenue = eventVenue,
            eventTime = eventTime,
            eventPurpose = eventPurpose,
            eventDlType = eventDlType,
            eventMode = eventMode,
            eventMaxMembers = eventMaxMembers,
            eventDepartments = eventDepartments,
            eventLink = eventLink
        )
    }
}
