package com.example.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.data.EventCache
import com.example.shared.model.*
import com.example.shared.repository.EventRepository
import com.example.shared.repository.TeamRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureEventDetailScreen(
    announcement: Announcement,
    currentUid: String?,
    friends: List<PureUser> = emptyList(),
    initialTab: Int = 0,
    onBack: () -> Unit,
    onShareClick: () -> Unit,
    onCopyIdClick: () -> Unit,
    onContactOrganizer: () -> Unit,
    onOpenChatWithUser: ((userId: String, name: String) -> Unit)? = null,
    formatTime: (Long) -> String,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit,
    eventRepository: EventRepository = remember { EventRepository() },
    teamRepository: TeamRepository = remember { TeamRepository() }
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val isOrganizer = announcement.authorUid == currentUid
    val isTeamMode = announcement.eventMode.equals("Team", ignoreCase = true)

    // Solo registration state
    var isSoloRegistered by remember { mutableStateOf(false) }
    var participantCount by remember { mutableIntStateOf(0) }
    var isRegisteringSolo by remember { mutableStateOf(false) }

    // Timeline state
    val cachedTimeline = remember(announcement.id) { EventCache.getTimeline(announcement.id) }
    var timelineItems by remember { mutableStateOf<List<EventTimelineItem>>(cachedTimeline ?: emptyList()) }
    var isTimelineLoading by remember { mutableStateOf(cachedTimeline == null) }
    var showAddMilestoneDialog by remember { mutableStateOf(false) }

    // Teams state
    val cachedTeams = remember(announcement.id) { EventCache.getTeams(announcement.id) }
    val cachedMyTeam = remember(announcement.id) { EventCache.getMyTeam(announcement.id) }
    val cachedJoinRequests = remember(cachedMyTeam?.id) { cachedMyTeam?.id?.let { EventCache.getJoinRequests(it) } }
    var myTeam by remember { mutableStateOf<TeamEventInfo?>(cachedMyTeam) }
    var otherTeams by remember {
        mutableStateOf<List<TeamEventInfo>>(
            cachedTeams?.filter { t ->
                t.id != cachedMyTeam?.id &&
                (currentUid == null || (t.ownerId != currentUid && t.members.none { m -> m.userId == currentUid }))
            } ?: emptyList()
        )
    }
    var joinRequestsForMyTeam by remember { mutableStateOf<List<JoinRequest>>(cachedJoinRequests ?: emptyList()) }
    var isTeamsLoading by remember { mutableStateOf(cachedTeams == null && cachedMyTeam == null) }
    var teamStatusMessage by remember { mutableStateOf<String?>(null) }
    var showCreateTeamDialog by remember { mutableStateOf(false) }
    var showInviteFriendsDialog by remember { mutableStateOf(false) }
    var showRenameTeamDialog by remember { mutableStateOf(false) }
    var teamToRequestJoin by remember { mutableStateOf<TeamEventInfo?>(null) }
    var myPendingRequestsByTeamId by remember { mutableStateOf<Map<String, JoinRequest>>(emptyMap()) }
    var processingRequestId by remember { mutableStateOf<String?>(null) }

    // Requirements matchmaking state
    var requirements by remember { mutableStateOf<List<TeamRequirement>>(emptyList()) }
    var myRespondedReqIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showPostRequirementDialog by remember { mutableStateOf(false) }

    // Load initial registration & timeline
    LaunchedEffect(announcement.id, currentUid) {
        scope.launch {
            participantCount = eventRepository.getRegistrationCount(announcement.id)
            if (currentUid != null) {
                isSoloRegistered = eventRepository.isRegisteredForEvent(announcement.id)
            }
        }
        scope.launch {
            if (timelineItems.isEmpty()) {
                isTimelineLoading = true
            }
            val items = eventRepository.getEventTimelineItems(announcement.id)
            timelineItems = items
            EventCache.saveTimeline(announcement.id, items)
            isTimelineLoading = false
        }
    }

    // Refresh teams
    fun refreshTeams(silent: Boolean = false) {
        if (!isTeamMode) return
        scope.launch {
            if (!silent && otherTeams.isEmpty() && myTeam == null) {
                isTeamsLoading = true
            }
            runCatching {
                val mine = teamRepository.getMyTeamMembership(announcement.id)
                val all = teamRepository.getTeamsForAnnouncement(announcement.id)
                val reqs = teamRepository.getTeamRequirements(announcement.id)
                val responded = teamRepository.getMyRespondedRequirementIds(announcement.id)

                myTeam = mine
                otherTeams = all.filter { t ->
                    t.id != mine?.id &&
                    (currentUid == null || (t.ownerId != currentUid && t.members.none { m -> m.userId == currentUid }))
                }
                EventCache.saveTeams(announcement.id, all)
                EventCache.saveMyTeam(announcement.id, mine)
                requirements = reqs
                myRespondedReqIds = responded

                // Load join requests if owner of myTeam
                if (mine != null && mine.ownerId == currentUid) {
                    val requests = teamRepository.getJoinRequestsForTeam(mine.id)
                    joinRequestsForMyTeam = requests
                    EventCache.saveJoinRequests(mine.id, requests)
                } else {
                    joinRequestsForMyTeam = emptyList()
                }

                // One batch query replaces one request per visible team.
                myPendingRequestsByTeamId = teamRepository
                    .getMyPendingJoinRequestsForTeams(otherTeams.map(TeamEventInfo::id))
            }.onFailure {
                if (!silent && otherTeams.isEmpty() && myTeam == null) {
                    teamStatusMessage = it.message ?: "Failed to refresh teams"
                }
            }
            if (!silent) {
                isTeamsLoading = false
            }
        }
    }

    // Initial eager load so badges and requests are ready before user even clicks Teams tab
    LaunchedEffect(announcement.id, isTeamMode) {
        if (isTeamMode) {
            refreshTeams(silent = cachedMyTeam != null || cachedTeams != null)
        }
    }

    // Live polling when viewing the screen
    LaunchedEffect(announcement.id, selectedTab, isTeamMode) {
        if (isTeamMode) {
            val pollDelayMs = if (selectedTab == 2) 15_000L else 30_000L
            while (currentCoroutineContext().isActive) {
                delay(pollDelayMs)
                refreshTeams(silent = true)
            }
        }
    }

    // Registration totals and organizer timeline changes are also remote
    // state. Keep them fresh without requiring users to leave and reopen.
    LaunchedEffect(announcement.id, currentUid, selectedTab) {
        while (currentCoroutineContext().isActive) {
            delay(30_000L)
            participantCount = eventRepository.getRegistrationCount(announcement.id)
            if (currentUid != null) {
                isSoloRegistered = eventRepository.isRegisteredForEvent(announcement.id)
            }
            if (selectedTab == 1) {
                val items = eventRepository.getEventTimelineItems(announcement.id)
                timelineItems = items
                EventCache.saveTimeline(announcement.id, items)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = announcement.title,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onShareClick) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = onCopyIdClick) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy ID")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header Banner & Quick Overview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (!announcement.imageUrl.isNullOrBlank()) {
                    renderImage(
                        announcement.imageUrl,
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        ContentScale.Crop
                    )
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    // Category & Status & Mode badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val status = EventStatus.fromEventTime(announcement.eventTime)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (status) {
                                EventStatus.LIVE -> MaterialTheme.colorScheme.errorContainer
                                EventStatus.UPCOMING -> MaterialTheme.colorScheme.primaryContainer
                                EventStatus.ENDED -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = status.displayLabel.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (status) {
                                    EventStatus.LIVE -> MaterialTheme.colorScheme.onErrorContainer
                                    EventStatus.UPCOMING -> MaterialTheme.colorScheme.onPrimaryContainer
                                    EventStatus.ENDED -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        if (!announcement.eventCategory.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = announcement.eventCategory,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = if (isTeamMode) {
                                    val max = announcement.eventMaxMembers?.let { " (Max $it)" } ?: ""
                                    "Team$max"
                                } else "Solo",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Organized by ${announcement.author}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isOrganizer) {
                            TextButton(
                                onClick = onContactOrganizer,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Contact", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // 4 Tabs: Overview, Timeline, Teams, Rules
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            height = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Overview", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Timeline", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                                if (timelineItems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge { Text("${timelineItems.size}") }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (isTeamMode) "Teams" else "Registration", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal)
                                if (isTeamMode && joinRequestsForMyTeam.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text("${joinRequestsForMyTeam.size}")
                                    }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Rules & Info", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (selectedTab) {
                    0 -> EventOverviewTab(
                        announcement = announcement,
                        isSoloMode = !isTeamMode,
                        isSoloRegistered = isSoloRegistered,
                        participantCount = participantCount,
                        isRegistering = isRegisteringSolo,
                        pendingRequestsCount = joinRequestsForMyTeam.size,
                        myTeamName = myTeam?.name,
                        onToggleSoloRegistration = {
                            if (currentUid == null) {
                                teamStatusMessage = "Please log in to register."
                                return@EventOverviewTab
                            }
                            isRegisteringSolo = true
                            scope.launch {
                                runCatching {
                                    if (isSoloRegistered) {
                                        eventRepository.unregisterFromEvent(announcement.id)
                                        isSoloRegistered = false
                                        participantCount = (participantCount - 1).coerceAtLeast(0)
                                    } else {
                                        eventRepository.registerForEvent(announcement.id)
                                        isSoloRegistered = true
                                        participantCount += 1
                                    }
                                }.onFailure {
                                    teamStatusMessage = it.message ?: "Registration failed."
                                }
                                isRegisteringSolo = false
                            }
                        },
                        onNavigateToTeams = { selectedTab = 2 }
                    )

                    1 -> EventTimelineTab(
                        timelineItems = timelineItems,
                        isLoading = isTimelineLoading,
                        isOrganizer = isOrganizer,
                        onAddMilestoneClick = { showAddMilestoneDialog = true },
                        onDeleteMilestone = { id ->
                            scope.launch {
                                runCatching {
                                    eventRepository.deleteTimelineItem(id)
                                    timelineItems = timelineItems.filterNot { it.id == id }
                                }
                            }
                        }
                    )

                    2 -> EventTeamsTab(
                        isTeamMode = isTeamMode,
                        isSoloRegistered = isSoloRegistered,
                        participantCount = participantCount,
                        myTeam = myTeam,
                        otherTeams = otherTeams,
                        joinRequests = joinRequestsForMyTeam,
                        pendingRequests = myPendingRequestsByTeamId,
                        requirements = requirements,
                        myRespondedReqIds = myRespondedReqIds,
                        currentUid = currentUid,
                        isLoading = isTeamsLoading,
                        onCreateTeamClick = { showCreateTeamDialog = true },
                        onRenameTeamClick = { showRenameTeamDialog = true },
                        onInviteFriendsClick = { showInviteFriendsDialog = true },
                        onRequestJoinTeam = { team -> teamToRequestJoin = team },
                        onApproveRequest = { reqId ->
                            if (processingRequestId == null) {
                                processingRequestId = reqId
                                scope.launch {
                                    runCatching {
                                        teamRepository.approveJoinRequest(reqId)
                                        teamStatusMessage = "Member added to team!"
                                        refreshTeams()
                                    }.onFailure { err ->
                                        teamStatusMessage = err.message ?: "Failed to approve request."
                                    }
                                    processingRequestId = null
                                }
                            }
                        },
                        onRejectRequest = { reqId ->
                            if (processingRequestId == null) {
                                processingRequestId = reqId
                                scope.launch {
                                    runCatching {
                                        teamRepository.rejectJoinRequest(reqId)
                                        teamStatusMessage = "Request rejected."
                                        refreshTeams()
                                    }.onFailure { err ->
                                        teamStatusMessage = err.message ?: "Failed to reject request."
                                    }
                                    processingRequestId = null
                                }
                            }
                        },
                        onWithdrawRequest = { reqId ->
                            scope.launch {
                                runCatching {
                                    teamRepository.withdrawJoinRequest(reqId)
                                    teamStatusMessage = "Request withdrawn."
                                    refreshTeams()
                                }.onFailure { err ->
                                    teamStatusMessage = err.message ?: "Failed to withdraw request."
                                }
                            }
                        },
                        onKickMember = { teamId, userId ->
                            scope.launch {
                                runCatching {
                                    teamRepository.kickMember(teamId, userId)
                                    teamStatusMessage = "Member removed."
                                    refreshTeams()
                                }.onFailure { err ->
                                    teamStatusMessage = err.message ?: "Failed to remove member."
                                }
                            }
                        },
                        onLeaveTeam = { teamId ->
                            scope.launch {
                                runCatching {
                                    teamRepository.leaveTeam(teamId)
                                    teamStatusMessage = "You left the team."
                                    refreshTeams()
                                }.onFailure { err ->
                                    teamStatusMessage = err.message ?: "Failed to leave team."
                                }
                            }
                        },
                        onDisbandTeam = { teamId ->
                            scope.launch {
                                runCatching {
                                    teamRepository.disbandTeam(teamId)
                                    teamStatusMessage = "Team disbanded."
                                    refreshTeams()
                                }.onFailure { err ->
                                    teamStatusMessage = err.message ?: "Failed to disband team."
                                }
                            }
                        },
                        onPostRequirementClick = { showPostRequirementDialog = true },
                        onRespondToRequirement = { reqId ->
                            scope.launch {
                                runCatching {
                                    teamRepository.respondToRequirement(reqId)
                                    myRespondedReqIds = myRespondedReqIds + reqId
                                }.onFailure { err ->
                                    teamStatusMessage = err.message ?: "Failed to send response."
                                }
                            }
                        },
                        onDeleteRequirement = { reqId ->
                            scope.launch {
                                runCatching {
                                    teamRepository.deleteRequirement(reqId)
                                    refreshTeams()
                                }.onFailure { err ->
                                    teamStatusMessage = err.message ?: "Failed to delete requirement."
                                }
                            }
                        },
                        processingRequestId = processingRequestId
                    )

                    3 -> EventRulesAndInfoTab(
                        announcement = announcement,
                        onContactOrganizer = onContactOrganizer
                    )
                }
            }
        }
    }

    // Dialog: Add Milestone (Organizer)
    if (showAddMilestoneDialog) {
        var mTitle by remember { mutableStateOf("") }
        var mDesc by remember { mutableStateOf("") }
        var mDate by remember { mutableStateOf("") }
        var mOrder by remember { mutableIntStateOf(timelineItems.size + 1) }
        var showMilestoneDatePicker by remember { mutableStateOf(false) }

        if (showMilestoneDatePicker) {
            PureDateTimePickerBottomSheet(
                title = "Round / Milestone Date & Time",
                mode = DateTimePickerMode.DATE_AND_TIME,
                onDismiss = { showMilestoneDatePicker = false },
                onConfirmed = { formatted ->
                    mDate = formatted
                    showMilestoneDatePicker = false
                }
            )
        }

        AlertDialog(
            onDismissRequest = { showAddMilestoneDialog = false },
            title = { Text("Add Event Milestone / Round") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mTitle,
                        onValueChange = { mTitle = it },
                        label = { Text("Milestone Title") },
                        placeholder = { Text("e.g. Round 1 - Idea Submission") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mDate,
                        onValueChange = { mDate = it },
                        label = { Text("Date / Time") },
                        placeholder = { Text("Tap calendar to select date & time") },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showMilestoneDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date & Time", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                    OutlinedTextField(
                        value = mDesc,
                        onValueChange = { mDesc = it },
                        label = { Text("Description & Instructions") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = mTitle.isNotBlank(),
                    onClick = {
                        scope.launch {
                            runCatching {
                                eventRepository.createTimelineItem(
                                    announcementId = announcement.id,
                                    title = mTitle.trim(),
                                    description = mDesc.trim(),
                                    scheduledAt = mDate.takeIf { it.isNotBlank() },
                                    sortOrder = mOrder
                                )
                                timelineItems = eventRepository.getEventTimelineItems(announcement.id)
                            }
                            showAddMilestoneDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMilestoneDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Create Team
    if (showCreateTeamDialog) {
        var teamName by remember { mutableStateOf("${announcement.title} Team") }
        var busy by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!busy) showCreateTeamDialog = false },
            title = { Text("Create Event Team") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Teams have a maximum of ${announcement.eventMaxMembers ?: "unlimited"} members.")
                    OutlinedTextField(
                        value = teamName,
                        onValueChange = { teamName = it },
                        label = { Text("Team Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && teamName.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                teamRepository.createTeam(
                                    announcementId = announcement.id,
                                    name = teamName.trim(),
                                    maxMembers = announcement.eventMaxMembers
                                )
                                refreshTeams()
                                showCreateTeamDialog = false
                            }.onFailure {
                                teamStatusMessage = it.message ?: "Failed to create team."
                            }
                            busy = false
                        }
                    }
                ) {
                    Text(if (busy) "Creating…" else "Create")
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { showCreateTeamDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Rename Team
    if (showRenameTeamDialog && myTeam != null) {
        var renameText by remember { mutableStateOf(myTeam!!.name) }
        AlertDialog(
            onDismissRequest = { showRenameTeamDialog = false },
            title = { Text("Rename Team") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Team Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        scope.launch {
                            runCatching {
                                teamRepository.renameTeam(myTeam!!.id, renameText.trim())
                                refreshTeams()
                            }
                            showRenameTeamDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameTeamDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Request to Join Team
    if (teamToRequestJoin != null) {
        var joinMessage by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) teamToRequestJoin = null },
            title = { Text("Request to Join ${teamToRequestJoin!!.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Introduce yourself or mention your skills/experience:")
                    OutlinedTextField(
                        value = joinMessage,
                        onValueChange = { joinMessage = it },
                        label = { Text("Message to team owner") },
                        placeholder = { Text("e.g. Hi! I have experience with UI design and Kotlin…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSubmitting,
                    onClick = {
                        isSubmitting = true
                        scope.launch {
                            runCatching {
                                teamRepository.requestToJoinTeam(teamToRequestJoin!!.id, joinMessage)
                                teamStatusMessage = "Join request sent to team leader!"
                                refreshTeams()
                                teamToRequestJoin = null
                            }.onFailure {
                                teamStatusMessage = it.message ?: "Failed to send request."
                            }
                            isSubmitting = false
                        }
                    }
                ) {
                    Text(if (isSubmitting) "Sending…" else "Send Request")
                }
            },
            dismissButton = {
                TextButton(enabled = !isSubmitting, onClick = { teamToRequestJoin = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Invite Friends
    if (showInviteFriendsDialog && myTeam != null) {
        AlertDialog(
            onDismissRequest = { showInviteFriendsDialog = false },
            title = { Text("Invite Friends to Team") },
            text = {
                if (friends.isEmpty()) {
                    Text("You don't have any friends added yet. Connect with classmates in Chats!")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(friends, key = { it.uid }) { friend ->
                            val alreadyInTeam = myTeam!!.members.any { it.userId == friend.uid }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(friend.name.firstOrNull()?.uppercase() ?: "F", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(friend.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text(friend.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (alreadyInTeam) {
                                        Text("Member", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        FilledTonalButton(
                                            onClick = {
                                                onOpenChatWithUser?.invoke(friend.uid, friend.name)
                                                showInviteFriendsDialog = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Message", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInviteFriendsDialog = false }) { Text("Close") }
            }
        )
    }

    // Dialog: Post Requirement
    if (showPostRequirementDialog) {
        var reqContent by remember { mutableStateOf("") }
        var reqNeeded by remember { mutableIntStateOf(1) }
        var isPosting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isPosting) showPostRequirementDialog = false },
            title = { Text("Post Teammate Requirement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = reqContent,
                        onValueChange = { reqContent = it },
                        label = { Text("Requirements & Skills") },
                        placeholder = { Text("Looking for 1 frontend developer familiar with Compose") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = reqNeeded.toString(),
                        onValueChange = { reqNeeded = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                        label = { Text("Members needed") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isPosting && reqContent.isNotBlank(),
                    onClick = {
                        isPosting = true
                        scope.launch {
                            runCatching {
                                teamRepository.createTeamRequirement(announcement.id, reqContent.trim(), reqNeeded)
                                refreshTeams()
                                showPostRequirementDialog = false
                            }.onFailure {
                                teamStatusMessage = it.message ?: "Failed to post requirement."
                            }
                            isPosting = false
                        }
                    }
                ) {
                    Text(if (isPosting) "Posting…" else "Post")
                }
            },
            dismissButton = {
                TextButton(enabled = !isPosting, onClick = { showPostRequirementDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Snackbar / status dialog if error occurs
    if (teamStatusMessage != null) {
        AlertDialog(
            onDismissRequest = { teamStatusMessage = null },
            title = { Text("Notice") },
            text = { Text(teamStatusMessage!!) },
            confirmButton = {
                TextButton(onClick = { teamStatusMessage = null }) { Text("OK") }
            }
        )
    }
}

// ---------------- TAB 0: OVERVIEW ----------------

@Composable
private fun EventOverviewTab(
    announcement: Announcement,
    isSoloMode: Boolean,
    isSoloRegistered: Boolean,
    participantCount: Int,
    isRegistering: Boolean,
    pendingRequestsCount: Int = 0,
    myTeamName: String? = null,
    onToggleSoloRegistration: () -> Unit,
    onNavigateToTeams: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pending Join Requests Banner for Team Leaders
        if (!isSoloMode && pendingRequestsCount > 0) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$pendingRequestsCount Pending Join Request${if (pendingRequestsCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (!myTeamName.isNullOrBlank()) "Students want to join $myTeamName" else "Students want to join your team",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onNavigateToTeams,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Review", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Registration Hero Card (Unstop Style)
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isSoloMode) "Solo Registration" else "Team Event Participation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isSoloMode) "$participantCount registered students" else "Compete in teams of up to ${announcement.eventMaxMembers ?: "any"} members",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isSoloMode) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSoloRegistered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Icon(
                                if (isSoloRegistered) Icons.Default.CheckCircle else Icons.Outlined.PersonAdd,
                                contentDescription = null,
                                tint = if (isSoloRegistered) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isSoloMode) {
                    Button(
                        onClick = onToggleSoloRegistration,
                        enabled = !isRegistering,
                        colors = if (isSoloRegistered) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isRegistering) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isSoloRegistered) "Cancel Registration" else "Register Now (Solo)", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onNavigateToTeams,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (pendingRequestsCount > 0) "Manage Team ($pendingRequestsCount Requests)"
                            else if (myTeamName != null) "View My Team ($myTeamName)"
                            else "Create or Join Team",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Key Information Grid
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Key Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                if (!announcement.eventVenue.isNullOrBlank()) {
                    DetailRow(Icons.Outlined.LocationOn, "Venue", announcement.eventVenue)
                }
                if (!announcement.eventTime.isNullOrBlank()) {
                    DetailRow(Icons.Outlined.CalendarMonth, "Date & Time", announcement.eventTime)
                }
                if (!announcement.eventDlType.isNullOrBlank()) {
                    DetailRow(Icons.Outlined.Description, "Duty Leave (DL)", announcement.eventDlType)
                }
                if (!announcement.eventDepartments.isNullOrBlank()) {
                    DetailRow(Icons.Outlined.Business, "Eligible Branches", announcement.eventDepartments)
                }
                if (!announcement.eventPurpose.isNullOrBlank()) {
                    DetailRow(Icons.Outlined.Flag, "Event Focus", announcement.eventPurpose)
                }
                if (!announcement.eventLink.isNullOrBlank()) {
                    DetailRow(Icons.Outlined.Link, "Official Link", announcement.eventLink)
                }
            }
        }

        // About the event
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("About This Event", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = announcement.content.ifBlank { "No detailed description provided." },
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ---------------- TAB 1: TIMELINE ----------------

@Composable
private fun EventTimelineTab(
    timelineItems: List<EventTimelineItem>,
    isLoading: Boolean,
    isOrganizer: Boolean,
    onAddMilestoneClick: () -> Unit,
    onDeleteMilestone: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Event Rounds & Milestones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Track schedule, submissions & evaluation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isOrganizer) {
                FilledTonalButton(onClick = onAddMilestoneClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Round")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            timelineItems.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.Timeline, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No timeline milestones yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("The organizer hasn't posted rounds schedule yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isOrganizer) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onAddMilestoneClick) {
                            Text("Add First Milestone")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(timelineItems, key = { it.id }) { item ->
                        TimelineMilestoneCard(
                            item = item,
                            isOrganizer = isOrganizer,
                            onDelete = { onDeleteMilestone(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineMilestoneCard(
    item: EventTimelineItem,
    isOrganizer: Boolean,
    onDelete: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${item.sortOrder}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (!item.scheduledAt.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(item.scheduledAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                if (item.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isOrganizer) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ---------------- TAB 2: TEAMS ----------------

@Composable
private fun EventTeamsTab(
    isTeamMode: Boolean,
    isSoloRegistered: Boolean,
    participantCount: Int,
    myTeam: TeamEventInfo?,
    otherTeams: List<TeamEventInfo>,
    joinRequests: List<JoinRequest>,
    pendingRequests: Map<String, JoinRequest>,
    requirements: List<TeamRequirement>,
    myRespondedReqIds: Set<String>,
    currentUid: String?,
    isLoading: Boolean,
    onCreateTeamClick: () -> Unit,
    onRenameTeamClick: () -> Unit,
    onInviteFriendsClick: () -> Unit,
    onRequestJoinTeam: (TeamEventInfo) -> Unit,
    onApproveRequest: (String) -> Unit,
    onRejectRequest: (String) -> Unit,
    onWithdrawRequest: (String) -> Unit,
    onKickMember: (teamId: String, userId: String) -> Unit,
    onLeaveTeam: (teamId: String) -> Unit,
    onDisbandTeam: (teamId: String) -> Unit,
    onPostRequirementClick: () -> Unit,
    onRespondToRequirement: (String) -> Unit,
    onDeleteRequirement: (String) -> Unit,
    processingRequestId: String? = null
) {
    if (!isTeamMode) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Solo Event", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("This is an individual event. Team management is not applicable.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Current Participants: $participantCount", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- MY TEAM SECTION ---
        item {
            if (myTeam != null) {
                MyTeamCard(
                    team = myTeam,
                    currentUid = currentUid,
                    joinRequests = joinRequests,
                    onRename = onRenameTeamClick,
                    onInvite = onInviteFriendsClick,
                    onApproveRequest = onApproveRequest,
                    onRejectRequest = onRejectRequest,
                    onKickMember = { uid -> onKickMember(myTeam.id, uid) },
                    onLeave = { onLeaveTeam(myTeam.id) },
                    onDisband = { onDisbandTeam(myTeam.id) },
                    processingRequestId = processingRequestId
                )
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("You don't have a team yet", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Create your own team or request to join an existing team below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onCreateTeamClick) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Create Team")
                            }
                            OutlinedButton(onClick = onPostRequirementClick) {
                                Text("Find Teammates")
                            }
                        }
                    }
                }
            }
        }

        // --- OPEN TEAMS LIST ---
        item {
            Text("Open Teams (${otherTeams.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (otherTeams.isEmpty()) {
            item {
                Text(
                    "No open teams yet. Be the first to create one!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(otherTeams, key = { it.id }) { team ->
                OpenTeamCard(
                    team = team,
                    pendingRequest = pendingRequests[team.id],
                    canRequestJoin = myTeam == null && !team.isFull &&
                            (currentUid == null || (team.ownerId != currentUid && team.members.none { it.userId == currentUid })),
                    onRequestJoin = { onRequestJoinTeam(team) },
                    onWithdrawRequest = {
                        val req = pendingRequests[team.id]
                        if (req != null) onWithdrawRequest(req.id)
                    }
                )
            }
        }

        // --- TEAM REQUIREMENTS / MATCHMAKING SECTION ---
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Teammate Requests (${requirements.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onPostRequirementClick) {
                    Text("+ Post Request", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (requirements.isEmpty()) {
            item {
                Text(
                    "No open requests. Looking for teammates? Post one above!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(requirements, key = { it.id }) { req ->
                RequirementCard(
                    requirement = req,
                    currentUid = currentUid,
                    hasResponded = myRespondedReqIds.contains(req.id),
                    onRespond = { onRespondToRequirement(req.id) },
                    onDelete = { onDeleteRequirement(req.id) }
                )
            }
        }
    }
}

// ---------------- SUB-COMPONENTS FOR TEAMS TAB ----------------

@Composable
private fun MyTeamCard(
    team: TeamEventInfo,
    currentUid: String?,
    joinRequests: List<JoinRequest>,
    onRename: () -> Unit,
    onInvite: () -> Unit,
    onApproveRequest: (String) -> Unit,
    onRejectRequest: (String) -> Unit,
    onKickMember: (String) -> Unit,
    onLeave: () -> Unit,
    onDisband: () -> Unit,
    processingRequestId: String? = null
) {
    val isOwner = team.ownerId == currentUid

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(team.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(if (isOwner) "Leader" else "Member", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text(
                        if (team.maxMembers != null) "${team.members.size} / ${team.maxMembers} members"
                        else "${team.members.size} members",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isOwner) {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename Team", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Members List
            Text("Members", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))

            team.members.forEach { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                member.name.firstOrNull()?.uppercase() ?: "?",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(member.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            if (member.userId == team.ownerId) {
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text("Leader", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                        }
                    }

                    if (isOwner && member.userId != currentUid) {
                        IconButton(
                            onClick = { onKickMember(member.userId) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.PersonRemove, contentDescription = "Remove member", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Pending Join Requests for Owner
            if (isOwner && joinRequests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Pending Join Requests (${joinRequests.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))

                joinRequests.forEach { req ->
                    val isCurrentBusy = processingRequestId == req.id
                    val isAnyBusy = processingRequestId != null
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(req.requesterName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.weight(1f))
                                FilledTonalButton(
                                    onClick = { onApproveRequest(req.id) },
                                    enabled = !isAnyBusy,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    if (isCurrentBusy) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                    } else {
                                        Text("Approve", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                OutlinedButton(
                                    onClick = { onRejectRequest(req.id) },
                                    enabled = !isAnyBusy,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("Reject", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (req.message.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("\"${req.message}\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isOwner) {
                    OutlinedButton(onClick = onInvite) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Invite Friends")
                    }
                    OutlinedButton(
                        onClick = onDisband,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Disband Team")
                    }
                } else {
                    OutlinedButton(
                        onClick = onLeave,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Leave Team")
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenTeamCard(
    team: TeamEventInfo,
    pendingRequest: JoinRequest?,
    canRequestJoin: Boolean,
    onRequestJoin: () -> Unit,
    onWithdrawRequest: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(team.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (team.isFull) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text("FULL", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Member progress bar
            if (team.maxMembers != null) {
                LinearProgressIndicator(
                    progress = { team.capacityRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (team.isFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("${team.members.size} / ${team.maxMembers} members (${team.availableSpots} spots left)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("${team.members.size} members", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Request to Join / Pending / Full
            when {
                pendingRequest != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(onClick = {}, label = { Text("Request Pending") }, leadingIcon = { Icon(Icons.Default.HourglassTop, null, modifier = Modifier.size(14.dp)) })
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onWithdrawRequest) {
                            Text("Withdraw", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                team.isFull -> {
                    FilledTonalButton(enabled = false, onClick = {}) {
                        Text("Team Full")
                    }
                }
                canRequestJoin -> {
                    FilledTonalButton(onClick = onRequestJoin) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Request to Join")
                    }
                }
            }
        }
    }
}

@Composable
private fun RequirementCard(
    requirement: TeamRequirement,
    currentUid: String?,
    hasResponded: Boolean,
    onRespond: () -> Unit,
    onDelete: () -> Unit
) {
    val isAuthor = requirement.authorId == currentUid

    ElevatedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Need ${requirement.membersNeeded} member(s)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                if (isAuthor) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(requirement.content, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(8.dp))

            if (!isAuthor) {
                if (hasResponded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Interested Sent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    TextButton(onClick = onRespond) {
                        Text("I'm Interested")
                    }
                }
            }
        }
    }
}

// ---------------- TAB 3: RULES & INFO ----------------

@Composable
private fun EventRulesAndInfoTab(
    announcement: Announcement,
    onContactOrganizer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Eligibility & Registration Rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "• Open to students of: ${announcement.eventDepartments ?: "All university departments"}\n" +
                    "• Participation Mode: ${announcement.eventMode ?: "Solo / Team"}\n" +
                    "• Team Size: ${if (announcement.eventMode.equals("Team", ignoreCase = true)) "Up to " + (announcement.eventMaxMembers ?: "any") + " members" else "Individual entry"}\n" +
                    "• Code of Conduct: Participants must adhere to the university academic integrity guidelines.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Duty Leave (DL) Policy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (!announcement.eventDlType.isNullOrBlank()) {
                        "• DL Category: ${announcement.eventDlType}\n• Attendance approval will be coordinated post-event upon verifying attendance with the event coordinator."
                    } else {
                        "• DL status not explicitly declared for this event. Check with your department coordinator."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp
                )
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Event Coordinator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(announcement.author.firstOrNull()?.uppercase() ?: "C", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(announcement.author, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("University Event Coordinator", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = onContactOrganizer) {
                        Text("Contact")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
