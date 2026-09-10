package com.example.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shared.data.SupabaseClientProvider
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class GroupRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val description: String = "",
    @SerialName("public_group_id") val publicGroupId: String? = null,
    @SerialName("is_public") val isPublic: Boolean = true
)

@Serializable
private data class CreateGroupRow(
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val description: String,
    @SerialName("is_public") val isPublic: Boolean
)

@Serializable
private data class MemberRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    val role: String = "member"
)

@Composable
fun PureGroupsScreen() {
    val client = remember { SupabaseClientProvider.client }
    var groups by remember { mutableStateOf<List<GroupRow>>(emptyList()) }
    var joinedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        error = null
        runCatching {
            groups = client.from("groups").select {
                order("created_at", Order.DESCENDING)
                limit(100)
            }.decodeList<GroupRow>()

            val uid = client.auth.currentUserOrNull()?.id
            joinedIds = if (uid == null) emptySet() else {
                client.from("group_members").select {
                    filter { eq("user_id", uid) }
                }.decodeList<MemberRow>().map { it.groupId }.toSet()
            }
        }.onFailure {
            error = it.message ?: "Unable to load groups."
        }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Groups", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Find and create student communities", style = MaterialTheme.typography.bodyMedium)
            }
            FilledIconButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create group")
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(error!!)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { scope.launch { refresh() } }) { Text("Retry") }
            }
            groups.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Group, null, Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("No groups yet", style = MaterialTheme.typography.titleMedium)
                Text("Create the first student group.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showCreate = true }) { Text("Create group") }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groups, key = { it.id }) { group ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (group.description.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(group.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(12.dp))
                            if (joinedIds.contains(group.id)) {
                                AssistChip(onClick = {}, label = { Text("Joined") })
                            } else {
                                Button(onClick = {
                                    val uid = client.auth.currentUserOrNull()?.id ?: return@Button
                                    scope.launch {
                                        runCatching {
                                            client.from("group_members").insert(
                                                MemberRow(groupId = group.id, userId = uid)
                                            )
                                            refresh()
                                        }.onFailure { error = it.message ?: "Unable to join group." }
                                    }
                                }) { Text("Join") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { if (!saving) showCreate = false },
            title = { Text("Create group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Group name") }, singleLine = true)
                    OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank() && !saving,
                    onClick = {
                        val uid = client.auth.currentUserOrNull()?.id ?: return@Button
                        saving = true
                        scope.launch {
                            runCatching {
                                client.from("groups").insert(
                                    CreateGroupRow(
                                        ownerId = uid,
                                        name = name.trim(),
                                        description = description.trim(),
                                        isPublic = true
                                    )
                                )
                                name = ""
                                description = ""
                                showCreate = false
                                refresh()
                            }.onFailure { error = it.message ?: "Unable to create group." }
                            saving = false
                        }
                    }
                ) { Text(if (saving) "Creating..." else "Create") }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }
}
