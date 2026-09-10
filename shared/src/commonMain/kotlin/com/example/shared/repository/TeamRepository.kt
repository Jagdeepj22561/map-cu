package com.example.shared.repository

import com.example.shared.data.SupabaseClientProvider
import com.example.shared.model.FriendSuggestion
import com.example.shared.model.TeamRequirement
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

class TeamRepository {
    private val client = SupabaseClientProvider.client

    @Serializable private data class RequirementRow(val id: String, @SerialName("announcement_id") val announcementId: String, @SerialName("author_id") val authorId: String, val content: String, @SerialName("members_needed") val membersNeeded: Int, @SerialName("created_at") val createdAt: String)
    @Serializable private data class RequirementInsert(@SerialName("announcement_id") val announcementId: String, @SerialName("author_id") val authorId: String, val content: String, @SerialName("members_needed") val membersNeeded: Int)
    @Serializable private data class MemberInsert(@SerialName("team_id") val teamId: String, @SerialName("user_id") val userId: String, val role: String = "member")
    @Serializable private data class FriendRequestInsert(@SerialName("sender_id") val senderId: String, @SerialName("receiver_id") val receiverId: String)
    @Serializable private data class ProfileRow(val id: String, val email: String, val name: String, @SerialName("profile_pic_url") val profilePicUrl: String = "", val course: String = "", val year: String = "", val semester: String = "", val latitude: Double = 0.0, val longitude: Double = 0.0)

    suspend fun joinTeam(teamId: String) {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        client.from("event_team_members").insert(MemberInsert(teamId, uid))
    }

    suspend fun createTeamRequirement(announcementId: String, content: String, membersNeeded: Int) {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        require(content.trim().isNotBlank()) { "Requirement cannot be empty" }
        require(membersNeeded > 0) { "Members needed must be positive" }
        client.from("team_requirements").insert(RequirementInsert(announcementId, uid, content.trim(), membersNeeded))
    }

    suspend fun getTeamRequirements(announcementId: String): List<TeamRequirement> = client.from("team_requirements").select {
        filter { eq("announcement_id", announcementId) }
        order("created_at", Order.DESCENDING)
    }.decodeList<RequirementRow>().map { row -> TeamRequirement(row.id, row.announcementId, row.authorId, row.content, row.membersNeeded, row.createdAt.hashCode().toLong()) }

    suspend fun respondToRequirement(requirementId: String) {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        @Serializable data class ResponseInsert(@SerialName("requirement_id") val requirementId: String, @SerialName("user_id") val userId: String)
        client.from("team_requirement_responses").insert(ResponseInsert(requirementId, uid))
    }

    suspend fun sendFriendRequest(receiverId: String) {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        require(uid != receiverId) { "You cannot add yourself" }
        client.from("friend_requests").insert(FriendRequestInsert(uid, receiverId))
    }

    suspend fun discoverFriends(limit: Int = 30): List<FriendSuggestion> {
        val uid = client.auth.currentUserOrNull()?.id ?: return emptyList()
        val me = client.from("profiles").select { filter { eq("id", uid) } }.decodeSingleOrNull<ProfileRow>() ?: return emptyList()
        val blocked = client.from("blocks").select { filter { eq("user_id", uid) } }.decodeList<BlockRow>().map { it.blockedUserId }.toSet()
        val friends = client.from("friendships").select { filter { eq("user_id", uid) } }.decodeList<FriendshipRow>().map { it.friendId }.toSet()
        val requests = client.from("friend_requests").select { filter { eq("sender_id", uid) } }.decodeList<FriendRequestRow>().map { it.receiverId }.toMutableSet()
        client.from("friend_requests").select { filter { eq("receiver_id", uid) } }.decodeList<FriendRequestRow>().forEach { requests += it.senderId }
        val candidates = client.from("profiles").select { limit(300) }.decodeList<ProfileRow>()
        return candidates.asSequence().filter { it.id != uid && it.id !in blocked && it.id !in friends && it.id !in requests }.map { p ->
            val reasons = buildList {
                if (me.course.isNotBlank() && me.course == p.course) add("Same course")
                if (me.year.isNotBlank() && me.year == p.year) add("Same year")
                if (me.semester.isNotBlank() && me.semester == p.semester) add("Same semester")
                if (me.latitude != 0.0 && me.longitude != 0.0 && p.latitude != 0.0 && p.longitude != 0.0) {
                    val d = sqrt((me.latitude - p.latitude) * (me.latitude - p.latitude) + (me.longitude - p.longitude) * (me.longitude - p.longitude))
                    if (d < 0.01) add("Nearby")
                }
            }
            FriendSuggestion(p.id, p.name, p.email, p.profilePicUrl.ifBlank { null }, p.course, p.year, p.semester, reasons.size, reasons)
        }.sortedByDescending { it.score }.take(limit).toList()
    }

    @Serializable private data class BlockRow(@SerialName("blocked_user_id") val blockedUserId: String)
    @Serializable private data class FriendshipRow(@SerialName("friend_id") val friendId: String)
    @Serializable private data class FriendRequestRow(@SerialName("sender_id") val senderId: String, @SerialName("receiver_id") val receiverId: String)
}
