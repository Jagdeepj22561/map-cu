package com.example.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
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
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
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
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    community.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Community",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                onClick = {
                                    showMenu = false
                                    scope.launch { loadMessages() }
                                }
                            )
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth().imePadding(),
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { }) {
                            Icon(
                                Icons.Default.Add,
                                "Attachment",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        TextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Message...") },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
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
                                        client.from("group_messages").insert(
                                            CreateGroupMessageRow(community.id, uid, text)
                                        )
                                        input = ""
                                        loadMessages()
                                    }.onFailure { error = it.message ?: "Unable to send message." }
                                    sending = false
                                }
                            },
                            modifier = Modifier.size(46.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null)
                        }
                    }
                }
            }
        ) { paddingValues ->
            when {
                loading -> Box(
                    Modifier.fillMaxSize().padding(paddingValues),
                    Alignment.Center
                ) { CircularProgressIndicator() }

                error != null -> Column(
                    Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { scope.launch { loadMessages() } }) { Text("Retry") }
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    var previousDate: String? = null
                    items(messages, key = { it.id }) { message ->
                        val date = communityDateLabel(message.createdAt)
                        if (date != previousDate) {
                            DateHeader(date)
                            previousDate = date
                        }

                        val isMe = message.senderId == myUid
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = if (isMe) {
                                    RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                                } else {
                                    RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                                }
                            ) {
                                Column(
                                    Modifier.widthIn(max = 300.dp).padding(horizontal = 13.dp, vertical = 9.dp)
                                ) {
                                    if (!isMe) {
                                        Text(
                                            "Member",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    Text(message.content, style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            communityTimeLabel(message.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                                        )
                                        if (isMe) {
                                            Spacer(Modifier.width(4.dp))
                                            Text("✓", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (messages.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), Alignment.Center) {
                                Text(
                                    "No messages yet. Start the conversation!",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun communityDateLabel(value: String): String {
    if (value.isBlank()) return "Today"
    val date = value.substringBefore('T')
    if (date.length != 10) return "Today"
    val parts = date.split('-')
    if (parts.size != 3) return "Today"
    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val month = parts[1].toIntOrNull()?.minus(1)
    return if (month != null && month in months.indices) {
        "${months[month]} ${parts[2].toIntOrNull() ?: parts[2]}, ${parts[0]}"
    } else "Today"
}

private fun communityTimeLabel(value: String): String {
    val time = value.substringAfter('T', "").substringBefore('.')
    if (time.length < 5) return ""
    val hour = time.substring(0, 2).toIntOrNull() ?: return ""
    val minute = time.substring(3, 5)
    val suffix = if (hour >= 12) "PM" else "AM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%s %s".format(displayHour, minute, suffix)
}
