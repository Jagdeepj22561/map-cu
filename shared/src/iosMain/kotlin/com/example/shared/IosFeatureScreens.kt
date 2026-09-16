package com.example.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.shared.model.Announcement
import com.example.shared.model.PureChat
import com.example.shared.repository.AnnouncementRepository
import com.example.shared.repository.IosChatRepository
import com.example.shared.repository.IosChatRequestState
import com.example.shared.repository.IosPreferencesStore
import com.example.shared.repository.IosUserProfile
import com.example.shared.repository.IosUserStore
import com.example.shared.repository.currentTimeMillis
import com.example.shared.ui.PureAnnouncementCard
import com.example.shared.ui.PureAnnouncementDetailScreen
import com.example.shared.ui.PureAvatarPlaceholder
import com.example.shared.ui.PureChatDetailScreen
import com.example.shared.ui.PureProfileScreen
import com.example.shared.ui.PureReportDialog
import com.example.shared.ui.PureSharePostDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
internal fun IosChatDetailContent(
    chat: PureChat,
    repository: IosChatRepository,
    onBack: () -> Unit,
    onProfileClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val myUid = repository.getCurrentUserUid() ?: ""
    val messages by repository.messagesFlow(chat.chatId).collectAsState(initial = emptyList())
    var isBlocked by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var requestState by remember { mutableStateOf<IosChatRequestState?>(null) }
    var requestActionBusy by remember { mutableStateOf(false) }
    var isFriendUser by remember { mutableStateOf(false) }
    var requestStateLoading by remember { mutableStateOf(true) }

    LaunchedEffect(chat.chatId) {
        repository.setActiveChat(chat.chatId)
        runCatching { repository.markChatRead(chat.chatId) }
        runCatching { isBlocked = repository.isUserBlocked(chat.friendUid) }
        runCatching {
            isFriendUser = repository.isFriend(chat.friendUid)
            requestState = repository.getChatRequestState(chat.friendUid)
        }
        requestStateLoading = false
    }

    LaunchedEffect(chat.chatId, chat.friendUid) {
        while (true) {
            runCatching {
                val friend = repository.isFriend(chat.friendUid)
                val state = repository.getChatRequestState(chat.friendUid)
                isFriendUser = friend
                requestState = if (friend) null else state
            }
            delay(10_000)
        }
    }

    DisposableEffect(chat.chatId) {
        onDispose {
            repository.setActiveChat(null)
        }
    }

    PureChatDetailScreen(
        friendName = chat.friendName,
        friendProfilePicUrl = chat.friendProfilePicUrl,
        messages = messages,
        myUid = myUid,
        onBack = onBack,
        onProfileClick = { onProfileClick(chat.friendUid) },
        onSendMessage = { content ->
            scope.launch {
                runCatching {
                    repository.sendMessage(chat.chatId, content, chat.friendUid)
                    if (!isFriendUser) requestState = IosChatRequestState("pending", incoming = false)
                }.onFailure { errorMessage = it.message ?: "Failed to send message" }
            }
        },
        onPickImage = {
            errorMessage = "Image upload is not wired to the native iOS picker yet"
        },
        onDeleteMessages = { ids, _ ->
            scope.launch {
                runCatching { repository.deleteMessagesLocally(ids.toList()) }
                    .onFailure { errorMessage = it.message ?: "Failed to delete message" }
            }
        },
        onCopyMessages = { ids ->
            val text = messages.filter { it.messageId in ids }.joinToString("\n") { it.content }
            clipboard.setText(AnnotatedString(text))
        },
        onEditMessage = { messageId, newContent ->
            scope.launch {
                runCatching { repository.editMessage(chat.chatId, messageId, newContent) }
                    .onFailure { errorMessage = it.message ?: "Failed to edit message" }
            }
        },
        onBlockUser = {
            scope.launch {
                runCatching {
                    if (isBlocked) repository.unblockUser(chat.friendUid) else repository.blockUser(chat.friendUid)
                    isBlocked = !isBlocked
                }.onFailure { errorMessage = it.message ?: "Failed to update block status" }
            }
        },
        onClearChat = {
            scope.launch {
                runCatching { repository.clearChat(chat.chatId) }
                    .onFailure { errorMessage = it.message ?: "Failed to clear chat" }
            }
        },
        onDeleteChat = {
            scope.launch {
                runCatching { repository.deleteChat(chat.chatId) }
                    .onSuccess { onBack() }
                    .onFailure { errorMessage = it.message ?: "Failed to delete chat" }
            }
        },
        onReportUser = {},
        isBlocked = isBlocked,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        isSearchActive = isSearchActive,
        onToggleSearch = { isSearchActive = it },
        formatTime = ::iosRelativeTime,
        formatDateHeader = ::iosDateHeader,
        onOpenPostLink = {},
        canEditMessage = { false },
        canDeleteForEveryone = false,
        requestBanner = {
            requestState?.let { state ->
                when {
                    state.incoming && state.status == "pending" -> Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Message request", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "This person wants to start a conversation. Accept to reply, or decline the request.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            Row(Modifier.padding(top = 8.dp)) {
                                Button(
                                    enabled = !requestActionBusy,
                                    onClick = {
                                        requestActionBusy = true
                                        scope.launch {
                                            runCatching { repository.acceptFriendRequest(chat.friendUid) }
                                                .onSuccess { isFriendUser = true; requestState = null }
                                                .onFailure { errorMessage = it.message ?: "Unable to accept request" }
                                            requestActionBusy = false
                                        }
                                    }
                                ) { Text("Accept") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    enabled = !requestActionBusy,
                                    onClick = {
                                        requestActionBusy = true
                                        scope.launch {
                                            runCatching { repository.rejectFriendRequest(chat.friendUid) }
                                                .onSuccess { requestState = IosChatRequestState("rejected", incoming = false) }
                                                .onFailure { errorMessage = it.message ?: "Unable to decline request" }
                                            requestActionBusy = false
                                        }
                                    }
                                ) { Text("Decline") }
                            }
                        }
                    }
                    state.status == "rejected" -> Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Message request declined. You cannot send messages in this chat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    !state.incoming && state.status == "pending" -> Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Message request sent. You can send one message until it is accepted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        canSendMessages = !requestStateLoading && (isFriendUser || requestState == null),
        renderImage = { url, modifier, scale ->
            IosRemoteImage(url, modifier, scale)
        }
    )

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("Close") } },
            title = { Text("Chat") },
            text = { Text(message) }
        )
    }
}

/* Retired iOS-only Firestore group chat UI. PureGroupsScreen is the shared
   Supabase implementation used by both Android and iOS.
@Composable
internal fun IosGroupChatDetailContent(
    group: PureGroup,
    repository: IosChatRepository,
    onBack: () -> Unit,
    onInfoClick: () -> Unit,
    onOpenGroupLink: (GroupLinkPayload) -> Unit,
    onOpenPost: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val myUid = repository.getCurrentUserUid()
    val messages by repository.groupMessagesFlow(group.groupId).collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(group.groupId) {
        repository.setActiveGroup(group.groupId)
        runCatching { repository.markGroupRead(group.groupId) }
    }

    DisposableEffect(group.groupId) {
        onDispose {
            repository.setActiveGroup(null)
        }
    }

    PureGroupChatDetailScreen(
        groupName = group.name,
        groupIconUrl = group.groupIconUrl,
        messages = messages,
        myUid = myUid,
        onBack = onBack,
        onInfoClick = onInfoClick,
        onSendMessage = { content ->
            scope.launch {
                runCatching { repository.sendGroupMessage(group.groupId, content) }
                    .onFailure { errorMessage = it.message ?: "Failed to send message" }
            }
        },
        onPickImage = {
            errorMessage = "Group image upload is not wired to the native iOS picker yet"
        },
        onDeleteMessages = { ids, isForEveryone ->
            scope.launch {
                runCatching {
                    if (isForEveryone) repository.deleteGroupMessagesForEveryone(group.groupId, ids.toList())
                    else repository.deleteGroupMessagesLocally(ids.toList())
                }.onFailure { errorMessage = it.message ?: "Failed to delete group message" }
            }
        },
        onCopyMessages = { ids ->
            val text = messages.filter { it.messageId in ids }.joinToString("\n") { it.content }
            clipboard.setText(AnnotatedString(text))
        },
        onEditMessage = { messageId, newContent ->
            scope.launch {
                runCatching { repository.editGroupMessage(group.groupId, messageId, newContent) }
                    .onFailure { errorMessage = it.message ?: "Failed to edit message" }
            }
        },
        onClearChat = {
            scope.launch {
                runCatching { repository.clearGroupChat(group.groupId) }
                    .onFailure { errorMessage = it.message ?: "Failed to clear group" }
            }
        },
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        isSearchActive = isSearchActive,
        onToggleSearch = { isSearchActive = it },
        formatTime = ::iosRelativeTime,
        formatDateHeader = ::iosDateHeader,
        onOpenPostLink = onOpenPost,
        onOpenGroupLink = onOpenGroupLink,
        renderImage = { url, modifier, scale ->
            IosRemoteImage(url, modifier, scale)
        }
    )

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("Close") } },
            title = { Text("Group") },
            text = { Text(message) }
        )
    }
}

*/
@Composable
internal fun IosAnnouncementDetailContent(
    announcementId: String,
    repository: AnnouncementRepository,
    currentUser: IosUserProfile?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val announcement by repository.getAnnouncementFlow(announcementId).collectAsState(initial = null)
    var showReportDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(announcementId) {
        errorMessage = null
        IosInAppDebugLogStore.log("Opening post detail for $announcementId")
        runCatching { repository.incrementViewCount(announcementId) }
    }

    LaunchedEffect(announcementId, announcement) {
        if (announcement != null) return@LaunchedEffect
        delay(2000)
        if (announcement == null && errorMessage == null) {
            errorMessage = "Post not found or unavailable"
            IosInAppDebugLogStore.log("Post detail unavailable for $announcementId")
        }
    }

    PureAnnouncementDetailScreen(
        announcement = announcement,
        isLoading = announcement == null && errorMessage == null,
        errorMessage = errorMessage,
        currentUid = currentUser?.uid,
        onBack = onBack,
        onShareClick = {
            announcement?.id?.let { clipboard.setText(AnnotatedString("maps123://post/$it")) }
        },
        onCopyIdClick = {
            announcement?.id?.let { clipboard.setText(AnnotatedString(it)) }
        },
        onContactAuthor = {},
        onReport = { showReportDialog = true },
        onDelete = { showDeleteConfirm = true },
        formatTime = ::iosRelativeTime,
        renderImage = { url, modifier, scale ->
            IosRemoteImage(url, modifier, scale)
        }
    )

    if (showReportDialog && announcement != null && currentUser != null) {
        PureReportDialog(
            onDismiss = { showReportDialog = false },
            onReport = { reason ->
                scope.launch {
                    runCatching {
                        repository.reportAnnouncement(announcement!!.id, currentUser.uid, reason)
                    }.onFailure { errorMessage = it.message ?: "Failed to submit report" }
                    showReportDialog = false
                }
            }
        )
    }

    if (showShareDialog && announcement != null) {
        val link = "maps123://post/${announcement!!.id}"
        PureSharePostDialog(
            link = link,
            onDismiss = { showShareDialog = false },
            onCopy = {
                clipboard.setText(AnnotatedString(link))
            },
            onShareViaApp = {
                clipboard.setText(AnnotatedString(link))
                showShareDialog = false
            }
        )
    }

    if (showDeleteConfirm && announcement != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching { repository.deleteAnnouncement(announcement!!.id) }
                                .onSuccess { onBack() }
                                .onFailure { errorMessage = it.message ?: "Failed to delete post" }
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
            title = { Text("Delete post?") },
            text = { Text("This cannot be undone.") }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun IosDebugLogContent(
    onBack: () -> Unit
) {
    val entries by IosInAppDebugLogStore.entries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { IosInAppDebugLogStore.clear() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear")
                    }
                }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("No log entries yet")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries.reversed()) { entry ->
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Text(
                            text = entry,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun IosProfileContent(
    profile: IosUserProfile,
    currentUserUid: String?,
    announcements: List<Announcement>,
    readOnly: Boolean,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit = {},
    onProfileSaved: (IosUserProfile) -> Unit
) {
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var name by remember(profile.uid, profile.lastUpdated) { mutableStateOf(profile.name) }
    var phoneNumber by remember(profile.uid, profile.lastUpdated) { mutableStateOf(profile.phoneNumber) }
    var year by remember(profile.uid, profile.lastUpdated) { mutableStateOf(profile.year) }
    var semester by remember(profile.uid, profile.lastUpdated) { mutableStateOf(profile.semester) }
    var course by remember(profile.uid, profile.lastUpdated) { mutableStateOf(profile.course) }
    var dob by remember(profile.uid, profile.lastUpdated) { mutableStateOf(profile.dob) }
    var instagram by remember(profile.uid, profile.lastUpdated) { mutableStateOf(profile.instagramLink) }
    var snapchat by remember(profile.uid, profile.lastUpdated) { mutableStateOf(profile.snapchatLink) }
    var linkedin by remember(profile.uid, profile.lastUpdated) { mutableStateOf(profile.linkedinLink) }
    val posts = remember(announcements, profile.uid) { announcements.filter { it.authorUid == profile.uid } }

    PureProfileScreen(
        isReadOnly = readOnly,
        isLoading = false,
        isBlocked = false,
        isEditing = editing,
        onToggleEditing = { editing = it },
        onBack = onBack,
        onSave = {
            if (readOnly) return@PureProfileScreen
            scope.launch {
                val updated = profile.copy(
                    name = name,
                    phoneNumber = phoneNumber,
                    year = year,
                    semester = semester,
                    course = course,
                    dob = dob,
                    instagramLink = instagram,
                    snapchatLink = snapchat,
                    linkedinLink = linkedin
                )
                runCatching {
                    IosUserStore.updateUserProfile(updated)
                    onProfileSaved(updated)
                    editing = false
                }.onFailure { errorMessage = it.message ?: "Failed to save profile" }
            }
        },
        onImageClick = {
            errorMessage = "Profile image upload is not wired to the native iOS picker yet"
        },
        name = name,
        onNameChange = { name = it },
        email = profile.email,
        profilePicUrl = profile.profilePicUrl,
        course = course,
        onCourseChange = { course = it },
        year = year,
        onYearChange = { year = it },
        semester = semester,
        onSemesterChange = { semester = it },
        dob = dob,
        onDobChange = { dob = it },
        phoneNumber = phoneNumber,
        onPhoneNumberChange = { phoneNumber = it },
        instagramLink = instagram,
        onInstagramChange = { instagram = it },
        snapchatLink = snapchat,
        onSnapchatChange = { snapchat = it },
        linkedinLink = linkedin,
        onLinkedinChange = { linkedin = it },
        onSocialClick = { _, _ -> },
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        tabs = listOf("Details", "Posts"),
        renderPosts = {
            if (posts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No posts yet")
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    posts.forEach { announcement ->
                        PureAnnouncementCard(
                            announcement = announcement,
                            currentUid = currentUserUid,
                            onReport = {},
                            onShare = {},
                            onDelete = {},
                            onClick = { onOpenPost(announcement.id) },
                            formatTime = ::iosRelativeTime,
                            renderImage = { url, modifier, scale ->
                                IosRemoteImage(url, modifier, scale)
                            }
                        )
                    }
                }
            }
        },
        renderImage = { url, modifier ->
            IosRemoteImage(url, modifier)
        }
    )

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("Close") } },
            title = { Text("Profile") },
            text = { Text(message) }
        )
    }
}

/* Retired iOS-only Firestore group info UI; see PureGroupsScreen.
@Composable
internal fun IosGroupInfoContent(
    group: PureGroup,
    repository: IosChatRepository,
    joinPreview: Boolean = false,
    linkPayload: GroupLinkPayload? = null,
    onBack: () -> Unit,
    onExitGroup: () -> Unit,
    onJoinedGroup: (PureGroup) -> Unit,
    onGroupUpdated: (PureGroup) -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var groupInfo by remember { mutableStateOf(group) }
    var members by remember { mutableStateOf<List<IosGroupMember>>(emptyList()) }
    var myRole by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var notificationsEnabled by remember(group.groupId) {
        mutableStateOf(IosPreferencesStore.isGroupNotificationsEnabled(group.groupId))
    }
    val groupMessages by repository.groupMessagesFlow(group.groupId).collectAsState(initial = emptyList())

    suspend fun reload() {
        groupInfo = repository.getGroup(group.groupId) ?: group
        members = repository.getGroupMembers(group.groupId)
        myRole = repository.getMyRole(group.groupId)
        loading = false
    }

    LaunchedEffect(group.groupId) {
        if (joinPreview) {
            loading = false
        } else {
            runCatching { reload() }.onFailure {
                errorMessage = it.message ?: "Failed to load group info"
                loading = false
            }
        }
    }

    val inviteLink = remember(groupInfo.groupId, groupInfo.publicGroupId, groupInfo.inviteCode, groupInfo.name, groupInfo.groupIconUrl) {
        buildGroupLink(
            groupId = groupInfo.groupId,
            groupCode = groupInfo.publicGroupId ?: groupInfo.inviteCode,
            groupName = groupInfo.name,
            iconUrl = groupInfo.groupIconUrl
        )
    }
    val detailLines = remember(groupInfo, members.size, myRole, linkPayload) {
        buildList {
            add(PureGroupInfoLine("Group ID", groupInfo.publicGroupId ?: linkPayload?.groupCode ?: groupInfo.groupId))
            add(PureGroupInfoLine("Visibility", groupInfo.visibility.ifBlank { "PRIVATE" }))
            add(PureGroupInfoLine("Created", if (groupInfo.createdAt > 0L) iosDateHeader(groupInfo.createdAt) else "Not available"))
            add(PureGroupInfoLine("Members", "${maxOf(members.size, groupInfo.memberCount)}"))
            myRole?.takeIf { it.isNotBlank() }?.let { add(PureGroupInfoLine("Your role", it)) }
            (groupInfo.inviteCode ?: linkPayload?.groupCode)?.takeIf { it.isNotBlank() }?.let {
                add(PureGroupInfoLine("Join code", it))
            }
        }
    }
    val canManage = !joinPreview && (myRole == "owner" || myRole == "admin")
    val mediaCount = remember(groupMessages) {
        groupMessages.count { it.type == "IMAGE" || !it.imageUrl.isNullOrBlank() }
    }

    PureGroupInfoScreen(
        groupName = groupInfo.name,
        memberCount = maxOf(members.size, groupInfo.memberCount),
        members = members.map { member -> UiGroupMember(member.user.toPureUser(), member.role) },
        myRole = myRole,
        iconUrl = groupInfo.groupIconUrl,
        loading = loading,
        detailLines = detailLines,
        inviteLink = if (joinPreview) linkPayload?.rawLink else inviteLink,
        pinnedMessagePreview = groupInfo.pinnedMessagePreview,
        mediaCount = mediaCount,
        notificationsEnabled = notificationsEnabled,
        canRename = canManage,
        canPinLatestMessage = canManage,
        showJoinButton = joinPreview,
        onBack = onBack,
        onExitGroup = {
            scope.launch {
                runCatching {
                    repository.exitGroup(group.groupId)
                    onExitGroup()
                }.onFailure { errorMessage = it.message ?: "Failed to exit group" }
            }
        },
        onJoinGroup = if (joinPreview) {
            {
                scope.launch {
                    val code = linkPayload?.groupCode ?: groupInfo.publicGroupId ?: groupInfo.inviteCode
                    if (code.isNullOrBlank()) {
                        errorMessage = "This group link is missing a join code"
                    } else {
                        runCatching { repository.joinGroupByCode(code) }
                            .onSuccess {
                                groupInfo = it
                                onJoinedGroup(it)
                            }
                            .onFailure { errorMessage = it.message ?: "Failed to join group" }
                    }
                }
            }
        } else null,
        onCopyInviteLink = {
            clipboard.setText(
                AnnotatedString(
                    if (joinPreview) {
                        linkPayload?.rawLink ?: inviteLink ?: ""
                    } else {
                        inviteLink ?: ""
                    }
                )
            )
        },
        onRenameGroup = { newName ->
            scope.launch {
                runCatching {
                    repository.renameGroup(group.groupId, newName)
                    reload()
                    onGroupUpdated(groupInfo)
                }.onFailure { errorMessage = it.message ?: "Failed to rename group" }
            }
        },
        onPinLatestMessage = {
            scope.launch {
                runCatching {
                    repository.pinLatestGroupMessage(group.groupId)
                    reload()
                    onGroupUpdated(groupInfo)
                }.onFailure { errorMessage = it.message ?: "Failed to pin latest message" }
            }
        },
        onToggleNotifications = { enabled ->
            notificationsEnabled = enabled
            IosPreferencesStore.setGroupNotificationsEnabled(group.groupId, enabled)
        },
        onMemberAction = { user, action ->
            scope.launch {
                runCatching {
                    when (action) {
                        "promote" -> repository.promoteToAdmin(group.groupId, user.uid)
                        "demote" -> repository.demoteAdmin(group.groupId, user.uid)
                        "transfer" -> repository.transferOwnership(group.groupId, user.uid)
                    }
                    reload()
                }.onFailure { errorMessage = it.message ?: "Failed to update member role" }
            }
        },
        renderImage = { url, modifier ->
            IosRemoteImage(url, modifier)
        }
    )

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("Close") } },
            title = { Text("Group info") },
            text = { Text(message) }
        )
    }
}

*/
internal fun iosRelativeTime(timestamp: Long): String {
    val diff = (currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L
    return when {
        minutes < 1L -> "Just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        days < 7L -> "${days}d ago"
        else -> iosDateHeader(timestamp)
    }
}

internal fun iosDateHeader(timestamp: Long): String {
    val diff = (currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val days = diff / 86_400_000L
    return when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        else -> "${days} days ago"
    }
}
