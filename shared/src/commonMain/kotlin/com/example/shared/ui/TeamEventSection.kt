package com.example.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.shared.model.Announcement
import com.example.shared.model.TeamRequirement
import com.example.shared.repository.TeamRepository
import kotlinx.coroutines.launch

@Composable
fun TeamEventSection(announcement: Announcement) {
    if (announcement.type != com.example.shared.model.AnnouncementType.EVENT || !announcement.eventMode.equals("Team", true)) return
    val repo = remember { TeamRepository() }
    val scope = rememberCoroutineScope()
    var requirements by remember { mutableStateOf<List<TeamRequirement>>(emptyList()) }
    var showFind by remember { mutableStateOf(false) }
    var showPost by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var needed by remember { mutableIntStateOf(2) }
    var status by remember { mutableStateOf<String?>(null) }
    fun refresh() = scope.launch { runCatching { requirements = repo.getTeamRequirements(announcement.id) }.onFailure { status = it.message ?: "Unable to load team requirements." } }
    LaunchedEffect(announcement.id) { refresh() }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row { Icon(Icons.Default.Groups, null); Spacer(Modifier.width(8.dp)); Text("Team Event", style = MaterialTheme.typography.titleLarge) }
            Text("Create a team, find teammates, or post what your team needs.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            repo.createTeam(announcement.id, "${announcement.title} Team", announcement.eventMaxMembers)
                            status = "Team created. Other students can now join it."
                        }.onFailure { status = it.message ?: "Unable to create team." }
                    }
                }) { Text("Create Team") }
                OutlinedButton(onClick = { showFind = true; refresh() }) { Text("Find Team Members") }
            }
            OutlinedButton(onClick = { showPost = true }) {
                Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("Post Team Requirement")
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (showFind) AlertDialog(
        onDismissRequest = { showFind = false },
        title = { Text("Find Team Members") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (requirements.isEmpty()) Text("No requirements posted yet.")
                requirements.forEach { r ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Need ${r.membersNeeded} member(s)")
                            Text(r.content)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching { repo.respondToRequirement(r.id); status = "Interest sent to the requirement owner." }
                                            .onFailure { status = it.message ?: "Unable to send interest." }
                                    }
                                }) { Text("I'm interested") }
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching { repo.sendFriendRequest(r.authorId); status = "Friend request sent to the requirement owner." }
                                            .onFailure { status = it.message ?: "Unable to send friend request." }
                                    }
                                }) { Text("Contact owner") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showFind = false }) { Text("Close") } }
    )

    if (showPost) AlertDialog(
        onDismissRequest = { showPost = false },
        title = { Text("Team Requirement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("Requirement") }, placeholder = { Text("Need 2 members — Android/Kotlin experience") })
                OutlinedTextField(needed.toString(), { needed = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 }, label = { Text("Members needed") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(enabled = text.isNotBlank(), onClick = {
                scope.launch {
                    runCatching { repo.createTeamRequirement(announcement.id, text, needed); text = ""; showPost = false; refresh(); status = "Requirement posted." }
                        .onFailure { status = it.message ?: "Unable to post requirement." }
                }
            }) { Text("Post") }
        },
        dismissButton = { TextButton(onClick = { showPost = false }) { Text("Cancel") } }
    )
}
