package com.example.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shared.model.PureFriendRequest
import com.example.shared.model.PureUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureFriendManagerBottomSheet(
    friends: List<PureUser>,
    requests: List<PureFriendRequest>,
    pendingRequestEmails: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onAcceptRequest: (String) -> Unit,
    onRejectRequest: (String) -> Unit,
    onUnfriend: (String) -> Unit,
    onSendRequest: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Manage Friends",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close")
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Requests (${requests.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Friends (${friends.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Add") }
                )
            }

            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> PureRequestsList(requests, onAcceptRequest, onRejectRequest, onProfileClick, renderImage)
                    1 -> PureFriendsList(friends, onUnfriend, onProfileClick, renderImage)
                    2 -> PureAddFriendSection(friends, pendingRequestEmails, onSendRequest)
                }
            }
        }
    }
}

@Composable
fun PureRequestsList(
    requests: List<PureFriendRequest>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    if (requests.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No pending requests", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(requests) { req ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.clickable { onProfileClick(req.senderId) }) {
                            Surface(shape = CircleShape, modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(req.senderName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        Spacer(Modifier.width(12.dp))
                        
                        Column(Modifier.weight(1f)) {
                            Text(req.senderName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(req.senderEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        Row {
                            IconButton(onClick = { onReject(req.senderId) }) {
                                Icon(Icons.Default.Close, "Reject", tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = { onAccept(req.senderId) }) {
                                Icon(Icons.Default.PersonAdd, "Accept", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PureFriendsList(
    friends: List<PureUser>,
    onUnfriend: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    if (friends.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No friends yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(friends) { friend ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProfileClick(friend.uid) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    renderImage(
                        friend.profilePicUrl,
                        Modifier.size(48.dp).clip(CircleShape),
                        ContentScale.Crop
                    )
                    
                    Spacer(Modifier.width(12.dp))
                    
                    Column(Modifier.weight(1f)) {
                        Text(friend.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(friend.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    var showConfirm by remember { mutableStateOf(false) }
                    
                    if (showConfirm) {
                        Row {
                            TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
                            TextButton(
                                onClick = { onUnfriend(friend.uid); showConfirm = false },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Confirm") }
                        }
                    } else {
                        TextButton(onClick = { showConfirm = true }) {
                            Text("Unfriend", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun PureAddFriendSection(
    friends: List<PureUser>,
    pendingRequestEmails: Set<String> = emptySet(),
    onSendRequest: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    val normalizedEmail = email.trim().lowercase()
    val normalizedPendingEmails = remember(pendingRequestEmails) {
        pendingRequestEmails.map { it.trim().lowercase() }.toSet()
    }
    
    val isAlreadyFriend = remember(normalizedEmail, friends) {
        normalizedEmail.isNotBlank() &&
            friends.any { it.email.trim().equals(normalizedEmail, ignoreCase = true) }
    }
    val isRequestSent = remember(normalizedEmail, normalizedPendingEmails) {
        normalizedEmail.isNotBlank() && normalizedEmail in normalizedPendingEmails
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Friend's Email") },
            modifier = Modifier.fillMaxWidth(),
            isError = isAlreadyFriend,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text("example@domain.com") }
        )
        
        if (isAlreadyFriend) {
            Text(
                text = "Already in your friends list", 
                color = MaterialTheme.colorScheme.error, 
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
        if (isRequestSent) {
            Text(
                text = "Friend request already sent",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
        
        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = { onSendRequest(email.trim()); email = "" },
            enabled = normalizedEmail.isNotBlank() &&
                !isAlreadyFriend &&
                !isRequestSent &&
                normalizedEmail.contains("@"),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.PersonAdd, null)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    isAlreadyFriend -> "Already Added"
                    isRequestSent -> "Request Sent"
                    else -> "Send Friend Request"
                }
            )
        }
        
        Spacer(Modifier.height(32.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("How it works", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter your friend's exact email address to send them a request. Once they accept, you'll be able to see each other on the map and chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
