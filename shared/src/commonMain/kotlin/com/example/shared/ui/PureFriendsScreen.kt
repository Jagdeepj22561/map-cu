package com.example.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shared.model.FriendSuggestion
import com.example.shared.repository.TeamRepository
import kotlinx.coroutines.launch

@Composable
fun PureFriendsScreen() {
    val repository = remember { TeamRepository() }
    var people by remember { mutableStateOf<List<FriendSuggestion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sendingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    fun refresh() = scope.launch {
        loading = true
        error = null
        runCatching {
            people = repository.discoverFriends()
            pendingIds = repository.getPendingFriendRequestUserIds()
        }.onFailure { error = it.message ?: "Unable to find people." }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Find people", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Connect with students around you", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Refresh suggestions") }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Campus connections", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Suggestions use shared academic and campus signals.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            error != null -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { refresh() }) { Text("Try again") }
            }
            people.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No new people to suggest right now.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(people, key = { it.userId }) { person ->
                    val isPending = person.userId in pendingIds
                    val isSending = person.userId in sendingIds

                    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(48.dp).clip(CircleShape), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Box(contentAlignment = Alignment.Center) { Text(person.name.firstOrNull()?.uppercase() ?: "S", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(person.name.ifBlank { "Student" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                val academics = listOf(person.course, person.year, person.semester).filter { it.isNotBlank() }.joinToString(" • ")
                                if (academics.isNotBlank()) Text(academics, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (person.reasons.isNotEmpty()) {
                                    Spacer(Modifier.height(5.dp))
                                    Text(person.reasons.joinToString("  •  "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            when {
                                isPending -> AssistChip(
                                    onClick = {},
                                    label = { Text("Request Pending") }
                                )
                                isSending -> FilledTonalButton(
                                    onClick = {},
                                    enabled = false
                                ) { Text("Sending…") }
                                else -> FilledTonalButton(
                                    onClick = {
                                        sendingIds = sendingIds + person.userId
                                        scope.launch {
                                            runCatching {
                                                repository.sendFriendRequest(person.userId)
                                                pendingIds = pendingIds + person.userId
                                            }.onFailure {
                                                error = it.message ?: "Unable to send request."
                                            }
                                            sendingIds = sendingIds - person.userId
                                        }
                                    },
                                    enabled = person.userId !in sendingIds
                                ) {
                                    Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Add")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
