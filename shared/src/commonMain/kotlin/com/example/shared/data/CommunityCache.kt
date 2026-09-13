package com.example.shared.data

import com.example.shared.ui.GroupRow
import com.example.shared.ui.GroupMember
import com.example.shared.ui.GroupMessageRow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object CommunityCache {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var inMemoryGroups: List<GroupRow>? = null
    private var inMemoryJoinedIds: Set<String>? = null
    private val inMemoryMessages: MutableMap<String, List<GroupMessageRow>> = mutableMapOf()
    private val inMemoryMembers: MutableMap<String, List<GroupMember>> = mutableMapOf()

    fun getCommunities(): Pair<List<GroupRow>?, Set<String>?> {
        if (inMemoryGroups != null) {
            return Pair(inMemoryGroups, inMemoryJoinedIds)
        }
        val groupsJson = PlatformDiskCache.get("cached_community_groups")
        val joinedJson = PlatformDiskCache.get("cached_community_joined_ids")
        if (!groupsJson.isNullOrBlank()) {
            runCatching {
                inMemoryGroups = json.decodeFromString<List<GroupRow>>(groupsJson)
            }
        }
        if (!joinedJson.isNullOrBlank()) {
            runCatching {
                inMemoryJoinedIds = json.decodeFromString<Set<String>>(joinedJson)
            }
        }
        return Pair(inMemoryGroups, inMemoryJoinedIds)
    }

    fun saveCommunities(groups: List<GroupRow>, joinedIds: Set<String>) {
        inMemoryGroups = groups
        inMemoryJoinedIds = joinedIds
        runCatching {
            PlatformDiskCache.set("cached_community_groups", json.encodeToString(groups))
            PlatformDiskCache.set("cached_community_joined_ids", json.encodeToString(joinedIds))
        }
    }

    fun getMessages(groupId: String): List<GroupMessageRow>? {
        inMemoryMessages[groupId]?.let { return it }
        val diskJson = PlatformDiskCache.get("cached_community_msgs_$groupId")
        if (!diskJson.isNullOrBlank()) {
            runCatching {
                val msgs = json.decodeFromString<List<GroupMessageRow>>(diskJson)
                inMemoryMessages[groupId] = msgs
                return msgs
            }
        }
        return null
    }

    fun saveMessages(groupId: String, messages: List<GroupMessageRow>) {
        inMemoryMessages[groupId] = messages
        runCatching {
            val toSave = if (messages.size > 100) messages.takeLast(100) else messages
            PlatformDiskCache.set("cached_community_msgs_$groupId", json.encodeToString(toSave))
        }
    }

    fun getMembers(groupId: String): List<GroupMember>? {
        return inMemoryMembers[groupId]
    }

    fun saveMembers(groupId: String, members: List<GroupMember>) {
        inMemoryMembers[groupId] = members
    }

    fun removeCommunity(groupId: String) {
        inMemoryGroups = inMemoryGroups?.filter { it.id != groupId }
        inMemoryJoinedIds = inMemoryJoinedIds?.minus(groupId)
        inMemoryMessages.remove(groupId)
        inMemoryMembers.remove(groupId)
        runCatching {
            PlatformDiskCache.remove("cached_community_msgs_$groupId")
            inMemoryGroups?.let { PlatformDiskCache.set("cached_community_groups", json.encodeToString(it)) }
            inMemoryJoinedIds?.let { PlatformDiskCache.set("cached_community_joined_ids", json.encodeToString(it)) }
        }
    }
}