package com.example.shared.repository

import com.example.shared.data.EventNotificationClient
import com.example.shared.data.SupabaseClientProvider
import com.example.shared.model.Announcement
import com.example.shared.model.AnnouncementType
import com.example.shared.model.Comment
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

private const val FEED_REFRESH_MS = 60_000L

/**
 * The single cross-platform owner of announcement network operations and DTO
 * mapping. Platform repositories may decorate this with a local cache and
 * lifecycle scheduling, but must not duplicate these queries.
 */
class AnnouncementRemoteDataSource(
    private val client: io.github.jan.supabase.SupabaseClient = SupabaseClientProvider.client
) {
    suspend fun loadAnnouncements(limit: Int = 20): List<Announcement> {
        val announcements = client.from("announcements").select {
            order("timestamp", Order.DESCENDING)
            limit(limit.coerceIn(1, 200).toLong())
        }.decodeList<AnnouncementRow>().map(AnnouncementRow::toModel)

        val ids = announcements.map { it.id }
        if (ids.isEmpty()) return emptyList()

        val likes = client.from("announcement_likes").select {
            Columns.raw("announcement_id, user_id")
            filter { isIn("announcement_id", ids) }
        }.decodeList<AnnouncementLikeRow>()
        val comments = client.from("announcement_comments").select {
            Columns.raw("id, announcement_id, author_id, body, created_at, profiles(name, profile_pic_url)")
            filter { isIn("announcement_id", ids) }
        }.decodeList<AnnouncementCommentRow>()

        val likesByAnnouncement = likes.groupBy({ it.announcementId }) { it.userId }
        val commentsByAnnouncement = comments.groupBy({ it.announcementId }) { it.toModel() }
        return announcements.map { announcement ->
            announcement.copy(
                likes = likesByAnnouncement[announcement.id]?.associateWith { true }.orEmpty(),
                comments = commentsByAnnouncement[announcement.id]?.associateBy(Comment::id).orEmpty()
            )
        }
    }

    suspend fun getLatestTimestamp(): Long? =
        client.from("announcements").select {
            Columns.raw("timestamp")
            order("timestamp", Order.DESCENDING)
            limit(1)
        }.decodeList<TimestampOnlyRow>().firstOrNull()?.timestamp

    suspend fun getAnnouncement(id: String): Announcement? {
        val announcement = client.from("announcements").select {
            filter { eq("id", id) }
            limit(1)
        }.decodeList<AnnouncementRow>().firstOrNull()?.toModel() ?: return null

        val likes = client.from("announcement_likes").select {
            Columns.raw("announcement_id, user_id")
            filter { eq("announcement_id", id) }
        }.decodeList<AnnouncementLikeRow>()
        val comments = client.from("announcement_comments").select {
            Columns.raw("id, announcement_id, author_id, body, created_at, profiles(name, profile_pic_url)")
            filter { eq("announcement_id", id) }
        }.decodeList<AnnouncementCommentRow>()
        return announcement.copy(
            likes = likes.associate { it.userId to true },
            comments = comments.associate { it.id to it.toModel() }
        )
    }

    suspend fun createAnnouncement(announcement: Announcement) {
        require(announcement.authorUid == currentUid()) { "You can only create your own announcement" }
        try {
            client.from("announcements").insert(announcement.toInsertRow())
        } catch (error: Exception) {
            if (error.message?.contains("event_category", ignoreCase = true) != true) throw error
            client.from("announcements").insert(announcement.toCompatInsertRow())
        }
        EventNotificationClient.dispatch("post_created", announcement.id)
    }

    suspend fun deleteAnnouncement(announcementId: String) {
        client.from("announcements").delete { filter { eq("id", announcementId) } }
    }

    suspend fun incrementViewCount(announcementId: String) {
        client.postgrest.rpc("increment_announcement_view", IncrementViewParams(announcementId))
    }

    suspend fun reportAnnouncement(announcementId: String, reporterUid: String, reason: String) {
        require(reporterUid == currentUid()) { "Invalid reporter session" }
        client.from("reports").insert(
            AnnouncementReportRow(
                announcementId = announcementId,
                reporterUid = reporterUid,
                authorUid = getAnnouncement(announcementId)?.authorUid,
                reason = reason.trim()
            )
        )
    }

    suspend fun setLike(announcementId: String, shouldLike: Boolean) {
        val uid = currentUid()
        if (shouldLike) {
            client.from("announcement_likes").insert(AnnouncementLikeRow(announcementId, uid))
        } else {
            client.from("announcement_likes").delete {
                filter {
                    eq("announcement_id", announcementId)
                    eq("user_id", uid)
                }
            }
        }
    }

    suspend fun addComment(announcementId: String, text: String) {
        require(text.isNotBlank()) { "Comment cannot be empty" }
        client.from("announcement_comments").insert(
            AnnouncementCommentInsert(announcementId, currentUid(), text.trim())
        )
    }

    suspend fun deleteComment(commentId: String) {
        client.from("announcement_comments").delete {
            filter {
                eq("id", commentId)
                eq("author_id", currentUid())
            }
        }
    }

    private suspend fun currentUid(): String {
        client.auth.awaitInitialization()
        return client.auth.currentUserOrNull()?.id ?: error("Login required")
    }
}

/** Cache-free repository used by iOS until an Apple local cache is injected. */
class AnnouncementRepository(
    private val remote: AnnouncementRemoteDataSource = AnnouncementRemoteDataSource()
) : IAnnouncementRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val announcements = MutableStateFlow<List<Announcement>>(emptyList())
    private val refreshMutex = Mutex()
    private var syncJob: Job? = null

    override fun getAnnouncementsFlow(): Flow<List<Announcement>> =
        announcements.onStart { startSync() }

    fun startSync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            while (isActive) {
                runCatching { refreshNow() }
                delay(FEED_REFRESH_MS)
            }
        }
    }

    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    suspend fun refreshNow() {
        refreshMutex.withLock {
            announcements.value = remote.loadAnnouncements()
        }
    }

    override suspend fun getAnnouncement(id: String): Announcement? {
        announcements.value.firstOrNull { it.id == id }?.let { return it }
        return remote.getAnnouncement(id)?.also { refreshed ->
            announcements.value = announcements.value
                .filterNot { it.id == refreshed.id }
                .plus(refreshed)
                .sortedByDescending(Announcement::timestamp)
        }
    }

    override fun getAnnouncementFlow(id: String): Flow<Announcement?> =
        announcements.map { feed -> feed.firstOrNull { it.id == id } }
            .onStart {
                startSync()
                getAnnouncement(id)
            }

    override suspend fun createAnnouncement(announcement: Announcement) {
        remote.createAnnouncement(announcement)
        announcements.value = listOf(announcement) + announcements.value.filterNot { it.id == announcement.id }
    }
    override suspend fun incrementViewCount(announcementId: String) = remote.incrementViewCount(announcementId)
    override suspend fun reportAnnouncement(announcementId: String, reporterUid: String, reason: String) =
        remote.reportAnnouncement(announcementId, reporterUid, reason)
    override suspend fun deleteAnnouncement(announcementId: String) {
        remote.deleteAnnouncement(announcementId)
        announcements.value = announcements.value.filterNot { it.id == announcementId }
    }

    suspend fun setLike(announcementId: String, shouldLike: Boolean) {
        remote.setLike(announcementId, shouldLike)
        refreshNow()
    }

    suspend fun addComment(announcementId: String, text: String) {
        remote.addComment(announcementId, text)
        refreshNow()
    }

    suspend fun deleteComment(commentId: String) {
        remote.deleteComment(commentId)
        refreshNow()
    }
}

@Serializable
private data class AnnouncementRow(
    val id: String,
    @SerialName("author_id") val authorUid: String,
    val title: String,
    val content: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val timestamp: Long = 0L,
    val author: String = "",
    @SerialName("author_profile_pic_url") val authorProfilePicUrl: String? = null,
    val type: String,
    @SerialName("share_count") val shareCount: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
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
    @SerialName("event_category") val eventCategory: String? = null
) {
    fun toModel() = Announcement(
        id = id, title = title, content = content, imageUrl = imageUrl, timestamp = timestamp,
        author = author, authorUid = authorUid, authorProfilePicUrl = authorProfilePicUrl,
        type = runCatching { AnnouncementType.valueOf(type) }.getOrDefault(AnnouncementType.NEWS),
        shareCount = shareCount, viewCount = viewCount, itemName = itemName, place = place,
        time = time, reward = reward, eventVenue = eventVenue, eventTime = eventTime,
        eventPurpose = eventPurpose, eventDlType = eventDlType, eventMode = eventMode,
        eventMaxMembers = eventMaxMembers, eventDepartments = eventDepartments,
        eventLink = eventLink, eventCategory = eventCategory
    )
}

@Serializable
private data class AnnouncementInsertRow(
    val id: String,
    @SerialName("author_id") val authorUid: String,
    val title: String,
    val content: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val timestamp: Long,
    val author: String,
    @SerialName("author_profile_pic_url") val authorProfilePicUrl: String? = null,
    val type: String,
    @SerialName("share_count") val shareCount: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
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
    @SerialName("event_category") val eventCategory: String? = null
)

@Serializable
private data class AnnouncementCompatInsertRow(
    val id: String,
    @SerialName("author_id") val authorUid: String,
    val title: String,
    val content: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val timestamp: Long,
    val author: String,
    @SerialName("author_profile_pic_url") val authorProfilePicUrl: String? = null,
    val type: String,
    @SerialName("share_count") val shareCount: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
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
    @SerialName("event_link") val eventLink: String? = null
)

private fun Announcement.toInsertRow() = AnnouncementInsertRow(
    id, requireNotNull(authorUid), title, content, imageUrl, timestamp, author,
    authorProfilePicUrl, type.name, shareCount, viewCount, itemName, place, time,
    reward, eventVenue, eventTime, eventPurpose, eventDlType, eventMode,
    eventMaxMembers, eventDepartments, eventLink, eventCategory
)

private fun Announcement.toCompatInsertRow() = AnnouncementCompatInsertRow(
    id, requireNotNull(authorUid), title, content, imageUrl, timestamp, author,
    authorProfilePicUrl, type.name, shareCount, viewCount, itemName, place, time,
    reward, eventVenue, eventTime, eventPurpose, eventDlType, eventMode,
    eventMaxMembers, eventDepartments, eventLink
)

@Serializable
private data class AnnouncementLikeRow(
    @SerialName("announcement_id") val announcementId: String,
    @SerialName("user_id") val userId: String
)

@Serializable
private data class AnnouncementCommentInsert(
    @SerialName("announcement_id") val announcementId: String,
    @SerialName("author_id") val authorId: String,
    val body: String
)

@Serializable
private data class CommentProfile(
    val name: String = "",
    @SerialName("profile_pic_url") val profilePicUrl: String = ""
)

@Serializable
private data class AnnouncementCommentRow(
    val id: String,
    @SerialName("announcement_id") val announcementId: String,
    @SerialName("author_id") val authorId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("profiles") val profile: CommentProfile? = null
) {
    fun toModel() = Comment(
        id = id,
        userId = authorId,
        userName = profile?.name.orEmpty(),
        userProfilePic = profile?.profilePicUrl?.takeIf(String::isNotBlank),
        text = body,
        timestamp = runCatching { Instant.parse(createdAt).toEpochMilliseconds() }.getOrDefault(0L)
    )
}

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

@Serializable
private data class TimestampOnlyRow(val timestamp: Long)
