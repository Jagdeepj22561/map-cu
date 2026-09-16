package com.example.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shared.model.Announcement
import com.example.shared.model.TeamEventInfo
import com.example.shared.model.TeamMemberInfo
import com.example.shared.model.TeamRequirement
import com.example.shared.repository.TeamRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun TeamEventSection(announcement: Announcement) {
    if (announcement.type != com.example.shared.model.AnnouncementType.EVENT || !announcement.eventMode.equals("Team", true)) return

    val repo = remember { TeamRepository() }
    val scope = rememberCoroutineScope()
    var requirements by remember { mutableStateOf<List<TeamRequirement>>(emptyList()) }
    var myTeam by remember { mutableStateOf<TeamEventInfo?>(null) }
    var otherTeams by remember { mutableStateOf<List<TeamEventInfo>>(emptyList()) }
    var showPost by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var needed by remember { mutableIntStateOf(2) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var createBusy by remember { mutableStateOf(false) }
    var joinTeamId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val myUid = remember { repo.myUserId() }
    var myRespondedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedResponses by remember { mutableStateOf<Set<String>>(emptySet()) }
    var responsesMap by remember { mutableStateOf<Map<String, List<TeamMemberInfo>>>(emptyMap()) }
    var postBusy by remember { mutableStateOf(false) }
    var deleteReqId by remember { mutableStateOf<String?>(null) }

    fun refreshAll() {
        scope.launch {
            loading = true
            runCatching {
                val mine = repo.getMyTeamMembership(announcement.id)
                val all = repo.getTeamsForAnnouncement(announcement.id)
                val reqs = repo.getTeamRequirements(announcement.id)
                val responded = repo.getMyRespondedRequirementIds(announcement.id)
                myTeam = mine
                otherTeams = if (mine == null) all else all.filter { it.id != mine.id }
                requirements = reqs
                myRespondedIds = responded
                status = null
            }.onFailure {
                status = it.message ?: "Unable to load team info."
            }
            loading = false
        }
    }

    LaunchedEffect(announcement.id) {
        while (currentCoroutineContext().isActive) {
            refreshAll()
            delay(30_000L)
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("${announcement.title} Team") }
        AlertDialog(
            onDismissRequest = { if (!createBusy) showCreateDialog = false },
            title = { Text("Create your team") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("One team per person per event. You can rename it later.")
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Team name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !createBusy && name.isNotBlank(),
                    onClick = {
                        createBusy = true
                        scope.launch {
                            runCatching {
                                repo.createTeam(announcement.id, name.trim(), announcement.eventMaxMembers)
                            }.onSuccess {
                                showCreateDialog = false
                                status = "Team created successfully."
                                refreshAll()
                            }.onFailure {
                                status = translateTeamError(it.message)
                            }
                            createBusy = false
                        }
                    }
                ) {
                    if (createBusy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(enabled = !createBusy, onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (renameDialogOpen && myTeam != null) {
        AlertDialog(
            onDismissRequest = { renameDialogOpen = false },
            title = { Text("Rename team") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Team name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        renameDialogOpen = false
                        scope.launch {
                            runCatching {
                                repo.renameTeam(myTeam!!.id, renameText.trim())
                            }.onSuccess {
                                status = "Team renamed."
                                refreshAll()
                            }.onFailure {
                                status = it.message ?: "Unable to rename team."
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameDialogOpen = false }) { Text("Cancel") } }
        )
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, null)
                Spacer(Modifier.width(8.dp))
                Text("Team Event", style = MaterialTheme.typography.titleLarge)
            }
            Text("Create a team, find teammates, or post what your team needs.")

            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Loading team info…", style = MaterialTheme.typography.bodySmall)
                }
                myTeam == null -> Button(
                    onClick = { showCreateDialog = true },
                    enabled = !createBusy
                ) {
                    Icon(Icons.Default.Groups, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Create Team")
                }
                else -> TeamOwnerCard(
                    team = myTeam!!,
                    onRename = {
                        renameText = myTeam!!.name
                        renameDialogOpen = true
                    },
                    onInvite = { showPost = true }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showFind = true; refreshAll() }) {
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

            if (otherTeams.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Other teams", style = MaterialTheme.typography.titleMedium)
                otherTeams.forEach { team ->
                    val joining = joinTeamId == team.id
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(team.name.ifBlank { "Untitled team" }, fontWeight = FontWeight.SemiBold)
                            if (team.members.isNotEmpty()) {
                                team.members.take(3).forEach { member ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Person, null, Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(member.name, style = MaterialTheme.typography.bodySmall)
                                        if (member.role == "owner") {
                                            Spacer(Modifier.width(6.dp))
                                            Text("(owner)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                if (team.members.size > 3) {
                                    Text("+${team.members.size - 3} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val capacityText = team.maxMembers?.let { "${team.members.size}/$it members" }
                                ?: "${team.members.size} member(s)"
                            Text(capacityText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            if (team.maxMembers != null) {
                                LinearProgressIndicator(
                                    progress = { team.capacityRatio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = if (team.isFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }

                            FilledTonalButton(
                                enabled = !joining && !team.isFull,
                                onClick = {
                                    joinTeamId = team.id
                                    scope.launch {
                                        runCatching { repo.joinTeam(team.id) }
                                            .onSuccess { status = "Joined ${team.name}."; refreshAll() }
                                            .onFailure { status = it.message ?: "Unable to join team." }
                                        joinTeamId = null
                                    }
                                }
                            ) {
                                if (joining) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    when {
                                        team.isFull -> "Team Full"
                                        joining -> "Joining…"
                                        else -> "Join this team"
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text("Team Requirements", style = MaterialTheme.typography.titleMedium)
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(22.dp))
                requirements.isEmpty() -> Text(
                    "No team requirements posted yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> requirements.forEach { requirement ->
                    val isAuthor = requirement.authorId == myUid
                    val alreadyInterested = requirement.id in myRespondedIds
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${requirement.membersNeeded} member(s) needed", style = MaterialTheme.typography.labelLarge)
                            Text(requirement.content, style = MaterialTheme.typography.bodyMedium)
                            when {
                                isAuthor -> {
                                    val expanded = requirement.id in expandedResponses
                                    val deleting = deleteReqId == requirement.id
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = {
                                            if (expanded) {
                                                expandedResponses = expandedResponses - requirement.id
                                            } else {
                                                expandedResponses = expandedResponses + requirement.id
                                                scope.launch {
                                                    runCatching { repo.getResponsesForRequirement(requirement.id) }
                                                        .onSuccess { list -> responsesMap = responsesMap + (requirement.id to list) }
                                                        .onFailure { status = it.message ?: "Unable to load interested users." }
                                                }
                                            }
                                        }) {
                                            Text(if (expanded) "Hide interested users" else "View interested")
                                        }
                                        Spacer(Modifier.weight(1f))
                                        IconButton(
                                            enabled = !deleting,
                                            onClick = {
                                                deleteReqId = requirement.id
                                                scope.launch {
                                                    runCatching { repo.deleteRequirement(requirement.id) }
                                                        .onSuccess {
                                                            status = "Requirement deleted."
                                                            refreshAll()
                                                        }
                                                        .onFailure { status = it.message ?: "Unable to delete." }
                                                    deleteReqId = null
                                                }
                                            }
                                        ) {
                                            if (deleting) {
                                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Default.Delete, "Delete requirement", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    if (expanded) {
                                        val list = responsesMap[requirement.id]
                                        when {
                                            list == null -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                            list.isEmpty() -> Text("No one has expressed interest yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            else -> list.forEach { member ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(Modifier.size(22.dp), shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(member.name.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(member.name, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                                alreadyInterested -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(4.dp))
                                        Text("You're interested", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                else -> {
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatching { repo.respondToRequirement(requirement.id) }
                                                .onSuccess {
                                                    myRespondedIds = myRespondedIds + requirement.id
                                                    status = "Interest sent."
                                                }
                                                .onFailure { status = it.message ?: "Unable to send interest." }
                                        }
                                    }) { Text("I'm interested") }
                                }
                            }
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
                            val isAuthor = r.authorId == myUid
                            val alreadyInterested = r.id in myRespondedIds
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Need ${r.membersNeeded} member(s)")
                                    Text(r.content)
                                    when {
                                        isAuthor -> {
                                            val expanded = r.id in expandedResponses
                                            val deleting = deleteReqId == r.id
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                TextButton(onClick = {
                                                    if (expanded) {
                                                        expandedResponses = expandedResponses - r.id
                                                    } else {
                                                        expandedResponses = expandedResponses + r.id
                                                        scope.launch {
                                                            runCatching { repo.getResponsesForRequirement(r.id) }
                                                                .onSuccess { list -> responsesMap = responsesMap + (r.id to list) }
                                                                .onFailure { status = it.message ?: "Unable to load interested users." }
                                                        }
                                                    }
                                                }) { Text(if (expanded) "Hide interested users" else "View interested") }
                                                Spacer(Modifier.weight(1f))
                                                IconButton(
                                                    enabled = !deleting,
                                                    onClick = {
                                                        deleteReqId = r.id
                                                        scope.launch {
                                                            runCatching { repo.deleteRequirement(r.id) }
                                                                .onSuccess {
                                                                    status = "Requirement deleted."
                                                                    refreshAll()
                                                                }
                                                                .onFailure { status = it.message ?: "Unable to delete." }
                                                            deleteReqId = null
                                                        }
                                                    }
                                                ) {
                                                    if (deleting) {
                                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    } else {
                                                        Icon(Icons.Default.Delete, "Delete requirement", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                            if (expanded) {
                                                val list = responsesMap[r.id]
                                                when {
                                                    list == null -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    list.isEmpty() -> Text("No one yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    else -> list.forEach { member ->
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Surface(Modifier.size(22.dp), shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                                                                Box(contentAlignment = Alignment.Center) {
                                                                    Text(member.name.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.labelSmall)
                                                                }
                                                            }
                                                            Spacer(Modifier.width(6.dp))
                                                            Text(member.name, style = MaterialTheme.typography.bodySmall)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        alreadyInterested -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(4.dp))
                                                Text("You're interested", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        else -> {
                                            TextButton(onClick = {
                                                scope.launch {
                                                    runCatching { repo.respondToRequirement(r.id) }
                                                        .onSuccess {
                                                            myRespondedIds = myRespondedIds + r.id
                                                            status = "Interest sent to the requirement owner."
                                                        }
                                                        .onFailure { status = it.message ?: "Unable to send interest." }
                                                }
                                            }) { Text("I'm interested") }
                                        }
                                    }
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
            onDismissRequest = { if (!postBusy) showPost = false },
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
                Button(enabled = text.isNotBlank() && !postBusy, onClick = {
                    postBusy = true
                    scope.launch {
                        runCatching {
                            repo.createTeamRequirement(announcement.id, text, needed)
                        }.onSuccess {
                            text = ""
                            showPost = false
                            status = "Requirement posted."
                            refreshAll()
                        }.onFailure {
                            status = it.message ?: "Unable to post requirement."
                        }
                        postBusy = false
                    }
                }) {
                    if (postBusy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Post")
                }
            },
            dismissButton = { TextButton(enabled = !postBusy, onClick = { showPost = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TeamOwnerCard(
    team: TeamEventInfo,
    onRename: () -> Unit,
    onInvite: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Your team", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(team.name.ifBlank { "Untitled team" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val capacity = team.maxMembers?.let { "${team.members.size}/$it members" } ?: "${team.members.size} member(s)"
            Text(capacity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (team.members.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                team.members.forEach { member ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(28.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(member.name.firstOrNull()?.uppercase() ?: "M", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(member.name, style = MaterialTheme.typography.bodyMedium)
                        if (member.role == "owner") {
                            Spacer(Modifier.width(6.dp))
                            AssistChip(onClick = {}, label = { Text("Owner") })
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRename) { Text("Rename") }
                OutlinedButton(onClick = onInvite) { Text("Post requirement") }
            }
        }
    }
}

private fun translateTeamError(message: String?): String {
    val text = message?.trim().orEmpty()
    return when {
        text.contains("duplicate key", ignoreCase = true) ||
            text.contains("unique", ignoreCase = true) ||
            text.contains("already exists", ignoreCase = true) ->
            "You already have a team for this event."
        text.isBlank() -> "Unable to create team."
        else -> text
    }
}
