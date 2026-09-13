package com.example.shared.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.model.Announcement
import com.example.shared.model.AnnouncementType

@Composable
fun PureAnnouncementCard(
    announcement: Announcement,
    currentUid: String?,
    onReport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onLike: (() -> Unit)? = null,
    onComment: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    isLikedByCurrentUser: Boolean = false,
    isSaved: Boolean = false,
    likeCount: Int = announcement.likes.size,
    commentCount: Int = announcement.comments.size,
    onContactAuthor: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onCreateTeamClick: (() -> Unit)? = null,
    formatTime: (Long) -> String,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            // --- HEADER: Author, Time, Menu ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                // Avatar
                renderImage(
                    announcement.authorProfilePicUrl,
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape),
                    ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = announcement.author,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PureStudentBadge()
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatTime(announcement.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = announcement.type.name.replace("_", " ").lowercase()
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Box {
                    var showMenu by remember { mutableStateOf(false) }
                    
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Report") },
                            onClick = {
                                showMenu = false
                                onReport()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = "Report"
                                )
                            }
                        )
                        
                        if (announcement.authorUid == currentUid) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- CONTENT: Title & Body ---
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (announcement.title.isNotBlank()) {
                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                if (announcement.content.isNotBlank()) {
                    Text(
                        text = announcement.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
            }

            // --- LOST & FOUND DETAILS ---
            if (announcement.type == AnnouncementType.LOST_AND_FOUND && 
               (!announcement.itemName.isNullOrBlank() || !announcement.place.isNullOrBlank() || 
                !announcement.time.isNullOrBlank() || !announcement.reward.isNullOrBlank())) {
                
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    if (!announcement.itemName.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📦 Item: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(text = announcement.itemName!!, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!announcement.place.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📍 Place: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(text = announcement.place!!, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!announcement.time.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🕒 Time: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(text = announcement.time!!, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!announcement.reward.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "💰 Reward: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(text = announcement.reward!!, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // --- EVENT DETAILS ---
            if (announcement.type == AnnouncementType.EVENT && 
               (!announcement.eventVenue.isNullOrBlank() || !announcement.eventTime.isNullOrBlank() || 
                !announcement.eventPurpose.isNullOrBlank() || !announcement.eventDlType.isNullOrBlank() ||
                !announcement.eventDepartments.isNullOrBlank() || !announcement.eventLink.isNullOrBlank())) {
                
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    if (!announcement.eventVenue.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📍 Venue: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(text = announcement.eventVenue!!, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!announcement.eventTime.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🕒 Time: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(text = announcement.eventTime!!, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!announcement.eventPurpose.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🎯 Purpose: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(text = announcement.eventPurpose!!, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!announcement.eventDlType.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "📄 DL Type: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(text = announcement.eventDlType!!, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!announcement.eventMode.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "👥 Mode: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(text = announcement.eventMode!!, style = MaterialTheme.typography.bodyMedium)
                            if (announcement.eventMode == "Team" && announcement.eventMaxMembers != null) {
                                Text(text = " (Max ${announcement.eventMaxMembers})", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (!announcement.eventDepartments.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🏢 Depts: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(text = announcement.eventDepartments!!, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!announcement.eventLink.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Link: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(text = announcement.eventLink!!, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // --- EVENT ACTION BUTTON / TEAMS GATEWAY ---
            if (announcement.type == AnnouncementType.EVENT) {
                Spacer(modifier = Modifier.height(10.dp))
                val isTeam = announcement.eventMode.equals("Team", ignoreCase = true)
                Surface(
                    onClick = { (onCreateTeamClick ?: onClick)?.invoke() },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isTeam) Icons.Default.Groups else Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isTeam) "Team Event Participation" else "Solo Event Participation",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (isTeam) "Tap to open team manager & create team" else "Tap to view timeline, rules & register",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { (onCreateTeamClick ?: onClick)?.invoke() },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (isTeam) "Create Team" else "View Details",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            
            // --- IMAGE ---
            if (announcement.imageUrl != null) {
                Spacer(modifier = Modifier.height(12.dp))
                renderImage(
                    announcement.imageUrl,
                    Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 400.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EngagementAction(if (isLikedByCurrentUser) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder, "$likeCount", if (isLikedByCurrentUser) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) { onLike?.invoke() }
                EngagementAction(Icons.Outlined.ChatBubbleOutline, "$commentCount") { onComment?.invoke() ?: onClick?.invoke() }
                EngagementAction(Icons.Outlined.IosShare, "Share") { onShare() }
                EngagementAction(if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder, if (isSaved) "Saved" else "Save", MaterialTheme.colorScheme.onSurfaceVariant) { onSave?.invoke() }
            }
            
            val canContactAuthor = !announcement.authorUid.isNullOrBlank() &&
                announcement.authorUid != currentUid &&
                onContactAuthor != null

            if (canContactAuthor && announcement.type != AnnouncementType.NEWS) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { onContactAuthor?.invoke() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (announcement.type == AnnouncementType.LOST_AND_FOUND) {
                            "Contact User Privately"
                        } else {
                            "Contact Author Privately"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EngagementAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureCreatePostDialog(
    initialType: AnnouncementType,
    onDismiss: () -> Unit,
    isLoading: Boolean,
    onPost: (
        title: String, 
        content: String, 
        type: AnnouncementType, 
        itemName: String?, 
        place: String?, 
        time: String?, 
        reward: String?,
        eventVenue: String?,
        eventTime: String?,
        eventPurpose: String?,
        eventDlType: String?,
        eventMode: String?,
        eventMaxMembers: Int?,
        eventDepartments: String?,
        eventLink: String?,
        eventCategory: String?
    ) -> Unit,
    onAddPhotoClick: () -> Unit,
    hasImage: Boolean,
    onRemoveImage: () -> Unit,
    renderSelectedImage: @Composable (Modifier) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(initialType) }

    // Lost & Found specific fields
    var itemName by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var reward by remember { mutableStateOf("") }

    // Event specific fields
    var eventVenue by remember { mutableStateOf("") }
    var eventTime by remember { mutableStateOf("") }
    var eventPurpose by remember { mutableStateOf("") }
    var eventDlType by remember { mutableStateOf("") }
    var eventMode by remember { mutableStateOf("Solo") }
    var eventMaxMembers by remember { mutableStateOf("") }
    var eventDepartments by remember { mutableStateOf("") }
    var eventLink by remember { mutableStateOf("") }
    var eventCategory by remember { mutableStateOf<String?>(null) }

    // Date & Time Picker Bottom Sheet state
    var showDateTimePicker by remember { mutableStateOf(false) }
    var dateTimePickerTitle by remember { mutableStateOf("Select Date & Time") }
    var dateTimePickerMode by remember { mutableStateOf(DateTimePickerMode.DATE_AND_TIME) }
    var onDateTimeSelectedTarget by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showDateTimePicker && onDateTimeSelectedTarget != null) {
        PureDateTimePickerBottomSheet(
            title = dateTimePickerTitle,
            mode = dateTimePickerMode,
            onDismiss = { showDateTimePicker = false },
            onConfirmed = { res ->
                onDateTimeSelectedTarget?.invoke(res)
                showDateTimePicker = false
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .fillMaxHeight(0.9f)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Create Post",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (selectedType) {
                            AnnouncementType.NEWS -> "Share campus announcements or updates"
                            AnnouncementType.LOST_AND_FOUND -> "Help others find or return lost campus items"
                            AnnouncementType.EVENT -> "Host a workshop, hackathon, or cultural fest"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Post Type Selector
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple(AnnouncementType.NEWS, "General News", Icons.Default.Campaign),
                            Triple(AnnouncementType.LOST_AND_FOUND, "Lost & Found", Icons.Default.Search),
                            Triple(AnnouncementType.EVENT, "Event", Icons.Default.Event)
                        ).forEach { (type, label, icon) ->
                            val isSelected = selectedType == type
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedType = type }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title field (All posts)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    placeholder = {
                        Text(
                            when (selectedType) {
                                AnnouncementType.NEWS -> "e.g. Midterm Schedule Announcement"
                                AnnouncementType.LOST_AND_FOUND -> "e.g. Lost Blue HP Laptop Charger"
                                AnnouncementType.EVENT -> "e.g. National Level Code-A-Thon 2026"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ---------------- LOST & FOUND FIELDS ----------------
                if (selectedType == AnnouncementType.LOST_AND_FOUND) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Item Details",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = itemName,
                                onValueChange = { itemName = it },
                                label = { Text("What was lost / found?") },
                                placeholder = { Text("e.g. Silver Titan Watch, Student ID") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.HelpOutline, contentDescription = null) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = place,
                                onValueChange = { place = it },
                                label = { Text("Where was it located / lost?") },
                                placeholder = { Text("e.g. Block 3 Cafeteria / Library 2nd Floor") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Interactive Date/Time bottom sheet trigger
                            OutlinedTextField(
                                value = time,
                                onValueChange = { time = it },
                                label = { Text("When?") },
                                placeholder = { Text("Tap calendar to select date & time") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                readOnly = true,
                                leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        dateTimePickerTitle = "When was it lost / found?"
                                        dateTimePickerMode = DateTimePickerMode.DATE_AND_TIME
                                        onDateTimeSelectedTarget = { res -> time = res }
                                        showDateTimePicker = true
                                    }) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = reward,
                                onValueChange = { reward = it },
                                label = { Text("Reward (Optional)") },
                                placeholder = { Text("e.g. Treat / ₹500 / Sincere Gratitude") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.CardGiftcard, contentDescription = null) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ---------------- EVENT SPECIFIC FIELDS ----------------
                if (selectedType == AnnouncementType.EVENT) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Event Configuration",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Category Selector
                            Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(com.example.shared.model.EventCategory.values().toList()) { cat ->
                                    FilterChip(
                                        selected = eventCategory == cat.displayLabel,
                                        onClick = { eventCategory = if (eventCategory == cat.displayLabel) null else cat.displayLabel },
                                        label = { Text(cat.displayLabel) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // Date & Time with interactive bottom sheet
                            OutlinedTextField(
                                value = eventTime,
                                onValueChange = { eventTime = it },
                                label = { Text("Event Date & Time *") },
                                placeholder = { Text("Tap calendar to pick date & time") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                readOnly = true,
                                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        dateTimePickerTitle = "Event Date & Time"
                                        dateTimePickerMode = DateTimePickerMode.DATE_AND_TIME
                                        onDateTimeSelectedTarget = { res -> eventTime = res }
                                        showDateTimePicker = true
                                    }) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date & Time", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = eventVenue,
                                onValueChange = { eventVenue = it },
                                label = { Text("Venue / Location") },
                                placeholder = { Text("e.g. Auditorium Hall B / Online Google Meet") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Mode Selection (Solo vs Team)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Participation Mode:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = eventMode == "Solo",
                                        onClick = { eventMode = "Solo" },
                                        label = { Text("Solo") },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    FilterChip(
                                        selected = eventMode == "Team",
                                        onClick = { eventMode = "Team" },
                                        label = { Text("Team") },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }

                            if (eventMode == "Team") {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = eventMaxMembers,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) eventMaxMembers = it },
                                    label = { Text("Max Members Per Team") },
                                    placeholder = { Text("e.g. 4") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null) }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = eventDepartments,
                                onValueChange = { eventDepartments = it },
                                label = { Text("Eligible Departments") },
                                placeholder = { Text("e.g. Open to All / CSE, IT, ECE") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = eventPurpose,
                                onValueChange = { eventPurpose = it },
                                label = { Text("Purpose / Objective") },
                                placeholder = { Text("e.g. Hands-on Machine Learning & Networking") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = eventLink,
                                onValueChange = { eventLink = it },
                                label = { Text("Official Link / Registration URL") },
                                placeholder = { Text("https://...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Description / What's on your mind
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = {
                        Text(
                            when (selectedType) {
                                AnnouncementType.NEWS -> "Announcement Details *"
                                AnnouncementType.LOST_AND_FOUND -> "Additional Details / Identification Marks *"
                                AnnouncementType.EVENT -> "Event Description, Rules & Prizes *"
                            }
                        )
                    },
                    placeholder = {
                        Text(
                            when (selectedType) {
                                AnnouncementType.NEWS -> "Type all announcements and notices here..."
                                AnnouncementType.LOST_AND_FOUND -> "Describe color, scratches, where you kept it..."
                                AnnouncementType.EVENT -> "Add round rules, timeline overview, prizes and contact..."
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 6
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Photo Upload Card
                if (hasImage) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        renderSelectedImage(
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        IconButton(
                            onClick = onRemoveImage,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove Image",
                                tint = Color.White
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onAddPhotoClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Banner / Photo (Optional)")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom CTA Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank() && content.isNotBlank()) {
                                onPost(
                                    title,
                                    content,
                                    selectedType,
                                    itemName.takeIf { selectedType == AnnouncementType.LOST_AND_FOUND && it.isNotBlank() },
                                    place.takeIf { selectedType == AnnouncementType.LOST_AND_FOUND && it.isNotBlank() },
                                    time.takeIf { selectedType == AnnouncementType.LOST_AND_FOUND && it.isNotBlank() },
                                    reward.takeIf { selectedType == AnnouncementType.LOST_AND_FOUND && it.isNotBlank() },
                                    eventVenue.takeIf { selectedType == AnnouncementType.EVENT && it.isNotBlank() },
                                    eventTime.takeIf { selectedType == AnnouncementType.EVENT && it.isNotBlank() },
                                    eventPurpose.takeIf { selectedType == AnnouncementType.EVENT && it.isNotBlank() },
                                    eventDlType.takeIf { selectedType == AnnouncementType.EVENT && it.isNotBlank() },
                                    eventMode.takeIf { selectedType == AnnouncementType.EVENT },
                                    eventMaxMembers.toIntOrNull()
                                        .takeIf { selectedType == AnnouncementType.EVENT && eventMode == "Team" },
                                    eventDepartments.takeIf { selectedType == AnnouncementType.EVENT && it.isNotBlank() },
                                    eventLink.takeIf { selectedType == AnnouncementType.EVENT && it.isNotBlank() },
                                    eventCategory.takeIf { selectedType == AnnouncementType.EVENT }
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        enabled = title.isNotBlank() && content.isNotBlank() && !isLoading,
                        modifier = Modifier.height(48.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Publish Post", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun PureReportDialog(
    onDismiss: () -> Unit,
    onReport: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val reasons = listOf("Spam", "Inappropriate Content", "Fake", "Other")
    var selectedReason by remember { mutableStateOf(reasons.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Post") },
        text = {
            Column {
                Text("Why are you reporting this post?")
                Spacer(modifier = Modifier.height(8.dp))
                reasons.forEach { r ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = r }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = (r == selectedReason),
                            onClick = { selectedReason = r }
                        )
                        Text(
                            text = r,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                if (selectedReason == "Other") {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Details") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalReason = if (selectedReason == "Other") reason else selectedReason
                    if (finalReason.isNotBlank()) {
                        onReport(finalReason)
                    }
                }
            ) {
                Text("Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PureSharePostDialog(
    link: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShareViaApp: () -> Unit,
    onShareToFriend: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Post") },
        text = {
            Column {
                Text("Post Link:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = onCopy) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                if (onShareToFriend != null) {
                    TextButton(onClick = onShareToFriend) {
                        Text("Send to friend")
                    }
                }
                TextButton(onClick = onShareViaApp) {
                    Text("Share via...")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
