package com.example.shared.data

import com.example.shared.model.EventListItem
import com.example.shared.model.EventTimelineItem
import com.example.shared.model.TeamEventInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object EventCache {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var inMemoryEvents: List<EventListItem>? = null
    private val inMemoryTimeline: MutableMap<String, List<EventTimelineItem>> = mutableMapOf()
    private val inMemoryTeams: MutableMap<String, List<TeamEventInfo>> = mutableMapOf()
    private val inMemoryMyTeam: MutableMap<String, TeamEventInfo> = mutableMapOf()
    private val inMemoryJoinRequests: MutableMap<String, List<com.example.shared.model.JoinRequest>> = mutableMapOf()

    fun getEvents(): List<EventListItem>? {
        if (inMemoryEvents != null) {
            return inMemoryEvents
        }
        val diskJson = PlatformDiskCache.get("cached_events_list")
        if (!diskJson.isNullOrBlank()) {
            runCatching {
                val list = json.decodeFromString<List<EventListItem>>(diskJson)
                inMemoryEvents = list
                return list
            }
        }
        return null
    }

    fun saveEvents(events: List<EventListItem>) {
        inMemoryEvents = events
        runCatching {
            PlatformDiskCache.set("cached_events_list", json.encodeToString(events))
        }
    }

    fun getTimeline(announcementId: String): List<EventTimelineItem>? {
        inMemoryTimeline[announcementId]?.let { return it }
        val diskJson = PlatformDiskCache.get("cached_timeline_$announcementId")
        if (!diskJson.isNullOrBlank()) {
            runCatching {
                val items = json.decodeFromString<List<EventTimelineItem>>(diskJson)
                inMemoryTimeline[announcementId] = items
                return items
            }
        }
        return null
    }

    fun saveTimeline(announcementId: String, items: List<EventTimelineItem>) {
        inMemoryTimeline[announcementId] = items
        runCatching {
            PlatformDiskCache.set("cached_timeline_$announcementId", json.encodeToString(items))
        }
    }

    fun getTeams(announcementId: String): List<TeamEventInfo>? {
        inMemoryTeams[announcementId]?.let { return it }
        val diskJson = PlatformDiskCache.get("cached_teams_$announcementId")
        if (!diskJson.isNullOrBlank()) {
            runCatching {
                val teams = json.decodeFromString<List<TeamEventInfo>>(diskJson)
                inMemoryTeams[announcementId] = teams
                return teams
            }
        }
        return null
    }

    fun saveTeams(announcementId: String, teams: List<TeamEventInfo>) {
        inMemoryTeams[announcementId] = teams
        runCatching {
            PlatformDiskCache.set("cached_teams_$announcementId", json.encodeToString(teams))
        }
    }

    fun getMyTeam(announcementId: String): TeamEventInfo? {
        inMemoryMyTeam[announcementId]?.let { return it }
        val diskJson = PlatformDiskCache.get("cached_my_team_$announcementId")
        if (!diskJson.isNullOrBlank()) {
            runCatching {
                val team = json.decodeFromString<TeamEventInfo>(diskJson)
                inMemoryMyTeam[announcementId] = team
                return team
            }
        }
        return null
    }

    fun saveMyTeam(announcementId: String, team: TeamEventInfo?) {
        if (team != null) {
            inMemoryMyTeam[announcementId] = team
            runCatching {
                PlatformDiskCache.set("cached_my_team_$announcementId", json.encodeToString(team))
            }
        } else {
            inMemoryMyTeam.remove(announcementId)
            runCatching {
                PlatformDiskCache.set("cached_my_team_$announcementId", "")
            }
        }
    }

    fun getJoinRequests(teamId: String): List<com.example.shared.model.JoinRequest>? {
        inMemoryJoinRequests[teamId]?.let { return it }
        val diskJson = PlatformDiskCache.get("cached_join_reqs_$teamId")
        if (!diskJson.isNullOrBlank()) {
            runCatching {
                val list = json.decodeFromString<List<com.example.shared.model.JoinRequest>>(diskJson)
                inMemoryJoinRequests[teamId] = list
                return list
            }
        }
        return null
    }

    fun saveJoinRequests(teamId: String, requests: List<com.example.shared.model.JoinRequest>) {
        inMemoryJoinRequests[teamId] = requests
        runCatching {
            PlatformDiskCache.set("cached_join_reqs_$teamId", json.encodeToString(requests))
        }
    }
}