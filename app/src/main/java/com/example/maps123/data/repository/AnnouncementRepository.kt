package com.example.maps123.data.repository

import android.content.Context
import android.util.Log
import com.example.maps123.data.local.AnnouncementCacheEntity
import com.example.maps123.data.local.AppDatabase
import com.example.maps123.data.local.toAnnouncement
import com.example.maps123.data.local.toCacheEntity
import com.example.maps123.data.supabase.SupabaseProvider
import com.example.shared.model.Announcement
import com.example.shared.repository.AnnouncementRemoteDataSource
import com.example.shared.repository.IAnnouncementRepository
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Android cache/lifecycle decorator around the shared Supabase data source. */
class AnnouncementRepository(context: Context) : IAnnouncementRepository {
    private val appContext = context.applicationContext
    private val db get() = AppDatabase.getInstance(appContext)
    private val dao get() = db.announcementDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val remote = AnnouncementRemoteDataSource()
    // A refresh must not write an older response after an engagement mutation
    // has updated Room. This protects the cache from out-of-order responses.
    private val feedMutationMutex = Mutex()
    private var syncJob: Job? = null
    private val _isInitialLoadRunning = MutableStateFlow(false)
    val isInitialLoadRunning: StateFlow<Boolean> = _isInitialLoadRunning.asStateFlow()

    override fun getAnnouncementsFlow(): Flow<List<Announcement>> = getAnnouncementsFlow(20)

    fun getAnnouncementsFlow(limit: Int): Flow<List<Announcement>> =
        dao.observeRecent(limit.coerceIn(1, 200))
            .map { it.map(AnnouncementCacheEntity::toAnnouncement) }
            .onStart { startFeedSync() }

    fun startFeedSync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            // First load: only show initial loading spinner if local cache is empty
            val hasLocalCache = dao.getCount() > 0
            refreshFeed(showLoading = !hasLocalCache)

            // Setup Realtime subscription for instant updates on announcements, comments, and likes
            runCatching {
                val channel = SupabaseProvider.client.channel("announcements-feed")

                launch {
                    channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                        table = "announcements"
                    }.collect {
                        Log.d("AnnouncementRepo", "New announcement inserted, refreshing feed")
                        runCatching { refreshFeed(false, force = true) }
                    }
                }

                launch {
                    channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
                        table = "announcements"
                    }.collect { action ->
                        val id = action.oldRecord["id"]?.toString()
                        if (id != null) {
                            dao.deleteById(id)
                        } else {
                            runCatching { refreshFeed(false, force = true) }
                        }
                    }
                }

                launch {
                    channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                        table = "announcement_comments"
                    }.collect {
                        Log.d("AnnouncementRepo", "New comment inserted, refreshing feed")
                        runCatching { refreshFeed(false, force = true) }
                    }
                }

                launch {
                    channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                        table = "announcement_likes"
                    }.collect {
                        Log.d("AnnouncementRepo", "New like inserted, refreshing feed")
                        runCatching { refreshFeed(false, force = true) }
                    }
                }

                channel.subscribe()
                Log.i("AnnouncementRepo", "Realtime subscription active for announcements feed")
            }.onFailure {
                Log.w("AnnouncementRepo", "Failed to start Realtime subscription, falling back to light polling", it)
            }

            // Light polling fallback at 60s intervals instead of aggressive 15s
            while (isActive) {
                delay(60_000)
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

    override suspend fun getAnnouncement(id: String): Announcement? {
        dao.getById(id)?.toAnnouncement()?.let { return it }
        val result = remote.getAnnouncement(id) ?: run {
            dao.deleteById(id)
            return null
        }
        dao.insert(result.toCacheEntity())
        return result
    }

    override fun getAnnouncementFlow(id: String): Flow<Announcement?> =
        dao.observeById(id).map { it?.toAnnouncement() }.onStart { getAnnouncement(id) }

    override suspend fun createAnnouncement(announcement: Announcement) {
        remote.createAnnouncement(announcement)
        dao.insert(announcement.toCacheEntity())
    }

    override suspend fun deleteAnnouncement(announcementId: String) {
        remote.deleteAnnouncement(announcementId)
        dao.deleteById(announcementId)
    }

    override suspend fun incrementViewCount(announcementId: String) =
        remote.incrementViewCount(announcementId)

    override suspend fun reportAnnouncement(announcementId: String, reporterUid: String, reason: String) =
        remote.reportAnnouncement(announcementId, reporterUid, reason)

    /** Saves a known like state without a pre-write read or a blocking feed refresh. */
    suspend fun setLike(announcementId: String, shouldLike: Boolean) {
        val uid = AuthRepository.currentUserId() ?: error("Login required")
        feedMutationMutex.withLock {
            remote.setLike(announcementId, shouldLike)
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
        feedMutationMutex.withLock {
            remote.addComment(announcementId, text)
            updateCachedAnnouncement(announcementId) { announcement ->
                announcement.copy(comments = announcement.comments + (localComment.id to localComment))
            }
        }
    }

    suspend fun deleteComment(announcementId: String, commentId: String) {
        feedMutationMutex.withLock {
            remote.deleteComment(commentId)
            updateCachedAnnouncement(announcementId) { announcement ->
                announcement.copy(comments = announcement.comments - commentId)
            }
        }
    }

    suspend fun refreshNow() = refreshFeed(showLoading = false, force = true)

    private suspend fun updateCachedAnnouncement(
        announcementId: String,
        transform: (Announcement) -> Announcement
    ) {
        dao.getById(announcementId)
            ?.toAnnouncement()
            ?.let(transform)
            ?.let { dao.insert(it.toCacheEntity()) }
    }

    private suspend fun refreshFeed(showLoading: Boolean, force: Boolean = false) {
        if (showLoading) _isInitialLoadRunning.value = true
        try {
            feedMutationMutex.withLock {
                // Quick check: if not forced and we have local cache, check if latest timestamp in Supabase is newer
                val localLatestTimestamp = dao.getLatestTimestamp()
                val localCount = dao.getCount()
                if (!force && localCount > 0 && localLatestTimestamp != null && localLatestTimestamp > 0) {
                    val remoteLatest = runCatching { remote.getLatestTimestamp() }.getOrNull()

                    // If remote latest timestamp is not newer than our latest cached timestamp,
                    // we don't need to do expensive multi-table queries (announcements + likes + comments + profiles)
                    if (remoteLatest != null && remoteLatest <= localLatestTimestamp) {
                        Log.d("AnnouncementRepo", "Cache is fresh (remote: $remoteLatest, local: $localLatestTimestamp), skipping full fetch")
                        return@withLock
                    }
                }

                val announcements = remote.loadAnnouncements(20)
                if (announcements.isNotEmpty()) {
                    dao.insertAll(announcements.map(Announcement::toCacheEntity))
                } else {
                    dao.clearAll()
                }
            }
        } finally { if (showLoading) _isInitialLoadRunning.value = false }
    }
}
