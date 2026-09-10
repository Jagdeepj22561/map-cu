package com.example.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    var sentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    fun refresh() = scope.launch {
        loading = true
        error = null
        runCatching { people = repository.discoverFriends() }.onFailure { error = it.message ?: "Unable to find people." }
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Find Friends", style = MaterialTheme.typography.headlineSmall)
                Text("People you may know based on campus connections.", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Refresh suggestions") }
        }
        Spacer(Modifier.height(12.dp))
        when {
            loading -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator() }
            error != null -> Column { Text(error!!); Button(onClick = { refresh() }) { Text("Retry") } }
            people.isEmpty() -> Text("No new suggestions right now.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(people, key = { it.userId }) { person ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(person.name.ifBlank { "Student" }, style = MaterialTheme.typography.titleMedium)
                                val academics = listOf(person.course, person.year, person.semester).filter { it.isNotBlank() }.joinToString(" • ")
                                if (academics.isNotBlank()) Text(academics, style = MaterialTheme.typography.bodySmall)
                                if (person.reasons.isNotEmpty()) Text(person.reasons.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                            }
                            if (person.userId in sentIds) AssistChip(onClick = {}, label = { Text("Request sent") })
                            else FilledIconButton(onClick = {
                                scope.launch { runCatching { repository.sendFriendRequest(person.userId); sentIds += person.userId }.onFailure { error = it.message ?: "Unable to send friend request." } }
                            }) { Icon(Icons.Default.PersonAdd, "Add friend") }
                        }
                    }
                }
            }
        }
    }
}
