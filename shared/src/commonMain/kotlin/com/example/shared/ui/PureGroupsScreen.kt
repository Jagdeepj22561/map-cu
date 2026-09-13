package com.example.shared.ui

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.shared.data.CommunityCache
import com.example.shared.data.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class GroupRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val description: String = "",
    @SerialName("public_group_id") val publicGroupId: String? = null,
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("is_public") val isPublic: Boolean = true
)

@Serializable private data class CreateGroupRow(
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val description: String,
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("is_public") val isPublic: Boolean = true
)

@Serializable private data class MemberRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    val role: String = "member"
)

@Serializable private data class MemberProfileRow(
    @SerialName("id") val uid: String,
    val name: String,
    @SerialName("profile_pic_url") val profilePicUrl: String? = null
)

@Serializable data class GroupMember(
    val userId: String,
    val name: String,
    val profilePicUrl: String? = null,
    val role: String = "member"
)

@Serializable data class GroupMessageRow(
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

fun getCommunityInviteCode(community: GroupRow): String {
    return community.inviteCode?.takeIf { it.isNotBlank() }
        ?: community.publicGroupId?.takeIf { it.isNotBlank() }
        ?: community.id.replace("-", "").take(8).uppercase()
}

fun getCommunityInviteLink(community: GroupRow): String {
    return "maps123://community/${getCommunityInviteCode(community)}"
}

fun parseCommunityJoinInput(raw: String): String {
    val trimmed = raw.trim()
    return when {
        trimmed.contains("/community/") -> trimmed.substringAfter("/community/").substringBefore("?").substringBefore("/").trim()
        trimmed.contains("code=") -> trimmed.substringAfter("code=").substringBefore("&").trim()
        else -> trimmed
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureGroupsScreen(
    onMessagePrivately: (senderUid: String, senderName: String) -> Unit = { _, _ -> }
) {
    val client = remember { SupabaseClientProvider.client }
    val myUid = client.auth.currentUserOrNull()?.id
    val cachedData = remember { CommunityCache.getCommunities() }
    var groups by remember { mutableStateOf<List<GroupRow>>(cachedData.first ?: emptyList()) }
    var joinedIds by remember { mutableStateOf<Set<String>>(cachedData.second ?: emptySet()) }
    var loading by remember { mutableStateOf(cachedData.first == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showJoinByCodeDialog by remember { mutableStateOf(false) }
    var selectedCommunity by remember { mutableStateOf<GroupRow?>(null) }
    var shareInviteCommunity by remember { mutableStateOf<GroupRow?>(null) }
    var communitySearchQuery by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: All, 1: My Communities, 2: Explore

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublicGroup by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    var joinInputText by remember { mutableStateOf("") }
    var joinLoading by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    suspend fun refresh(isManual: Boolean = false) {
        if (isManual || groups.isEmpty()) {
            loading = true
        }
        error = null
        runCatching {
            val fetchedGroups = client.from("groups").select {
                order("created_at", Order.DESCENDING)
                limit(100)
            }.decodeList<GroupRow>()
            val uid = client.auth.currentUserOrNull()?.id
            val fetchedJoined = if (uid == null) emptySet() else {
                client.from("group_members").select {
                    filter { eq("user_id", uid) }
                }.decodeList<MemberRow>().map { it.groupId }.toSet()
            }
            groups = fetchedGroups
            joinedIds = fetchedJoined
            CommunityCache.saveCommunities(fetchedGroups, fetchedJoined)
        }.onFailure {
            if (groups.isEmpty()) {
                error = it.message ?: "Unable to load communities."
            }
        }
        loading = false
    }

    suspend fun joinCommunity(community: GroupRow) {
        val uid = client.auth.currentUserOrNull()?.id ?: return
        runCatching {
            client.from("group_members").insert(MemberRow(community.id, uid, "member"))
            val updatedJoined = joinedIds + community.id
            joinedIds = updatedJoined
            CommunityCache.saveCommunities(groups, updatedJoined)
            selectedCommunity = community
        }.onFailure { error = it.message ?: "Unable to join community." }
    }

    suspend fun handleJoinByCodeOrLink(input: String) {
        val uid = client.auth.currentUserOrNull()?.id ?: return
        val cleanInput = parseCommunityJoinInput(input)
        if (cleanInput.isBlank()) {
            joinError = "Please enter a valid link or join code."
            return
        }
        joinLoading = true
        joinError = null
        runCatching {
            // First check if already in loaded list
            var target = groups.firstOrNull {
                it.id.equals(cleanInput, ignoreCase = true) ||
                it.inviteCode?.equals(cleanInput, ignoreCase = true) == true ||
                it.publicGroupId?.equals(cleanInput, ignoreCase = true) == true ||
                getCommunityInviteCode(it).equals(cleanInput, ignoreCase = true)
            }

            if (target == null) {
                // Query database
                val fetched = client.from("groups").select {
                    filter {
                        or {
                            eq("id", cleanInput)
                            eq("invite_code", cleanInput)
                            eq("public_group_id", cleanInput)
                        }
                    }
                    limit(1)
                }.decodeList<GroupRow>()
                target = fetched.firstOrNull()
            }

            if (target == null) {
                joinError = "Community not found. Check the code or link and try again."
                joinLoading = false
                return
            }

            // If found, join if not joined
            if (!joinedIds.contains(target.id)) {
                client.from("group_members").insert(MemberRow(target.id, uid, "member"))
                joinedIds = joinedIds + target.id
            }

            if (groups.none { it.id == target.id }) {
                groups = listOf(target) + groups
            }
            CommunityCache.saveCommunities(groups, joinedIds)
            showJoinByCodeDialog = false
            joinInputText = ""
            selectedCommunity = target
        }.onFailure {
            joinError = it.message ?: "Failed to join community."
        }
        joinLoading = false
    }

    LaunchedEffect(Unit) { refresh() }

    // Filter communities
    val filteredGroups = remember(groups, joinedIds, selectedFilterIndex, communitySearchQuery) {
        groups.filter { community ->
            val matchesFilter = when (selectedFilterIndex) {
                1 -> joinedIds.contains(community.id)
                2 -> !joinedIds.contains(community.id)
                else -> true
            }
            val matchesSearch = communitySearchQuery.isBlank() ||
                community.name.contains(communitySearchQuery, ignoreCase = true) ||
                community.description.contains(communitySearchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    val myCommunitiesCount = remember(groups, joinedIds) { groups.count { joinedIds.contains(it.id) } }
    val exploreCommunitiesCount = remember(groups, joinedIds) { groups.count { !joinedIds.contains(it.id) } }

    Column(Modifier.fillMaxSize()) {
        // Modern Top Header
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            Modifier.size(42.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Communities", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Connect, collaborate & learn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(
                            onClick = {
                                joinError = null
                                joinInputText = ""
                                showJoinByCodeDialog = true
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Link, "Join with Code", modifier = Modifier.size(20.dp))
                        }
                        FilledIconButton(
                            onClick = { showCreate = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Add, "Create Community", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = communitySearchQuery,
                    onValueChange = { communitySearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search communities…", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (communitySearchQuery.isNotBlank()) {
                            IconButton(onClick = { communitySearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                Spacer(Modifier.height(8.dp))

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilterIndex == 0,
                        onClick = { selectedFilterIndex = 0 },
                        label = { Text("All (${groups.size})") }
                    )
                    FilterChip(
                        selected = selectedFilterIndex == 1,
                        onClick = { selectedFilterIndex = 1 },
                        label = { Text("My Communities ($myCommunitiesCount)") }
                    )
                    FilterChip(
                        selected = selectedFilterIndex == 2,
                        onClick = { selectedFilterIndex = 2 },
                        label = { Text("Explore ($exploreCommunitiesCount)") }
                    )
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
                Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { scope.launch { refresh(isManual = true) } }) { Text("Retry") }
            }
            filteredGroups.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    Modifier.size(72.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (communitySearchQuery.isNotBlank()) "No communities match \"$communitySearchQuery\""
                    else if (selectedFilterIndex == 1) "You haven't joined any communities yet"
                    else "No communities found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (selectedFilterIndex == 1) "Explore and join public communities or create your own."
                    else "Start a community for your course, batch, club or dorm.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        joinError = null
                        joinInputText = ""
                        showJoinByCodeDialog = true
                    }) {
                        Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Join via Code")
                    }
                    Button(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create")
                    }
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredGroups, key = { it.id }) { community ->
                    val isJoined = joinedIds.contains(community.id)
                    val isOwner = community.ownerId == myUid

                    ElevatedCard(
                        onClick = {
                            if (isJoined) selectedCommunity = community
                            else scope.launch { joinCommunity(community) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    Modifier.size(46.dp),
                                    shape = CircleShape,
                                    color = if (isOwner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            community.name.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOwner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            community.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isOwner) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text("👑 Owner", style = MaterialTheme.typography.labelSmall) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                                    labelColor = MaterialTheme.colorScheme.primary
                                                ),
                                                border = null,
                                                modifier = Modifier.height(24.dp)
                                            )
                                        } else if (isJoined) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text("✓ Joined", style = MaterialTheme.typography.labelSmall) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                                    labelColor = MaterialTheme.colorScheme.secondary
                                                ),
                                                border = null,
                                                modifier = Modifier.height(24.dp)
                                            )
                                        }

                                        SuggestionChip(
                                            onClick = {},
                                            label = {
                                                Text(
                                                    if (community.isPublic) "Public" else "Private",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            border = null,
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }
                                }
                            }

                            if (community.description.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    community.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Tag,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        getCommunityInviteCode(community),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedIconButton(
                                        onClick = { shareInviteCommunity = community },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Share, "Share Invite Link", modifier = Modifier.size(18.dp))
                                    }

                                    if (isJoined) {
                                        FilledTonalButton(
                                            onClick = { selectedCommunity = community },
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Text("Open Chat", style = MaterialTheme.typography.labelLarge)
                                        }
                                    } else {
                                        Button(
                                            onClick = { scope.launch { joinCommunity(community) } },
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Join", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Community Dialog
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { if (!saving) showCreate = false },
            title = { Text("Create Community") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        name,
                        { name = it },
                        label = { Text("Community Name") },
                        placeholder = { Text("e.g. AI & Robotics Club, CS Batch 2026") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        description,
                        { description = it },
                        label = { Text("Description") },
                        placeholder = { Text("What is this community about?") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Switch(
                            checked = isPublicGroup,
                            onCheckedChange = { isPublicGroup = it }
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (isPublicGroup) "Public Community" else "Private Community",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (isPublicGroup) "Anyone on campus can find and join" else "Only accessible via invite link or code",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                                val generatedCode = "C-" + ((100000..999999).random().toString())
                                val created = client.from("groups").insert(
                                    CreateGroupRow(
                                        ownerId = uid,
                                        name = name.trim(),
                                        description = description.trim(),
                                        inviteCode = generatedCode,
                                        isPublic = isPublicGroup
                                    )
                                ) {
                                    select()
                                }.decodeSingle<GroupRow>()

                                // Add creator as owner in group_members
                                runCatching {
                                    client.from("group_members").insert(MemberRow(created.id, uid, "owner"))
                                }

                                val updatedGroups = listOf(created) + groups
                                val updatedJoined = joinedIds + created.id
                                groups = updatedGroups
                                joinedIds = updatedJoined
                                CommunityCache.saveCommunities(updatedGroups, updatedJoined)

                                name = ""
                                description = ""
                                isPublicGroup = true
                                showCreate = false
                                selectedCommunity = created
                            }.onFailure { error = it.message ?: "Unable to create community." }
                            saving = false
                        }
                    }
                ) { Text(if (saving) "Creating…" else "Create Community") }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }

    // Join with Code / Link Dialog
    if (showJoinByCodeDialog) {
        AlertDialog(
            onDismissRequest = { if (!joinLoading) showJoinByCodeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Join with Link or Code")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Paste an invite link or enter a 6-8 digit community code to join directly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = joinInputText,
                        onValueChange = {
                            joinInputText = it
                            joinError = null
                        },
                        label = { Text("Invite Link or Code") },
                        placeholder = { Text("e.g. C-123456 or maps123://community/...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (joinInputText.isBlank()) {
                                TextButton(onClick = {
                                    val clip = clipboard.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        joinInputText = clip
                                    }
                                }) { Text("Paste") }
                            } else {
                                IconButton(onClick = { joinInputText = "" }) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        }
                    )
                    if (joinError != null) {
                        Text(joinError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = joinInputText.isNotBlank() && !joinLoading,
                    onClick = {
                        scope.launch { handleJoinByCodeOrLink(joinInputText) }
                    }
                ) {
                    if (joinLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Join Community")
                }
            },
            dismissButton = {
                TextButton(enabled = !joinLoading, onClick = { showJoinByCodeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Share / Invite Modal
    shareInviteCommunity?.let { community ->
        CommunityInviteDialog(
            community = community,
            onDismiss = { shareInviteCommunity = null }
        )
    }

    // Community Chat Dialog
    selectedCommunity?.let { community ->
        CommunityChatDialog(
            community = community,
            client = client,
            onDismiss = { selectedCommunity = null },
            onCommunityDeleted = { deletedId ->
                groups = groups.filterNot { it.id == deletedId }
                joinedIds = joinedIds - deletedId
                CommunityCache.removeCommunity(deletedId)
                selectedCommunity = null
            },
            onCommunityLeft = { leftId ->
                joinedIds = joinedIds - leftId
                CommunityCache.saveCommunities(groups, joinedIds)
                selectedCommunity = null
            },
            onMessagePrivately = onMessagePrivately
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityInviteDialog(
    community: GroupRow,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copiedNotice by remember { mutableStateOf<String?>(null) }
    val inviteCode = remember(community) { getCommunityInviteCode(community) }
    val inviteLink = remember(community) { getCommunityInviteLink(community) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Invite Friends", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Share this link or code with campus friends to join \"${community.name}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // Join Code Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Join Code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(inviteCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    FilledTonalButton(onClick = {
                        clipboard.setText(AnnotatedString(inviteCode))
                        copiedNotice = "Code copied to clipboard!"
                    }) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Code")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Invite Link Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Shareable Link", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            inviteLink,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(inviteLink))
                        copiedNotice = "Invite link copied to clipboard!"
                    }) {
                        Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Link")
                    }
                }
            }

            if (copiedNotice != null) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(copiedNotice!!, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CommunityChatDialog(
    community: GroupRow,
    client: io.github.jan.supabase.SupabaseClient,
    onDismiss: () -> Unit,
    onCommunityDeleted: (String) -> Unit = {},
    onCommunityLeft: (String) -> Unit = {},
    onMessagePrivately: (senderUid: String, senderName: String) -> Unit = { _, _ -> }
) {
    val cachedMessages = remember(community.id) { CommunityCache.getMessages(community.id) }
    var messages by remember(community.id) { mutableStateOf<List<GroupMessageRow>>(cachedMessages ?: emptyList()) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(cachedMessages == null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val cachedMembers = remember(community.id) { CommunityCache.getMembers(community.id) }
    var members by remember(community.id) { mutableStateOf<List<GroupMember>>(cachedMembers ?: emptyList()) }
    var showMembers by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showAddMemberSheet by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var actionSheetMessage by remember { mutableStateOf<GroupMessageRow?>(null) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<GroupMessageRow?>(null) }

    var memberToRemove by remember { mutableStateOf<GroupMember?>(null) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteCommunityConfirm by remember { mutableStateOf(false) }
    var isDeletingCommunity by remember { mutableStateOf(false) }

    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val myUid = client.auth.currentUserOrNull()?.id

    val myRole = remember(members, myUid, community.ownerId) {
        when {
            community.ownerId == myUid -> "owner"
            members.firstOrNull { it.userId == myUid }?.role == "owner" -> "owner"
            members.firstOrNull { it.userId == myUid }?.role == "admin" -> "admin"
            else -> "member"
        }
    }
    val isOwner = myRole == "owner"
    val isAdmin = isOwner || myRole == "admin"

    val memberNameMap = remember(members) { members.associate { it.userId to it.name } }

    suspend fun loadMembers() {
        runCatching {
            val memberRows = client.from("group_members").select {
                filter { eq("group_id", community.id) }
            }.decodeList<MemberRow>()

            val userIds = memberRows.map { it.userId }
            val profiles = if (userIds.isNotEmpty()) {
                client.from("profiles").select {
                    filter { isIn("id", userIds) }
                }.decodeList<MemberProfileRow>()
            } else emptyList()

            val profileMap = profiles.associateBy { it.uid }
            val sortedMembers = memberRows.map { member ->
                val profile = profileMap[member.userId]
                val role = if (member.userId == community.ownerId) "owner" else member.role
                GroupMember(
                    userId = member.userId,
                    name = profile?.name ?: if (member.userId == myUid) "You" else "Campus Student",
                    profilePicUrl = profile?.profilePicUrl,
                    role = role
                )
            }.sortedWith(
                compareBy<GroupMember> {
                    when (it.role) {
                        "owner" -> 0
                        "admin" -> 1
                        else -> 2
                    }
                }.thenBy { it.name.lowercase() }
            )
            members = sortedMembers
            CommunityCache.saveMembers(community.id, sortedMembers)
        }.onFailure {
            if (members.isEmpty()) {
                error = it.message ?: "Unable to load members."
            }
        }
    }

    suspend fun loadMessages(showLoading: Boolean = messages.isEmpty()) {
        if (showLoading) loading = true
        error = null
        var lastError: String? = null
        repeat(2) { attempt ->
            if (attempt > 0) kotlinx.coroutines.delay(1000L)
            val result = runCatching {
                client.from("group_messages").select {
                    filter { eq("group_id", community.id) }
                    order("created_at", Order.ASCENDING)
                    limit(200)
                }.decodeList<GroupMessageRow>()
            }
            if (result.isSuccess) {
                val fetched = result.getOrDefault(emptyList())
                messages = fetched
                CommunityCache.saveMessages(community.id, fetched)
                loading = false
                return
            }
            lastError = result.exceptionOrNull()?.message ?: "Unable to load community messages."
        }
        if (messages.isEmpty()) {
            error = lastError
        }
        loading = false
    }

    suspend fun removeMemberFromCommunity(target: GroupMember) {
        runCatching {
            client.from("group_members").delete {
                filter {
                    eq("group_id", community.id)
                    eq("user_id", target.userId)
                }
            }
            val updated = members.filterNot { it.userId == target.userId }
            members = updated
            CommunityCache.saveMembers(community.id, updated)
        }.onFailure {
            error = it.message ?: "Failed to remove member."
        }
    }

    suspend fun updateMemberRole(target: GroupMember, newRole: String) {
        runCatching {
            client.from("group_members").update({
                set("role", newRole)
            }) {
                filter {
                    eq("group_id", community.id)
                    eq("user_id", target.userId)
                }
            }
            val updated = members.map {
                if (it.userId == target.userId) it.copy(role = newRole) else it
            }
            members = updated
            CommunityCache.saveMembers(community.id, updated)
        }.onFailure {
            error = it.message ?: "Failed to update member role."
        }
    }

    suspend fun leaveCurrentCommunity() {
        val uid = myUid ?: return
        runCatching {
            client.from("group_members").delete {
                filter {
                    eq("group_id", community.id)
                    eq("user_id", uid)
                }
            }
            onCommunityLeft(community.id)
        }.onFailure {
            error = it.message ?: "Failed to leave community."
        }
    }

    suspend fun deleteCurrentCommunity() {
        isDeletingCommunity = true
        runCatching {
            client.from("groups").delete {
                filter { eq("id", community.id) }
            }
            onCommunityDeleted(community.id)
        }.onFailure {
            error = it.message ?: "Failed to delete community."
        }
        isDeletingCommunity = false
    }

    LaunchedEffect(community.id) {
        loadMessages()
        loadMembers()
    }
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
                                Modifier.size(42.dp),
                                shape = CircleShape,
                                color = if (isOwner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        community.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOwner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    community.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${members.size} members" + if (isOwner) " • 👑 Owner" else if (isAdmin) " • 🛡️ Admin" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        if (searchMode) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                placeholder = { Text("Search messages…") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            IconButton(onClick = {
                                searchMode = false
                                searchQuery = ""
                            }) { Icon(Icons.Default.Close, "Close search") }
                        } else {
                            if (isAdmin) {
                                IconButton(onClick = { showAddMemberSheet = true }) {
                                    Icon(Icons.Default.PersonAdd, "Add Member")
                                }
                            }
                            IconButton(onClick = { showInviteDialog = true }) {
                                Icon(Icons.Default.Share, "Invite")
                            }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, "More options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Community Info") },
                                    leadingIcon = { Icon(Icons.Default.Info, null) },
                                    onClick = {
                                        showMenu = false
                                        showInfo = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Members (${members.size})") },
                                    leadingIcon = { Icon(Icons.Default.Groups, null) },
                                    onClick = {
                                        showMenu = false
                                        showMembers = true
                                    }
                                )
                                if (isAdmin) {
                                    DropdownMenuItem(
                                        text = { Text("Add Member") },
                                        leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                                        onClick = {
                                            showMenu = false
                                            showAddMemberSheet = true
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Invite via Link / Code") },
                                    leadingIcon = { Icon(Icons.Default.Link, null) },
                                    onClick = {
                                        showMenu = false
                                        showInviteDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Search Messages") },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    onClick = {
                                        showMenu = false
                                        searchMode = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                    onClick = {
                                        showMenu = false
                                        scope.launch {
                                            loadMessages()
                                            loadMembers()
                                        }
                                    }
                                )
                                HorizontalDivider()
                                if (isOwner) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Community", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showMenu = false
                                            showDeleteCommunityConfirm = true
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Leave Community", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showMenu = false
                                            showLeaveConfirm = true
                                        }
                                    )
                                }
                            }
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
                    Column {
                        if (editingMessageId != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Editing message",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        editingMessageId = null
                                        input = ""
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        "Cancel edit",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Message #${community.name}") },
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )

                            Spacer(Modifier.width(4.dp))

                            val canSend = input.isNotBlank() && !sending
                            IconButton(
                                onClick = {
                                    val uid = myUid ?: return@IconButton
                                    val text = input.trim()
                                    if (text.isBlank() || sending) return@IconButton
                                    sending = true
                                    scope.launch {
                                        runCatching {
                                            val editId = editingMessageId
                                            if (editId != null) {
                                                client.from("group_messages").update({
                                                    set("content", text)
                                                }) {
                                                    filter { eq("id", editId) }
                                                }
                                                editingMessageId = null
                                            } else {
                                                client.from("group_messages").insert(
                                                    CreateGroupMessageRow(community.id, uid, text)
                                                )
                                            }
                                            input = ""
                                            loadMessages(showLoading = false)
                                        }.onFailure { error = it.message ?: "Unable to send message." }
                                        sending = false
                                    }
                                },
                                enabled = canSend,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (canSend) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    }
                                )
                            }
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
                    Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { scope.launch { loadMessages() } }) { Text("Retry") }
                }

                else -> {
                    val filteredMessages = remember(messages, searchQuery) {
                        if (searchQuery.isBlank()) messages
                        else messages.filter { it.content.contains(searchQuery, ignoreCase = true) }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        var previousDate: String? = null
                        items(filteredMessages, key = { it.id }) { message ->
                            val date = communityDateLabel(message.createdAt)
                            if (date != previousDate) {
                                DateHeader(date)
                                previousDate = date
                            }

                            val isMe = message.senderId == myUid
                            val senderMember = members.firstOrNull { it.userId == message.senderId }
                            val senderName = senderMember?.name ?: memberNameMap[message.senderId] ?: "Member"
                            val senderRole = senderMember?.role ?: if (message.senderId == community.ownerId) "owner" else "member"

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    color = if (isMe) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = if (isMe) {
                                        RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                                    } else {
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                                    },
                                    shadowElevation = if (isMe) 1.dp else 0.dp,
                                    tonalElevation = 0.dp,
                                    modifier = Modifier.combinedClickable(
                                        onClick = { },
                                        onLongClick = { actionSheetMessage = message }
                                    )
                                ) {
                                    Column(
                                        Modifier.widthIn(max = 300.dp).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)
                                    ) {
                                        if (!isMe) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    senderName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                if (senderRole == "owner") {
                                                    Text("👑", fontSize = 10.sp)
                                                } else if (senderRole == "admin") {
                                                    Text("🛡️", fontSize = 10.sp)
                                                }
                                            }
                                            Spacer(Modifier.height(2.dp))
                                        }
                                        Text(
                                            message.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isMe) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End,
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text(
                                                communityTimeLabel(message.createdAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (isMe) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.DoneAll,
                                                    contentDescription = "Sent",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = Color(0xFF4FC3F7)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (filteredMessages.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), Alignment.Center) {
                                    Text(
                                        if (searchQuery.isNotBlank()) "No messages match \"$searchQuery\""
                                        else "No messages yet. Say hello to everyone!",
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

    // Members Bottom Sheet
    if (showMembers) {
        var memberFilterQuery by remember { mutableStateOf("") }
        val displayedMembers = remember(members, memberFilterQuery) {
            if (memberFilterQuery.isBlank()) members
            else members.filter { it.name.contains(memberFilterQuery, ignoreCase = true) }
        }

        ModalBottomSheet(
            onDismissRequest = { showMembers = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Members (${members.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isAdmin) {
                        FilledTonalButton(
                            onClick = {
                                showMembers = false
                                showAddMemberSheet = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Member Search Filter
                OutlinedTextField(
                    value = memberFilterQuery,
                    onValueChange = { memberFilterQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search members…") },
                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (memberFilterQuery.isNotBlank()) {
                            IconButton(onClick = { memberFilterQuery = "" }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedMembers, key = { it.userId }) { member ->
                        var memberMenuExpanded by remember { mutableStateOf(false) }
                        val isSelf = member.userId == myUid
                        val targetIsOwner = member.role == "owner" || member.userId == community.ownerId
                        val targetIsAdmin = member.role == "admin" || targetIsOwner

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    Modifier.size(38.dp),
                                    shape = CircleShape,
                                    color = if (targetIsOwner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            member.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = if (targetIsOwner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            member.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isSelf) {
                                            Text("(You)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    if (targetIsOwner) {
                                        Text(
                                            "👑 Owner",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else if (member.role == "admin") {
                                        Text(
                                            "🛡️ Admin",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(
                                            "Member",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Actions menu
                                Box {
                                    IconButton(onClick = { memberMenuExpanded = true }) {
                                        Icon(Icons.Default.MoreVert, "Member Actions", modifier = Modifier.size(18.dp))
                                    }

                                    DropdownMenu(
                                        expanded = memberMenuExpanded,
                                        onDismissRequest = { memberMenuExpanded = false }
                                    ) {
                                        if (!isSelf) {
                                            DropdownMenuItem(
                                                text = { Text("Message Privately") },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                                                onClick = {
                                                    memberMenuExpanded = false
                                                    showMembers = false
                                                    onMessagePrivately(member.userId, member.name)
                                                }
                                            )
                                        }

                                        // Owner actions
                                        if (isOwner && !isSelf) {
                                            if (member.role == "admin") {
                                                DropdownMenuItem(
                                                    text = { Text("Dismiss as Admin") },
                                                    leadingIcon = { Icon(Icons.Default.Shield, null) },
                                                    onClick = {
                                                        memberMenuExpanded = false
                                                        scope.launch { updateMemberRole(member, "member") }
                                                    }
                                                )
                                            } else {
                                                DropdownMenuItem(
                                                    text = { Text("Promote to Admin") },
                                                    leadingIcon = { Icon(Icons.Default.Shield, null) },
                                                    onClick = {
                                                        memberMenuExpanded = false
                                                        scope.launch { updateMemberRole(member, "admin") }
                                                    }
                                                )
                                            }

                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text("Remove from Community", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    memberMenuExpanded = false
                                                    memberToRemove = member
                                                }
                                            )
                                        } else if (isAdmin && !targetIsAdmin && !isSelf) {
                                            // Admin can remove regular members
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text("Remove from Community", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    memberMenuExpanded = false
                                                    memberToRemove = member
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Member Bottom Sheet (Admin / Owner)
    if (showAddMemberSheet) {
        var userSearchQuery by remember { mutableStateOf("") }
        var isSearchingUsers by remember { mutableStateOf(false) }
        var searchResults by remember { mutableStateOf<List<MemberProfileRow>>(emptyList()) }
        var addingUid by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(userSearchQuery) {
            val query = userSearchQuery.trim()
            if (query.length < 2) {
                searchResults = emptyList()
                return@LaunchedEffect
            }
            isSearchingUsers = true
            runCatching {
                val currentMemberIds = members.map { it.userId }.toSet()
                val fetched = client.from("profiles").select {
                    filter {
                        ilike("name", "%$query%")
                    }
                    limit(20)
                }.decodeList<MemberProfileRow>()
                searchResults = fetched.filterNot { currentMemberIds.contains(it.uid) }
            }
            isSearchingUsers = false
        }

        ModalBottomSheet(
            onDismissRequest = { showAddMemberSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Add Members", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Search campus students to invite", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = userSearchQuery,
                    onValueChange = { userSearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by name…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (userSearchQuery.isNotBlank()) {
                            IconButton(onClick = { userSearchQuery = "" }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(12.dp))

                when {
                    isSearchingUsers -> Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                    searchResults.isEmpty() && userSearchQuery.trim().length >= 2 -> Box(
                        Modifier.fillMaxWidth().height(100.dp),
                        Alignment.Center
                    ) {
                        Text("No eligible users found matching \"$userSearchQuery\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    searchResults.isEmpty() -> Box(
                        Modifier.fillMaxWidth().height(100.dp),
                        Alignment.Center
                    ) {
                        Text("Type at least 2 characters to search students", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults, key = { it.uid }) { user ->
                            val isAdding = addingUid == user.uid
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Surface(
                                            Modifier.size(36.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(user.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(user.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    }

                                    Button(
                                        enabled = !isAdding,
                                        onClick = {
                                            addingUid = user.uid
                                            scope.launch {
                                                runCatching {
                                                    client.from("group_members").insert(MemberRow(community.id, user.uid, "member"))
                                                    loadMembers()
                                                    searchResults = searchResults.filterNot { it.uid == user.uid }
                                                }
                                                addingUid = null
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        if (isAdding) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        else Text("Add")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Invite Modal Sheet
    if (showInviteDialog) {
        CommunityInviteDialog(
            community = community,
            onDismiss = { showInviteDialog = false }
        )
    }

    // Community Info Dialog
    if (showInfo) {
        val adminMembers = members.filter { it.role == "admin" || it.role == "owner" || it.userId == community.ownerId }
        AlertDialog(
            onDismissRequest = { showInfo = false },
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
                    Text(community.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (community.description.isNotBlank()) {
                        Column {
                            Text("Description", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            Text(community.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Column {
                        Text("Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(if (community.isPublic) "Public community" else "Private community", style = MaterialTheme.typography.bodyMedium)
                    }
                    Column {
                        Text("Members", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text("${members.size} members", style = MaterialTheme.typography.bodyMedium)
                    }
                    Column {
                        Text("Join Code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(getCommunityInviteCode(community), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    if (adminMembers.isNotEmpty()) {
                        Column {
                            Text("Leadership", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            adminMembers.forEach { admin ->
                                val badge = if (admin.userId == community.ownerId) "👑 Owner" else "🛡️ Admin"
                                Text(
                                    admin.name + " (" + badge + if (admin.userId == myUid) ", You" else "" + ")",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("Close") }
            }
        )
    }

    // Confirm Remove Member Dialog
    memberToRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Remove Member") },
            text = { Text("Are you sure you want to remove \"${target.name}\" from ${community.name}? They will lose access to messages.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toRemove = target
                        memberToRemove = null
                        scope.launch { removeMemberFromCommunity(toRemove) }
                    }
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) { Text("Cancel") }
            }
        )
    }

    // Confirm Leave Community Dialog
    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave Community") },
            text = { Text("Are you sure you want to leave \"${community.name}\"? You will need to rejoin to view and post messages.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirm = false
                        scope.launch { leaveCurrentCommunity() }
                    }
                ) { Text("Leave", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Confirm Delete Community Dialog (Owner)
    if (showDeleteCommunityConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingCommunity) showDeleteCommunityConfirm = false },
            title = { Text("Delete Community") },
            text = { Text("Are you sure you want to delete \"${community.name}\"? This action is permanent and will remove all members and messages.") },
            confirmButton = {
                TextButton(
                    enabled = !isDeletingCommunity,
                    onClick = {
                        scope.launch { deleteCurrentCommunity() }
                    }
                ) { Text(if (isDeletingCommunity) "Deleting…" else "Delete Permanently", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !isDeletingCommunity, onClick = { showDeleteCommunityConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Action Sheet for Message
    actionSheetMessage?.let { message ->
        val isOwnMessage = message.senderId == myUid

        ModalBottomSheet(
            onDismissRequest = { actionSheetMessage = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = message.content.takeIf { it.isNotBlank() } ?: "Message",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(message.content))
                        actionSheetMessage = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(text = "Copy text", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                }

                if (isOwnMessage) {
                    TextButton(
                        onClick = {
                            editingMessageId = message.id
                            input = message.content
                            actionSheetMessage = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(text = "Edit", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    }
                }

                if (isOwnMessage || isAdmin) {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = message
                            actionSheetMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(text = "Delete", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    }
                }

                if (!isOwnMessage) {
                    TextButton(
                        onClick = {
                            val name = memberNameMap[message.senderId] ?: "Member"
                            actionSheetMessage = null
                            onMessagePrivately(message.senderId, name)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(text = "Message privately", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    }
                }
            }
        }
    }

    showDeleteConfirm?.let { message ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete message") },
            text = { Text("This will permanently remove this message.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching {
                            client.from("group_messages").delete {
                                filter { eq("id", message.id) }
                            }
                            messages = messages.filterNot { it.id == message.id }
                            CommunityCache.saveMessages(community.id, messages)
                        }
                        showDeleteConfirm = null
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
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
    return "$displayHour:${minute.padStart(2, '0')} $suffix"
}
