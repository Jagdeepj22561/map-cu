package com.example.maps123.data.repository

import android.content.Context
import android.util.Log
import com.example.maps123.data.local.AnnouncementCacheEntity
import com.example.maps123.data.local.AppDatabase
import com.example.maps123.data.local.toAnnouncement
import com.example.maps123.data.local.toCacheEntity
import com.example.maps123.data.supabase.SupabaseProvider
import com.example.shared.model.Announcement
import com.example.shared.model.AnnouncementType
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Supabase-backed announcement feed. Room remains the offline cache. */
class AnnouncementRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db get() = AppDatabase.getInstance(appContext)
    private val dao get() = db.announcementDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // A refresh must not write an older response after an engagement mutation
    // has updated Room. This protects the cache from out-of-order responses.
    private val feedMutationMutex = Mutex()
    private var syncJob: Job? = null
    private val _isInitialLoadRunning = MutableStateFlow(false)
    val isInitialLoadRunning: StateFlow<Boolean> = _isInitialLoadRunning.asStateFlow()

    fun getAnnouncementsFlow(limit: Int = 20): Flow<List<Announcement>> =
        dao.observeRecent(limit.coerceIn(1, 200))
            .map { it.map(AnnouncementCacheEntity::toAnnouncement) }
            .onStart { startFeedSync() }

    fun startFeedSync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            refreshFeed(true)
            // Polling replaces the former Firestore listener until the chat
            // migration enables a shared Supabase Realtime subscription.
            while (true) {
                delay(15_000)
                runCatching { refreshFeed(false) }
                    .onFailure { Log.w("AnnouncementRepo", "Feed refresh failed", it) }
            }
        }
    }

    fun stopSync() { syncJob?.cancel(); syncJob = null }

    suspend fun getCachedAnnouncements(limit: Int = 20) =
        dao.getRecent(limit.coerceIn(1, 200)).map(AnnouncementCacheEntity::toAnnouncement)
    suspend fun getCachedAnnouncement(id: String) = dao.getById(id)?.toAnnouncement()
    suspend fun getNewAnnouncementCount(since: Long) = dao.getRecent(20).count { it.timestamp > since }

    suspend fun getAnnouncement(id: String): Announcement? {
        dao.getById(id)?.toAnnouncement()?.let { return it }
        val client = SupabaseProvider.client
        val row = client.from("announcements").select {
            filter { eq("id", id) }
        }.decodeList<AnnouncementRow>().firstOrNull()?.toAnnouncement() ?: run {
            dao.deleteById(id)
            return null
        }

        val likes = client.from("announcement_likes").select {
            Columns.raw("user_id")
            filter { eq("announcement_id", id) }
        }.decodeList<AnnouncementLikeRow>()

        // Include the author's profile with each comment. The previous query
        // fetched only author_id, so NestedComment.profile was always null and
        // the UI had no real account name to display.
        val comments = client.from("announcement_comments").select {
            Columns.raw("id, author_id, body, created_at, profiles(name, profile_pic_url)")
            filter { eq("announcement_id", id) }
        }.decodeList<NestedComment>()

        val result = row.copy(
            likes = likes.associate { it.userId to true },
            comments = comments.associate { comment ->
                comment.id to com.example.shared.model.Comment(
                    id = comment.id,
                    userId = comment.authorId,
                    userName = comment.profile?.name.orEmpty(),
                    userProfilePic = comment.profile?.profilePicUrl,
                    text = comment.body,
                    timestamp = comment.timestamp()
                )
            }
        )
        dao.insert(result.toCacheEntity())
        return result
    }

    suspend fun createAnnouncement(announcement: Announcement) {
        val userId = AuthRepository.currentUserId() ?: error("Not logged in")
        require(announcement.authorUid == userId) { "You can only create your own announcement" }
        SupabaseProvider.client.from("announcements").insert(announcement.toRow())
        dao.insert(announcement.toCacheEntity())
    }

    suspend fun deleteAnnouncement(announcementId: String) {
        SupabaseProvider.client.from("announcements").delete { filter { eq("id", announcementId) } }
        dao.deleteById(announcementId)
    }

    suspend fun reportAnnouncement(announcementId: String, reporterUid: String, reason: String) {
        require(reporterUid == AuthRepository.currentUserId()) { "Invalid reporter session" }
        SupabaseProvider.client.from("reports").insert(
            ReportRow(announcementId, reporterUid, getAnnouncement(announcementId)?.authorUid, reason.trim())
        )
    }

    /** Saves a known like state without a pre-write read or a blocking feed refresh. */
    suspend fun setLike(announcementId: String, shouldLike: Boolean) {
        val uid = AuthRepository.currentUserId() ?: error("Login required")
        feedMutationMutex.withLock {
            if (shouldLike) {
                SupabaseProvider.client.from("announcement_likes").insert(AnnouncementLikeRow(announcementId, uid))
            } else {
                SupabaseProvider.client.from("announcement_likes").delete {
                    filter { eq("announcement_id", announcementId); eq("user_id", uid) }
                }
            }
            updateCachedAnnouncement(announcementId) { announcement ->
                announcement.copy(
                    likes = announcement.likes.toMutableMap().apply {
                        if (shouldLike) put(uid, true) else remove(uid)
                    }
                )
            }
        }
    }

    suspend fun addComment(announcementId: String, text: String, localComment: com.example.shared.model.Comment) {
        val uid = AuthRepository.currentUserId() ?: error("Login required")
        require(text.isNotBlank()) { "Comment cannot be empty" }
        feedMutationMutex.withLock {
            SupabaseProvider.client.from("announcement_comments").insert(
                AnnouncementCommentInsert(announcementId, uid, text.trim())
            )
            updateCachedAnnouncement(announcementId) { announcement ->
                announcement.copy(comments = announcement.comments + (localComment.id to localComment))
            }
        }
    }

    suspend fun refreshNow() = refreshFeed(showLoading = false)

    private suspend fun updateCachedAnnouncement(
        announcementId: String,
        transform: (Announcement) -> Announcement
    ) {
        dao.getById(announcementId)
            ?.toAnnouncement()
            ?.let(transform)
            ?.let { dao.insert(it.toCacheEntity()) }
    }

    private suspend fun refreshFeed(showLoading: Boolean) {
        if (showLoading) _isInitialLoadRunning.value = true
        try {
            feedMutationMutex.withLock {
                val client = SupabaseProvider.client
                val remote = client.from("announcements").select {
                    order("timestamp", Order.DESCENDING)
                    limit(20)
                }.decodeList<AnnouncementRow>().map(AnnouncementRow::toAnnouncement)

                val remoteIds = remote.map { it.id }
                if (remoteIds.isNotEmpty()) {
                    val allLikes = client.from("announcement_likes").select {
                        Columns.raw("announcement_id, user_id")
                    }.decodeList<AnnouncementLikeRow>()

                    // Fetch comment authors together with each comment. The
                    // profiles relation is defined by announcement_comments.author_id.
                    val allComments = client.from("announcement_comments").select {
                        Columns.raw("id, announcement_id, author_id, body, created_at, profiles(name, profile_pic_url)")
                    }.decodeList<NestedComment>()

                    val likesByPost = allLikes.filter { it.announcementId in remoteIds }
                        .groupBy({ it.announcementId }) { it.userId }

                    val commentsByPost = allComments.filter { it.announcementId in remoteIds }
                        .groupBy({ it.announcementId }) { comment ->
                            com.example.shared.model.Comment(
                                id = comment.id,
                                userId = comment.authorId,
                                userName = comment.profile?.name.orEmpty(),
                                userProfilePic = comment.profile?.profilePicUrl,
                                text = comment.body,
                                timestamp = comment.timestamp()
                            )
                        }

                    val merged = remote.map { announcement ->
                        announcement.copy(
                            likes = likesByPost[announcement.id]?.associate { it to true } ?: announcement.likes,
                            comments = commentsByPost[announcement.id]?.associateBy { it.id } ?: announcement.comments
                        )
                    }
                    dao.insertAll(merged.map(Announcement::toCacheEntity))
                } else {
                    dao.clearAll()
                }
            }
        } finally { if (showLoading) _isInitialLoadRunning.value = false }
    }
}

@Serializable
private data class AnnouncementRow(
    val id: String,
    @SerialName("author_id") val authorId: String,
    val title: String, val content: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val timestamp: Long, val author: String,
    @SerialName("author_profile_pic_url") val authorProfilePicUrl: String? = null,
    val type: String,
    @SerialName("share_count") val shareCount: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("item_name") val itemName: String? = null,
    val place: String? = null, val time: String? = null, val reward: String? = null,
    @SerialName("event_venue") val eventVenue: String? = null,
    @SerialName("event_time") val eventTime: String? = null,
    @SerialName("event_purpose") val eventPurpose: String? = null,
    @SerialName("event_dl_type") val eventDlType: String? = null,
    @SerialName("event_mode") val eventMode: String? = null,
    @SerialName("event_max_members") val eventMaxMembers: Int? = null,
    @SerialName("event_departments") val eventDepartments: String? = null,
    @SerialName("event_link") val eventLink: String? = null,
    @SerialName("announcement_likes") val announcementLikes: List<NestedLike> = emptyList(),
    @SerialName("announcement_comments") val announcementComments: List<NestedComment> = emptyList()
)

private fun Announcement.toRow() = AnnouncementRow(
    id, requireNotNull(authorUid), title, content, imageUrl, timestamp, author, authorProfilePicUrl,
    type.name, shareCount, viewCount, itemName, place, time, reward, eventVenue, eventTime,
    eventPurpose, eventDlType, eventMode, eventMaxMembers, eventDepartments, eventLink
)

private fun AnnouncementRow.toAnnouncement() = Announcement(
    id = id, title = title, content = content, imageUrl = imageUrl, timestamp = timestamp,
    author = author, authorUid = authorId, authorProfilePicUrl = authorProfilePicUrl,
    type = runCatching { AnnouncementType.valueOf(type) }.getOrDefault(AnnouncementType.NEWS),
    shareCount = shareCount, viewCount = viewCount, itemName = itemName, place = place, time = time,
    reward = reward, eventVenue = eventVenue, eventTime = eventTime, eventPurpose = eventPurpose,
    eventDlType = eventDlType, eventMode = eventMode, eventMaxMembers = eventMaxMembers,
    eventDepartments = eventDepartments, eventLink = eventLink,
    likes = announcementLikes.associate { it.userId to true },
    comments = announcementComments.associate { comment ->
        comment.id to com.example.shared.model.Comment(
            id = comment.id,
            userId = comment.authorId,
            userName = comment.profile?.name.orEmpty(),
            userProfilePic = comment.profile?.profilePicUrl,
            text = comment.body,
            timestamp = comment.timestamp()
        )
    }
)

@Serializable
private data class ReportRow(
    @SerialName("announcement_id") val announcementId: String,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("author_id") val authorId: String?,
    val reason: String
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

@Serializable private data class NestedLike(@SerialName("user_id") val userId: String)
@Serializable
private data class CommentProfile(
    val name: String = "",
    @SerialName("profile_pic_url") val profilePicUrl: String = ""
)

@Serializable
private data class NestedComment(
    val id: String,
    @SerialName("announcement_id") val announcementId: String = "",
    @SerialName("author_id") val authorId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("profiles") val profile: CommentProfile? = null
) {
    fun timestamp() = runCatching { java.time.OffsetDateTime.parse(createdAt).toInstant().toEpochMilli() }.getOrDefault(0L)
}
