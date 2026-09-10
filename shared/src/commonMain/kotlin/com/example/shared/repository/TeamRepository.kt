package com.example.shared.repository

import com.example.shared.data.SupabaseClientProvider
import com.example.shared.model.FriendSuggestion
import com.example.shared.model.TeamRequirement
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class TeamRepository {
    private val client = SupabaseClientProvider.client
    @Serializable private data class RequirementRow(val id: String, @SerialName("announcement_id") val announcementId: String, @SerialName("author_id") val authorId: String, val content: String, @SerialName("members_needed") val membersNeeded: Int, @SerialName("created_at") val createdAt: String)
    @Serializable private data class RequirementInsert(@SerialName("announcement_id") val announcementId: String, @SerialName("author_id") val authorId: String, val content: String, @SerialName("members_needed") val membersNeeded: Int)
    @Serializable private data class TeamInsert(@SerialName("announcement_id") val announcementId: String, @SerialName("owner_id") val ownerId: String, val name: String, @SerialName("max_members") val maxMembers: Int?)
    @Serializable private data class TeamRow(val id: String)
    @Serializable private data class MemberInsert(@SerialName("team_id") val teamId: String, @SerialName("user_id") val userId: String, val role: String = "member")
    @Serializable private data class FriendRequestInsert(@SerialName("sender_id") val senderId: String, @SerialName("receiver_id") val receiverId: String)
    @Serializable private data class DiscoveryParams(@SerialName("p_limit") val limit: Int)
    @Serializable private data class DiscoveryRow(@SerialName("user_id") val userId: String, val name: String, val email: String, @SerialName("profile_pic_url") val profilePicUrl: String = "", val course: String = "", val year: String = "", val semester: String = "", val score: Int = 0, val reasons: List<String> = emptyList())

    suspend fun createTeam(announcementId: String, name: String, maxMembers: Int?): String {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        client.from("event_teams").insert(TeamInsert(announcementId, uid, name.trim(), maxMembers))
        return client.from("event_teams").select { filter { eq("announcement_id", announcementId); eq("owner_id", uid) } }.decodeSingle<TeamRow>().id
    }
    suspend fun joinTeam(teamId: String) { val uid = client.auth.currentUserOrNull()?.id ?: error("Login required"); client.from("event_team_members").insert(MemberInsert(teamId, uid)) }
    suspend fun createTeamRequirement(announcementId: String, content: String, membersNeeded: Int) { val uid = client.auth.currentUserOrNull()?.id ?: error("Login required"); require(content.trim().isNotBlank()); require(membersNeeded > 0); client.from("team_requirements").insert(RequirementInsert(announcementId, uid, content.trim(), membersNeeded)) }
    suspend fun getTeamRequirements(announcementId: String): List<TeamRequirement> = client.from("team_requirements").select { filter { eq("announcement_id", announcementId) }; order("created_at", Order.DESCENDING) }.decodeList<RequirementRow>().map { TeamRequirement(it.id, it.announcementId, it.authorId, it.content, it.membersNeeded, it.createdAt.hashCode().toLong()) }
    suspend fun respondToRequirement(requirementId: String) { val uid = client.auth.currentUserOrNull()?.id ?: error("Login required"); @Serializable data class R(@SerialName("requirement_id") val requirementId: String, @SerialName("user_id") val userId: String); client.from("team_requirement_responses").insert(R(requirementId, uid)) }
    suspend fun sendFriendRequest(receiverId: String) { val uid = client.auth.currentUserOrNull()?.id ?: error("Login required"); require(uid != receiverId); client.from("friend_requests").insert(FriendRequestInsert(uid, receiverId)) }
    suspend fun discoverFriends(limit: Int = 30): List<FriendSuggestion> = client.postgrest.rpc("discover_people", DiscoveryParams(limit.coerceIn(1, 100))).decodeList<DiscoveryRow>().map { FriendSuggestion(it.userId, it.name, it.email, it.profilePicUrl.ifBlank { null }, it.course, it.year, it.semester, it.score, it.reasons) }
}
