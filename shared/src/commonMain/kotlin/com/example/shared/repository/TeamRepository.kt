package com.example.shared.repository

import com.example.shared.data.SupabaseClientProvider
import com.example.shared.data.EventNotificationClient
import com.example.shared.model.FriendSuggestion
import com.example.shared.model.TeamRequirement
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
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
    @Serializable private data class FriendRequestRow(
        val id: String,
        @SerialName("sender_id") val senderId: String,
        @SerialName("receiver_id") val receiverId: String,
        val status: String = "pending",
        @SerialName("created_at") val createdAt: String = ""
    )
    @Serializable private data class DiscoveryParams(@SerialName("p_limit") val limit: Int)
    @Serializable private data class DiscoveryRow(@SerialName("user_id") val userId: String, val name: String, val email: String, @SerialName("profile_pic_url") val profilePicUrl: String = "", val course: String = "", val year: String = "", val semester: String = "", val score: Int = 0, val reasons: List<String> = emptyList())
    @Serializable private data class EventTeamRow(
        val id: String,
        @SerialName("announcement_id") val announcementId: String,
        @SerialName("owner_id") val ownerId: String,
        val name: String,
        @SerialName("max_members") val maxMembers: Int? = null,
        @SerialName("created_at") val createdAt: String = ""
    )
    @Serializable private data class TeamMemberRow(
        @SerialName("user_id") val userId: String,
        @SerialName("team_id") val teamId: String = "",
        val role: String = "member",
        @SerialName("joined_at") val joinedAt: String = ""
    )
    @Serializable private data class ProfileLiteRow(
        val id: String,
        val name: String,
        @SerialName("profile_pic_url") val profilePicUrl: String = ""
    )
    @Serializable private data class ResponseInsert(@SerialName("requirement_id") val requirementId: String, @SerialName("user_id") val userId: String)
    @Serializable private data class ResponseRow(@SerialName("requirement_id") val requirementId: String, @SerialName("user_id") val userId: String)
    @Serializable private data class JoinRequestInsert(
        @SerialName("team_id") val teamId: String,
        @SerialName("requester_id") val requesterId: String,
        val message: String = ""
    )
    @Serializable private data class JoinRequestRow(
        val id: String,
        @SerialName("team_id") val teamId: String,
        @SerialName("requester_id") val requesterId: String,
        val message: String = "",
        val status: String = "pending",
        @SerialName("reviewed_by") val reviewedBy: String? = null,
        @SerialName("created_at") val createdAt: String = ""
    )

    suspend fun createTeam(announcementId: String, name: String, maxMembers: Int?): String {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        client.from("event_teams").insert(TeamInsert(announcementId, uid, name.trim(), maxMembers))
        val teamId = client.from("event_teams").select { filter { eq("announcement_id", announcementId); eq("owner_id", uid) } }.decodeSingle<TeamRow>().id
        runCatching {
            client.from("event_team_members").insert(MemberInsert(teamId, uid, role = "owner"))
        }
        return teamId
    }

    suspend fun joinTeam(teamId: String) {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        client.from("event_team_members").insert(MemberInsert(teamId, uid))
        EventNotificationClient.dispatch("team_member_joined", teamId)
    }

    suspend fun createTeamRequirement(announcementId: String, content: String, membersNeeded: Int) {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        require(content.trim().isNotBlank())
        require(membersNeeded > 0)
        val trimmed = content.trim()
        val existing = client.from("team_requirements")
            .select { filter { eq("announcement_id", announcementId); eq("author_id", uid); eq("content", trimmed) } }
            .decodeList<RequirementRow>()
        if (existing.isNotEmpty()) error("You already posted this requirement.")
        runCatching {
            client.from("team_requirements").insert(RequirementInsert(announcementId, uid, trimmed, membersNeeded))
        }.onFailure { e ->
            val msg = e.message.orEmpty()
            val friendly = when {
                msg.contains("duplicate key", ignoreCase = true) ||
                    msg.contains("unique", ignoreCase = true) ||
                    msg.contains("already exists", ignoreCase = true) ->
                    "You already posted this requirement."
                else -> msg.ifBlank { "Unable to post requirement." }
            }
            error(friendly)
        }
    }

    suspend fun deleteRequirement(requirementId: String) {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        val existing = client.from("team_requirements")
            .select { filter { eq("id", requirementId); eq("author_id", uid) } }
            .decodeList<RequirementRow>()
        if (existing.isEmpty()) error("You can only delete your own requirements.")
        runCatching {
            client.from("team_requirement_responses").delete { filter { eq("requirement_id", requirementId) } }
        }
        client.from("team_requirements").delete { filter { eq("id", requirementId); eq("author_id", uid) } }
    }

    suspend fun getTeamRequirements(announcementId: String): List<TeamRequirement> =
        client.from("team_requirements")
            .select { filter { eq("announcement_id", announcementId) }; order("created_at", Order.DESCENDING) }
            .decodeList<RequirementRow>()
            .map { TeamRequirement(it.id, it.announcementId, it.authorId, it.content, it.membersNeeded, it.createdAt) }

    suspend fun respondToRequirement(requirementId: String) {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        runCatching {
            client.from("team_requirement_responses").insert(ResponseInsert(requirementId, uid))
        }.onFailure { e ->
            val msg = e.message.orEmpty()
            val friendly = when {
                msg.contains("duplicate key", ignoreCase = true) ||
                    msg.contains("unique", ignoreCase = true) ||
                    msg.contains("already exists", ignoreCase = true) ->
                    "You have already expressed interest in this requirement."
                else -> msg.ifBlank { "Unable to send interest." }
            }
            error(friendly)
        }
    }

    fun myUserId(): String? = client.auth.currentUserOrNull()?.id

    suspend fun getMyRespondedRequirementIds(announcementId: String): Set<String> {
        val uid = client.auth.currentUserOrNull()?.id ?: return emptySet()
        val reqIds = client.from("team_requirements")
            .select { filter { eq("announcement_id", announcementId) } }
            .decodeList<RequirementRow>()
            .map { it.id }
        if (reqIds.isEmpty()) return emptySet()
        return client.from("team_requirement_responses")
            .select { filter { eq("user_id", uid); isIn("requirement_id", reqIds) } }
            .decodeList<ResponseRow>()
            .map { it.requirementId }
            .toSet()
    }

    suspend fun getResponsesForRequirement(requirementId: String): List<com.example.shared.model.TeamMemberInfo> {
        val rows = runCatching {
            client.from("team_requirement_responses")
                .select { filter { eq("requirement_id", requirementId) }; order("created_at", Order.ASCENDING) }
                .decodeList<ResponseRow>()
        }.getOrDefault(emptyList())
        if (rows.isEmpty()) return emptyList()
        val userIds = rows.map { it.userId }.distinct()
        val profiles = runCatching {
            client.from("profiles").select { filter { isIn("id", userIds) } }.decodeList<ProfileLiteRow>()
        }.getOrDefault(emptyList()).associateBy { it.id }
        return rows.map { row ->
            val profile = profiles[row.userId]
            com.example.shared.model.TeamMemberInfo(
                userId = row.userId,
                name = profile?.name ?: "Member",
                profilePicUrl = profile?.profilePicUrl?.takeIf { it.isNotBlank() },
                role = "interested"
            )
        }
    }

    suspend fun sendFriendRequest(receiverId: String) {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        require(uid != receiverId) { "You cannot send a friend request to yourself." }
        runCatching {
            client.from("friend_requests").insert(FriendRequestInsert(uid, receiverId))
        }.onFailure { e ->
            val msg = e.message.orEmpty()
            val friendly = when {
                msg.contains("already sent you a friend request", ignoreCase = true) ||
                    msg.contains("bidirectional pending", ignoreCase = true) ->
                    "This user has already sent you a friend request."
                msg.contains("already friends", ignoreCase = true) ||
                    msg.contains("friendship overlap", ignoreCase = true) ->
                    "You are already friends with this user."
                msg.contains("already sent", ignoreCase = true) ||
                    msg.contains("duplicate key", ignoreCase = true) ||
                    msg.contains("unique", ignoreCase = true) ->
                    "Friend request already sent."
                else -> msg.ifBlank { "Failed to send friend request." }
            }
            error(friendly)
        }
        val createdRequest = client.from("friend_requests").select {
            filter { eq("sender_id", uid); eq("receiver_id", receiverId); eq("status", "pending") }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.decodeList<FriendRequestRow>().firstOrNull()
        if (createdRequest != null) {
            EventNotificationClient.dispatch("friend_request_created", createdRequest.id)
        }
    }

    suspend fun getPendingFriendRequestUserIds(): Set<String> {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        val requests = client.from("friend_requests")
            .select { filter { eq("status", "pending"); eq("sender_id", uid) } }
            .decodeList<FriendRequestRow>()
        val incoming = client.from("friend_requests")
            .select { filter { eq("status", "pending"); eq("receiver_id", uid) } }
            .decodeList<FriendRequestRow>()
        return (requests.asSequence().map { it.receiverId } + incoming.asSequence().map { it.senderId }).toSet()
    }

    suspend fun discoverFriends(limit: Int = 30): List<FriendSuggestion> =
        client.postgrest.rpc("discover_people", DiscoveryParams(limit.coerceIn(1, 100)))
            .decodeList<DiscoveryRow>()
            .map { FriendSuggestion(it.userId, it.name, it.email, it.profilePicUrl.ifBlank { null }, it.course, it.year, it.semester, it.score, it.reasons) }

    suspend fun renameTeam(teamId: String, newName: String) {
        client.auth.currentUserOrNull()?.id ?: error("Login required")
        require(newName.isNotBlank()) { "Team name cannot be empty." }
        client.from("event_teams").update({ set("name", newName.trim()) }) {
            filter { eq("id", teamId) }
        }
    }

    suspend fun getMyTeam(announcementId: String): com.example.shared.model.TeamEventInfo? {
        val uid = client.auth.currentUserOrNull()?.id ?: return null
        val team = runCatching {
            client.from("event_teams").select {
                filter { eq("announcement_id", announcementId); eq("owner_id", uid) }
                limit(1)
            }.decodeList<EventTeamRow>().firstOrNull()
        }.getOrNull() ?: return null
        val members = getTeamMembersInternal(team.id)
        return com.example.shared.model.TeamEventInfo(
            id = team.id,
            announcementId = team.announcementId,
            ownerId = team.ownerId,
            name = team.name,
            maxMembers = team.maxMembers,
            members = members,
            createdAt = team.createdAt
        )
    }

    suspend fun getTeamsForAnnouncement(announcementId: String): List<com.example.shared.model.TeamEventInfo> {
        val teams = client.from("event_teams").select {
            filter { eq("announcement_id", announcementId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<EventTeamRow>()
        if (teams.isEmpty()) return emptyList()

        val teamIds = teams.map { it.id }
        val memberRows = runCatching {
            client.from("event_team_members").select {
                filter { isIn("team_id", teamIds) }
                order("joined_at", Order.ASCENDING)
            }.decodeList<TeamMemberRow>()
        }.getOrDefault(emptyList())

        val userIds = memberRows.map { it.userId }.distinct()
        val profiles = if (userIds.isNotEmpty()) {
            runCatching {
                client.from("profiles").select {
                    filter { isIn("id", userIds) }
                }.decodeList<ProfileLiteRow>()
            }.getOrDefault(emptyList()).associateBy { it.id }
        } else {
            emptyMap()
        }

        val membersByTeamId = memberRows.groupBy { it.teamId }.mapValues { (_, rows) ->
            rows.map { row ->
                val profile = profiles[row.userId]
                com.example.shared.model.TeamMemberInfo(
                    userId = row.userId,
                    name = profile?.name ?: "Member",
                    profilePicUrl = profile?.profilePicUrl?.takeIf { it.isNotBlank() },
                    role = row.role
                )
            }
        }

        return teams.map { team ->
            com.example.shared.model.TeamEventInfo(
                id = team.id,
                announcementId = team.announcementId,
                ownerId = team.ownerId,
                name = team.name,
                maxMembers = team.maxMembers,
                members = membersByTeamId[team.id] ?: emptyList(),
                createdAt = team.createdAt
            )
        }
    }

    private suspend fun getTeamMembersInternal(teamId: String): List<com.example.shared.model.TeamMemberInfo> {
        val rows = runCatching {
            client.from("event_team_members").select {
                filter { eq("team_id", teamId) }
                order("joined_at", Order.ASCENDING)
            }.decodeList<TeamMemberRow>()
        }.getOrDefault(emptyList())
        if (rows.isEmpty()) return emptyList()
        val userIds = rows.map { it.userId }.distinct()
        val profiles = runCatching {
            client.from("profiles").select {
                filter { isIn("id", userIds) }
            }.decodeList<ProfileLiteRow>()
        }.getOrDefault(emptyList()).associateBy { it.id }
        return rows.map { row ->
            val profile = profiles[row.userId]
            com.example.shared.model.TeamMemberInfo(
                userId = row.userId,
                name = profile?.name ?: "Member",
                profilePicUrl = profile?.profilePicUrl?.takeIf { it.isNotBlank() },
                role = row.role
            )
        }
    }

    @Serializable private data class RequestIdParam(@SerialName("p_request_id") val requestId: String)

    suspend fun requestToJoinTeam(teamId: String, message: String = "") {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        // Verify user is not owner of the team
        val team = runCatching {
            client.from("event_teams").select {
                filter { eq("id", teamId) }
                limit(1)
            }.decodeList<EventTeamRow>().firstOrNull()
        }.getOrNull()

        if (team != null && team.ownerId == uid) {
            error("You cannot join your own team.")
        }

        // Verify user is not already a member
        val isMember = runCatching {
            client.from("event_team_members").select {
                filter { eq("team_id", teamId); eq("user_id", uid) }
                limit(1)
            }.decodeList<TeamMemberRow>().isNotEmpty()
        }.getOrDefault(false)

        if (isMember) {
            error("You are already a member of this team.")
        }

        // Check if there is an existing pending request
        val existing = client.from("join_requests").select {
            filter { eq("team_id", teamId); eq("requester_id", uid) }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.decodeList<JoinRequestRow>().firstOrNull()

        if (existing != null && existing.status == "pending") {
            error("You already have a pending request for this team.")
        }

        // If previously rejected or approved or cancelled, delete old record so clean insert can proceed
        if (existing != null) {
            runCatching {
                client.from("join_requests").delete {
                    filter { eq("id", existing.id) }
                }
            }
        }

        runCatching {
            client.from("join_requests").insert(JoinRequestInsert(teamId, uid, message.trim()))
        }.onFailure { e ->
            val msg = e.message.orEmpty()
            val friendly = when {
                msg.contains("duplicate", ignoreCase = true) || msg.contains("unique", ignoreCase = true) ->
                    "You already have a pending request for this team."
                else -> msg.ifBlank { "Failed to send join request." }
            }
            error(friendly)
        }
        // The server uses this immutable request id to verify that the caller
        // owns a still-pending request before it notifies the team owner.
        val createdRequest = client.from("join_requests").select {
            filter { eq("team_id", teamId); eq("requester_id", uid); eq("status", "pending") }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.decodeList<JoinRequestRow>().firstOrNull()
        if (createdRequest != null) {
            EventNotificationClient.dispatch("team_join_requested", createdRequest.id)
        }
    }

    suspend fun getJoinRequestsForTeam(teamId: String): List<com.example.shared.model.JoinRequest> {
        val rows = client.from("join_requests").select {
            filter { eq("team_id", teamId); eq("status", "pending") }
            order("created_at", Order.DESCENDING)
        }.decodeList<JoinRequestRow>()
        if (rows.isEmpty()) return emptyList()
        val userIds = rows.map { it.requesterId }.distinct()
        val profiles = runCatching {
            client.from("profiles").select { filter { isIn("id", userIds) } }.decodeList<ProfileLiteRow>()
        }.getOrDefault(emptyList()).associateBy { it.id }
        return rows.map { row ->
            val profile = profiles[row.requesterId]
            com.example.shared.model.JoinRequest(
                id = row.id,
                teamId = row.teamId,
                requesterId = row.requesterId,
                requesterName = profile?.name ?: "User",
                requesterPicUrl = profile?.profilePicUrl?.takeIf { it.isNotBlank() },
                message = row.message,
                status = row.status,
                reviewedBy = row.reviewedBy,
                createdAt = row.createdAt
            )
        }
    }

    suspend fun approveJoinRequest(requestId: String) {
        client.auth.currentUserOrNull()?.id ?: error("Login required")
        client.postgrest.rpc("approve_join_request", RequestIdParam(requestId))
        EventNotificationClient.dispatch("team_join_approved", requestId)
    }

    suspend fun rejectJoinRequest(requestId: String) {
        client.auth.currentUserOrNull()?.id ?: error("Login required")
        client.postgrest.rpc("reject_join_request", RequestIdParam(requestId))
        EventNotificationClient.dispatch("team_join_rejected", requestId)
    }

    suspend fun withdrawJoinRequest(requestId: String) {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        client.from("join_requests").delete {
            filter { eq("id", requestId); eq("requester_id", uid) }
        }
    }

    suspend fun kickMember(teamId: String, userId: String) {
        client.auth.currentUserOrNull()?.id ?: error("Login required")
        client.from("event_team_members").delete {
            filter { eq("team_id", teamId); eq("user_id", userId) }
        }
    }

    suspend fun leaveTeam(teamId: String) {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        client.from("event_team_members").delete {
            filter { eq("team_id", teamId); eq("user_id", uid) }
        }
    }

    suspend fun disbandTeam(teamId: String) {
        val uid = client.auth.currentUserOrNull()?.id ?: error("Login required")
        runCatching { client.from("join_requests").delete { filter { eq("team_id", teamId) } } }
        runCatching { client.from("event_team_members").delete { filter { eq("team_id", teamId) } } }
        client.from("event_teams").delete {
            filter { eq("id", teamId); eq("owner_id", uid) }
        }
    }

    suspend fun getMyTeamMembership(announcementId: String): com.example.shared.model.TeamEventInfo? {
        val uid = client.auth.currentUserOrNull()?.id ?: return null
        val owned = getMyTeam(announcementId)
        if (owned != null) return owned

        val memberships = runCatching {
            client.from("event_team_members").select {
                filter { eq("user_id", uid) }
            }.decodeList<TeamMemberRow>()
        }.getOrNull() ?: emptyList()

        for (m in memberships) {
            if (m.teamId.isBlank()) continue
            val team = runCatching {
                client.from("event_teams").select {
                    filter { eq("id", m.teamId); eq("announcement_id", announcementId) }
                    limit(1)
                }.decodeList<EventTeamRow>().firstOrNull()
            }.getOrNull()
            if (team != null && team.announcementId == announcementId) {
                val members = getTeamMembersInternal(team.id)
                return com.example.shared.model.TeamEventInfo(
                    id = team.id,
                    announcementId = team.announcementId,
                    ownerId = team.ownerId,
                    name = team.name,
                    maxMembers = team.maxMembers,
                    members = members,
                    createdAt = team.createdAt
                )
            }
        }
        return null
    }

    suspend fun getMyJoinRequestForTeam(teamId: String): com.example.shared.model.JoinRequest? {
        val uid = client.auth.currentUserOrNull()?.id ?: return null
        return runCatching {
            val row = client.from("join_requests").select {
                filter { eq("team_id", teamId); eq("requester_id", uid) }
                order("created_at", Order.DESCENDING)
                limit(1)
            }.decodeList<JoinRequestRow>().firstOrNull() ?: return null
            com.example.shared.model.JoinRequest(
                id = row.id,
                teamId = row.teamId,
                requesterId = row.requesterId,
                requesterName = "",
                message = row.message,
                status = row.status,
                reviewedBy = row.reviewedBy,
                createdAt = row.createdAt
            )
        }.getOrNull()
    }

    /** Loads the current user's pending state for all visible teams in one query. */
    suspend fun getMyPendingJoinRequestsForTeams(
        teamIds: List<String>
    ): Map<String, com.example.shared.model.JoinRequest> {
        val uid = client.auth.currentUserOrNull()?.id ?: return emptyMap()
        if (teamIds.isEmpty()) return emptyMap()
        return client.from("join_requests").select {
            filter {
                eq("requester_id", uid)
                eq("status", "pending")
                isIn("team_id", teamIds.distinct())
            }
            order("created_at", Order.DESCENDING)
        }.decodeList<JoinRequestRow>()
            .distinctBy(JoinRequestRow::teamId)
            .associate { row ->
                row.teamId to com.example.shared.model.JoinRequest(
                    id = row.id,
                    teamId = row.teamId,
                    requesterId = row.requesterId,
                    requesterName = "",
                    message = row.message,
                    status = row.status,
                    reviewedBy = row.reviewedBy,
                    createdAt = row.createdAt
                )
            }
    }
}
