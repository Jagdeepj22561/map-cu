package com.example.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.shared.data.SupabaseClientProvider
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable private data class GroupRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val description: String = "",
    @SerialName("public_group_id") val publicGroupId: String? = null,
    @SerialName("is_public") val isPublic: Boolean = true
)

@Serializable private data class CreateGroupRow(
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val description: String,
    @SerialName("is_public") val isPublic: Boolean
)

@Serializable private data class MemberRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    val role: String = "member"
)

@Serializable private data class GroupMessageRow(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable private data class CreateGroupMessageRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String
)

@Composable
fun PureGroupsScreen() {
    val client = remember { SupabaseClientProvider.client }
    var groups by remember { mutableStateOf<List<GroupRow>>(emptyList()) }
    var joinedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var selectedCommunity by remember { mutableStateOf<GroupRow?>(null) }
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
        }.onFailure { error = it.message ?: "Unable to load communities." }
        loading = false
    }

    suspend fun joinCommunity(community: GroupRow) {
        val uid = client.auth.currentUserOrNull()?.id ?: return
        runCatching {
            client.from("group_members").insert(MemberRow(community.id, uid))
            joinedIds = joinedIds + community.id
            selectedCommunity = community
        }.onFailure { error = it.message ?: "Unable to join community." }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        Modifier.size(46.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Communities", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Find your people on campus", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledIconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, "Create community")
                    }
                }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            error != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { scope.launch { refresh() } }) { Text("Retry") }
            }
            groups.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(Modifier.size(72.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("No communities yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Start a community for your course, club or project.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showCreate = true }) { Text("Create community") }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(groups, key = { it.id }) { community ->
                    ElevatedCard(
                        onClick = {
                            if (joinedIds.contains(community.id)) selectedCommunity = community
                            else scope.launch { joinCommunity(community) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(42.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(community.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (community.isPublic) "Public community" else "Private community",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (community.description.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(community.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(12.dp))
                            if (joinedIds.contains(community.id)) {
                                Button(onClick = { selectedCommunity = community }) {
                                    Text("Open Community")
                                }
                            } else {
                                Button(onClick = { scope.launch { joinCommunity(community) } }) {
                                    Text("Join Community")
                                }
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
            title = { Text("Create community") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Community name") }, singleLine = true)
                    OutlinedTextField(description, { description = it }, label = { Text("What is it about?") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank() && !saving, onClick = {
                    val uid = client.auth.currentUserOrNull()?.id ?: return@Button
                    saving = true
                    scope.launch {
                        runCatching {
                            client.from("groups").insert(CreateGroupRow(uid, name.trim(), description.trim(), true))
                            name = ""
                            description = ""
                            showCreate = false
                            refresh()
                        }.onFailure { error = it.message ?: "Unable to create community." }
                        saving = false
                    }
                }) { Text(if (saving) "Creating…" else "Create") }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }

    selectedCommunity?.let { community ->
        CommunityChatDialog(
            community = community,
            client = client,
            onDismiss = { selectedCommunity = null }
        )
    }
}

@Composable
private fun CommunityChatDialog(
    community: GroupRow,
    client: io.github.jan.supabase.SupabaseClient,
    onDismiss: () -> Unit
) {
    var messages by remember(community.id) { mutableStateOf<List<GroupMessageRow>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val myUid = client.auth.currentUserOrNull()?.id

    suspend fun loadMessages() {
        loading = true
        error = null
        runCatching {
            messages = client.from("group_messages").select {
                filter { eq("group_id", community.id) }
                order("created_at", Order.ASCENDING)
                limit(200)
            }.decodeList<GroupMessageRow>()
        }.onFailure { error = it.message ?: "Unable to load community messages." }
        loading = false
    }

    LaunchedEffect(community.id) { loadMessages() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(community.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Community", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { scope.launch { loadMessages() } }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }

                HorizontalDivider()

                when {
                    loading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() }
                    error != null -> Column(
                        Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { scope.launch { loadMessages() } }) { Text("Retry") }
                    }
                    else -> LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        if (messages.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), Alignment.Center) {
                                    Text("No messages yet. Start the conversation!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        items(messages, key = { it.id }) { message ->
                            val isMe = message.senderId == myUid
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(Modifier.widthIn(max = 300.dp).padding(horizontal = 13.dp, vertical = 9.dp)) {
                                        if (!isMe) {
                                            Text("Member", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(2.dp))
                                        }
                                        Text(message.content, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Message community…") },
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                val uid = myUid ?: return@FloatingActionButton
                                val text = input.trim()
                                if (text.isBlank() || sending) return@FloatingActionButton
                                sending = true
                                scope.launch {
                                    runCatching {
                                        client.from("group_messages").insert(CreateGroupMessageRow(community.id, uid, text))
                                        input = ""
                                        loadMessages()
                                    }.onFailure { error = it.message ?: "Unable to send message." }
                                    sending = false
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send")
                        }
                    }
                }
            }
        }
    }
}
