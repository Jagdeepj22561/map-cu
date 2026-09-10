package com.example.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    var showPost by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var needed by remember { mutableIntStateOf(2) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            runCatching { repo.getTeamRequirements(announcement.id) }
                .onSuccess { requirements = it; status = null }
                .onFailure { status = it.message ?: "Unable to load team requirements." }
            loading = false
        }
    }

    LaunchedEffect(announcement.id) { refresh() }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                Icon(Icons.Default.Groups, null)
                Spacer(Modifier.width(8.dp))
                Text("Team Event", style = MaterialTheme.typography.titleLarge)
            }
            Text("Create a team, find teammates, or post what your team needs.")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            repo.createTeam(announcement.id, "${announcement.title} Team", announcement.eventMaxMembers)
                        }.onSuccess {
                            status = "Team created successfully."
                            refresh()
                        }.onFailure {
                            status = it.message ?: "Unable to create team."
                        }
                    }
                }) { Text("Create Team") }

                OutlinedButton(onClick = { showFind = true; refresh() }) {
                    Text("Find Team Members")
                }
            }

            OutlinedButton(
                onClick = { showPost = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PersonAdd, null)
                Spacer(Modifier.width(6.dp))
                Text("Post Team Requirement")
            }

            // Requirements are now visible directly on the detail page.
            Text("Team Requirements", style = MaterialTheme.typography.titleMedium)
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(22.dp))
                requirements.isEmpty() -> Text(
                    "No team requirements posted yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> requirements.forEach { requirement ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${requirement.membersNeeded} member(s) needed", style = MaterialTheme.typography.labelLarge)
                            Text(requirement.content, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { repo.respondToRequirement(requirement.id) }
                                        .onSuccess { status = "Interest sent." }
                                        .onFailure { status = it.message ?: "Unable to send interest." }
                                }
                            }) { Text("I'm interested") }
                        }
                    }
                }
            }

            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (showFind) {
        AlertDialog(
            onDismissRequest = { showFind = false },
            title = { Text("Find Team Members") },
            text = {
                if (requirements.isEmpty()) {
                    Text("No requirements posted yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(requirements, key = { it.id }) { r ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Need ${r.membersNeeded} member(s)")
                                    Text(r.content)
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatching { repo.respondToRequirement(r.id) }
                                                .onSuccess { status = "Interest sent to the requirement owner." }
                                                .onFailure { status = it.message ?: "Unable to send interest." }
                                        }
                                    }) { Text("I'm interested") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFind = false }) { Text("Close") } }
        )
    }

    if (showPost) {
        AlertDialog(
            onDismissRequest = { showPost = false },
            title = { Text("Team Requirement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Requirement") },
                        placeholder = { Text("Need 2 members — Android/Kotlin experience") }
                    )
                    OutlinedTextField(
                        value = needed.toString(),
                        onValueChange = { needed = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                        label = { Text("Members needed") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(enabled = text.isNotBlank(), onClick = {
                    scope.launch {
                        runCatching {
                            repo.createTeamRequirement(announcement.id, text, needed)
                        }.onSuccess {
                            text = ""
                            showPost = false
                            status = "Requirement posted."
                            refresh()
                        }.onFailure {
                            status = it.message ?: "Unable to post requirement."
                        }
                    }
                }) { Text("Post") }
            },
            dismissButton = { TextButton(onClick = { showPost = false }) { Text("Cancel") } }
        )
    }
}
