package com.example.shared.repository

import cocoapods.FirebaseFirestore.FIRDocumentSnapshot
import cocoapods.FirebaseFirestore.FIRQuerySnapshot
import cocoapods.FirebaseFirestore.FIRFieldValue
import com.example.shared.IosInAppDebugLogStore
import com.example.shared.model.Announcement
import com.example.shared.model.AnnouncementType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private object IosAnnouncementStore {
    private const val announcementsCacheKey = "ios.cache.announcements"
    private const val announcementPollIntervalMs = 60_000L
    private const val minimumRefreshGapMs = 20_000L
    private const val viewWindowMs = 6 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var syncJob: Job? = null
    private var loadedCacheOwnerUid: String? = IosSessionStore.current()?.uid
    private var lastRefreshAt = 0L
    private var refreshInProgress = false
    private val viewedAnnouncements = mutableMapOf<String, Long>()

    val announcements = MutableStateFlow(loadCachedAnnouncements())

    init {
        start()
    }

    fun start() {
        ensureLocalCacheLoaded()
        if (syncJob != null) return
        syncJob = scope.launch {
            runCatching { refresh(force = true) }
            while (isActive) {
                delay(announcementPollIntervalMs)
                runCatching { refresh() }
            }
        }
    }

    suspend fun refresh(force: Boolean = false) {
        ensureLocalCacheLoaded()
        val now = currentTimeMillis()
        if (refreshInProgress) return
        if (!force && now - lastRefreshAt < minimumRefreshGapMs) return
        refreshInProgress = true
        try {
            val snapshot = suspendCancellableCoroutine<FIRQuerySnapshot?> { continuation ->
                IosFirestoreRefs.announcements.getDocumentsWithCompletion { snapshot, error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(snapshot)
                }
            }
            val items = snapshot?.documents?.mapNotNull { doc ->
                val snapshot = doc as FIRDocumentSnapshot
                runCatching {
                    snapshot.data()?.asStringMap()?.toAnnouncement(snapshot.documentID)
                }.getOrNull()
            }?.sortedByDescending { it.timestamp } ?: emptyList()
            announcements.value = items
            lastRefreshAt = now
            persist()
        } finally {
            refreshInProgress = false
        }
    }

    suspend fun createAnnouncement(announcement: Announcement) {
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.announcement(announcement.id)
                .setData(announcement.toMap() as Map<Any?, *>) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        upsert(announcement)
    }

    suspend fun incrementViewCount(announcementId: String) {
        if (!allowViewIncrement(announcementId)) return
        runCatching {
            suspendCancellableCoroutine<Unit> { continuation ->
                IosFirestoreRefs.announcement(announcementId)
                    .updateData(mapOf("viewCount" to FIRFieldValue.fieldValueForIncrement(1)) as Map<Any?, *>) { error ->
                        if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                        else continuation.resume(Unit)
                    }
            }
        }.onSuccess {
            announcements.value.firstOrNull { it.id == announcementId }?.let { current ->
                upsert(current.copy(viewCount = current.viewCount + 1))
            }
        }.onFailure {
            rollbackViewIncrement(announcementId)
            throw it
        }
    }

    suspend fun reportAnnouncement(announcementId: String, reporterUid: String, reason: String) {
        val announcement = getAnnouncement(announcementId)
        val reportId = "report_${currentTimeMillis()}"
        val payload = mapOf(
            "announcementId" to announcementId,
            "reporterUid" to reporterUid,
            "authorId" to (announcement?.authorUid ?: ""),
            "reason" to reason,
            "timestamp" to currentTimeMillis()
        )
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.reports.documentWithPath(reportId)
                .setData(payload as Map<Any?, *>) { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
    }

    suspend fun deleteAnnouncement(announcementId: String) {
        suspendCancellableCoroutine<Unit> { continuation ->
            IosFirestoreRefs.announcement(announcementId)
                .deleteDocumentWithCompletion { error ->
                    if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                    else continuation.resume(Unit)
                }
        }
        announcements.value = announcements.value.filterNot { it.id == announcementId }
        persist()
    }

    suspend fun getAnnouncement(id: String): Announcement? {
        announcements.value.firstOrNull { it.id == id }?.let { return it }
        val doc = suspendCancellableCoroutine<FIRDocumentSnapshot?> { continuation ->
            IosFirestoreRefs.announcement(id).getDocumentWithCompletion { snapshot, error ->
                if (error != null) continuation.resumeWithException(Throwable(error.toString()))
                else continuation.resume(snapshot)
            }
        }
        val remote = runCatching {
            doc?.data()?.asStringMap()?.toAnnouncement(id)
        }.getOrNull()
        if (remote != null) {
            upsert(remote)
        }
        return remote
    }

    private fun upsert(announcement: Announcement) {
        announcements.value = (
            announcements.value.filterNot { it.id == announcement.id } + announcement
        ).sortedByDescending { it.timestamp }
        persist()
    }

    private fun persist() {
        IosLocalDatabaseStore.saveJson(
            announcementsCacheKey,
            announcements.value.map { it.toMap() }
        )
    }

    private fun loadCachedAnnouncements(): List<Announcement> {
        return IosLocalDatabaseStore.readJson(announcementsCacheKey).asList()
            ?.mapNotNull { raw ->
                runCatching {
                    raw.asStringMap()?.toAnnouncement()
                }.onFailure {
                    IosInAppDebugLogStore.log("Announcement cache parse failed: ${it.message ?: "unknown"}")
                }.getOrNull()
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    private fun ensureLocalCacheLoaded() {
        val currentOwner = IosSessionStore.current()?.uid
        if (loadedCacheOwnerUid == currentOwner) return
        loadedCacheOwnerUid = currentOwner
        lastRefreshAt = 0L
        refreshInProgress = false
        viewedAnnouncements.clear()
        announcements.value = loadCachedAnnouncements()
    }

    private fun allowViewIncrement(announcementId: String): Boolean {
        val key = viewKey(announcementId)
        val now = currentTimeMillis()
        val lastSeen = viewedAnnouncements[key] ?: 0L
        if (now - lastSeen < viewWindowMs) return false
        viewedAnnouncements[key] = now
        return true
    }

    private fun rollbackViewIncrement(announcementId: String) {
        viewedAnnouncements.remove(viewKey(announcementId))
    }

    private fun viewKey(announcementId: String): String {
        val uid = IosSessionStore.current()?.uid ?: "guest"
        return "$uid:$announcementId"
    }
}

actual class AnnouncementRepository : IAnnouncementRepository {
    override fun getAnnouncementsFlow(): Flow<List<Announcement>> = IosAnnouncementStore.announcements

    override suspend fun getAnnouncement(id: String): Announcement? = IosAnnouncementStore.getAnnouncement(id)

    suspend fun refreshNow() {
        IosAnnouncementStore.refresh()
    }

    override fun getAnnouncementFlow(id: String): Flow<Announcement?> {
        return IosAnnouncementStore.announcements.map { list -> list.firstOrNull { it.id == id } }
    }

    override suspend fun createAnnouncement(announcement: Announcement) {
        IosAnnouncementStore.createAnnouncement(announcement)
    }

    override suspend fun incrementViewCount(announcementId: String) {
        IosAnnouncementStore.incrementViewCount(announcementId)
    }

    override suspend fun reportAnnouncement(announcementId: String, reporterUid: String, reason: String) {
        IosAnnouncementStore.reportAnnouncement(announcementId, reporterUid, reason)
    }

    override suspend fun deleteAnnouncement(announcementId: String) {
        IosAnnouncementStore.deleteAnnouncement(announcementId)
    }
}

private fun Announcement.toMap(): Map<String, Any?> = buildMap {
    put("id", id)
    put("title", title)
    put("content", content)
    put("timestamp", timestamp)
    put("author", author)
    put("authorUid", authorUid)
    put("type", type.name)
    put("shareCount", shareCount)
    put("viewCount", viewCount)
    put("itemName", itemName)
    put("place", place)
    put("time", time)
    put("reward", reward)
    put("eventVenue", eventVenue)
    put("eventTime", eventTime)
    put("eventPurpose", eventPurpose)
    put("eventDlType", eventDlType)
    put("eventMode", eventMode)
    put("eventMaxMembers", eventMaxMembers)
    put("eventDepartments", eventDepartments)
    put("eventLink", eventLink)
    if (!imageUrl.isNullOrBlank()) put("imageUrl", imageUrl)
    if (!authorProfilePicUrl.isNullOrBlank()) put("authorProfilePicUrl", authorProfilePicUrl)
}

private fun Map<String, Any?>.toAnnouncement(): Announcement {
    val typeValue = this["type"].stringValue()
    val type = runCatching { AnnouncementType.valueOf(typeValue) }.getOrElse {
        when (typeValue.uppercase()) {
            "LOST & FOUND", "LOST_AND_FOUND" -> AnnouncementType.LOST_AND_FOUND
            "EVENTS", "EVENT" -> AnnouncementType.EVENT
            else -> AnnouncementType.NEWS
        }
    }
    return Announcement(
        id = this["id"].stringValue(),
        title = this["title"].stringValue(),
        content = this["content"].stringValue(),
        imageUrl = this["imageUrl"].stringValue().takeIf { it.isNotBlank() },
        timestamp = this["timestamp"].longValue(),
        author = this["author"].stringValue(),
        authorUid = this["authorUid"].stringValue().takeIf { it.isNotBlank() },
        authorProfilePicUrl = this["authorProfilePicUrl"].stringValue().takeIf { it.isNotBlank() },
        type = type,
        likes = emptyMap(),
        comments = emptyMap(),
        shareCount = this["shareCount"].longValue().toInt(),
        viewCount = this["viewCount"].longValue().toInt(),
        itemName = this["itemName"].stringValue().takeIf { it.isNotBlank() },
        place = this["place"].stringValue().takeIf { it.isNotBlank() },
        time = this["time"].stringValue().takeIf { it.isNotBlank() },
        reward = this["reward"].stringValue().takeIf { it.isNotBlank() },
        eventVenue = this["eventVenue"].stringValue().takeIf { it.isNotBlank() },
        eventTime = this["eventTime"].stringValue().takeIf { it.isNotBlank() },
        eventPurpose = this["eventPurpose"].stringValue().takeIf { it.isNotBlank() },
        eventDlType = this["eventDlType"].stringValue().takeIf { it.isNotBlank() },
        eventMode = this["eventMode"].stringValue().takeIf { it.isNotBlank() },
        eventMaxMembers = this["eventMaxMembers"].longValue().toInt().takeIf { it > 0 },
        eventDepartments = this["eventDepartments"].stringValue().takeIf { it.isNotBlank() },
        eventLink = this["eventLink"].stringValue().takeIf { it.isNotBlank() }
    )
}
