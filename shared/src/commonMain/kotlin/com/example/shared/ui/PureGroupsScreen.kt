package com.example.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
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

@Serializable private data class GroupRow(val id: String, @SerialName("owner_id") val ownerId: String, val name: String, val description: String = "", @SerialName("public_group_id") val publicGroupId: String? = null, @SerialName("is_public") val isPublic: Boolean = true)
@Serializable private data class CreateGroupRow(@SerialName("owner_id") val ownerId: String, val name: String, val description: String, @SerialName("is_public") val isPublic: Boolean)
@Serializable private data class MemberRow(@SerialName("group_id") val groupId: String, @SerialName("user_id") val userId: String, val role: String = "member")

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
        loading = true; error = null
        runCatching {
            groups = client.from("groups").select { order("created_at", Order.DESCENDING); limit(100) }.decodeList<GroupRow>()
            val uid = client.auth.currentUserOrNull()?.id
            joinedIds = if (uid == null) emptySet() else client.from("group_members").select { filter { eq("user_id", uid) } }.decodeList<MemberRow>().map { it.groupId }.toSet()
        }.onFailure { error = it.message ?: "Unable to load groups." }
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(46.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary) } }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Communities", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Find your people on campus", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledIconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "Create group") }
                }
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            error != null -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(error!!, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(12.dp)); Button(onClick = { scope.launch { refresh() } }) { Text("Retry") } }
            groups.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Surface(Modifier.size(72.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) } }
                Spacer(Modifier.height(14.dp)); Text("No communities yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("Start a community for your course, club or project.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp)); Button(onClick = { showCreate = true }) { Text("Create community") }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(groups, key = { it.id }) { group ->
                    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(42.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.secondary) } }
                                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(if (group.isPublic) "Public community" else "Private community", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            if (group.description.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(group.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Spacer(Modifier.height(12.dp))
                            if (joinedIds.contains(group.id)) AssistChip(onClick = {}, label = { Text("Joined") }) else Button(onClick = { val uid = client.auth.currentUserOrNull()?.id ?: return@Button; scope.launch { runCatching { client.from("group_members").insert(MemberRow(group.id, uid)); refresh() }.onFailure { error = it.message ?: "Unable to join group." } } }) { Text("Join community") }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) AlertDialog(onDismissRequest = { if (!saving) showCreate = false }, title = { Text("Create community") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Community name") }, singleLine = true); OutlinedTextField(description, { description = it }, label = { Text("What is it about?") }, minLines = 3) } }, confirmButton = { Button(enabled = name.isNotBlank() && !saving, onClick = { val uid = client.auth.currentUserOrNull()?.id ?: return@Button; saving = true; scope.launch { runCatching { client.from("groups").insert(CreateGroupRow(uid, name.trim(), description.trim(), true)); name = ""; description = ""; showCreate = false; refresh() }.onFailure { error = it.message ?: "Unable to create group." }; saving = false } }) { Text(if (saving) "Creating…" else "Create") } }, dismissButton = { TextButton(enabled = !saving, onClick = { showCreate = false }) { Text("Cancel") } })
}
