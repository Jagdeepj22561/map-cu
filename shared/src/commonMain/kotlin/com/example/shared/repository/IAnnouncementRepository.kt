package com.example.shared.repository

import com.example.shared.model.Announcement
import kotlinx.coroutines.flow.Flow

interface IAnnouncementRepository {
    fun getAnnouncementsFlow(): Flow<List<Announcement>>
    suspend fun getAnnouncement(id: String): Announcement?
    fun getAnnouncementFlow(id: String): Flow<Announcement?>
    suspend fun createAnnouncement(announcement: Announcement)
    suspend fun incrementViewCount(announcementId: String)
    suspend fun reportAnnouncement(announcementId: String, reporterUid: String, reason: String)
    suspend fun deleteAnnouncement(announcementId: String)
}

expect class AnnouncementRepository(): IAnnouncementRepository
