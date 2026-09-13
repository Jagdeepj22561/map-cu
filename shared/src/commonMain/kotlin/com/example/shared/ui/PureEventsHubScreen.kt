package com.example.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.data.EventCache
import com.example.shared.model.EventCategory
import com.example.shared.model.EventListItem
import com.example.shared.model.EventStatus
import com.example.shared.repository.EventRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureEventsHubScreen(
    onEventClick: (String) -> Unit,
    onOpenTeamsClick: ((String) -> Unit)? = null,
    onCreateEventClick: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit,
    eventRepository: EventRepository = remember { EventRepository() }
) {
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf<EventCategory?>(null) }
    var selectedStatus by remember { mutableStateOf<EventStatus?>(null) }
    var selectedMode by remember { mutableStateOf<String?>(null) } // "Solo", "Team", or null
    val cachedEvents = remember { EventCache.getEvents() }
    var events by remember { mutableStateOf<List<EventListItem>>(cachedEvents ?: emptyList()) }
    var isLoading by remember { mutableStateOf(cachedEvents == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun refreshEvents() {
        scope.launch {
            val isDefaultFilter = selectedCategory == null && selectedStatus == null && selectedMode == null && searchQuery.isBlank()
            if (isDefaultFilter && events.isNotEmpty()) {
                isLoading = false
            } else if (events.isEmpty()) {
                isLoading = true
            }
            errorMessage = null
            runCatching {
                eventRepository.getEvents(
                    category = selectedCategory?.displayLabel,
                    mode = selectedMode,
                    status = selectedStatus,
                    searchQuery = searchQuery
                )
            }.onSuccess {
                events = it
                if (isDefaultFilter) {
                    EventCache.saveEvents(it)
                }
            }.onFailure {
                if (events.isEmpty()) {
                    errorMessage = it.message ?: "Failed to load events"
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedCategory, selectedStatus, selectedMode, searchQuery) {
        refreshEvents()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateEventClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = "Host Event") },
                text = { Text("Host Event", fontWeight = FontWeight.Bold) },
                modifier = Modifier.padding(bottom = 96.dp)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top Section: Title & Stats summary
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Events Hub",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Compete, learn, collaborate & win",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { refreshEvents() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh events")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Integrated Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search hackathons, workshops, competitions…", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Multi-dimensional filters (Horizontal scrollable)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mode filter
                        FilterChip(
                            selected = selectedMode == null,
                            onClick = { selectedMode = null },
                            label = { Text("All Modes") }
                        )
                        FilterChip(
                            selected = selectedMode == "Solo",
                            onClick = { selectedMode = if (selectedMode == "Solo") null else "Solo" },
                            label = { Text("Solo") },
                            leadingIcon = { Icon(Icons.Outlined.Person, null, modifier = Modifier.size(14.dp)) }
                        )
                        FilterChip(
                            selected = selectedMode == "Team",
                            onClick = { selectedMode = if (selectedMode == "Team") null else "Team" },
                            label = { Text("Team") },
                            leadingIcon = { Icon(Icons.Outlined.Groups, null, modifier = Modifier.size(14.dp)) }
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Status filter
                        FilterChip(
                            selected = selectedStatus == null,
                            onClick = { selectedStatus = null },
                            label = { Text("All Status") }
                        )
                        EventStatus.values().forEach { status ->
                            FilterChip(
                                selected = selectedStatus == status,
                                onClick = { selectedStatus = if (selectedStatus == status) null else status },
                                label = { Text(status.displayLabel) }
                            )
                        }
                    }

                    // Category chips row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All Categories") }
                        )
                        EventCategory.values().forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                                label = { Text(cat.displayLabel) }
                            )
                        }
                    }
                }
            }

            // Body content: Event cards list / Loading / Empty
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = errorMessage ?: "Unable to load events.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { refreshEvents() }) {
                            Text("Retry")
                        }
                    }
                }

                events.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No events found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try clearing or changing your filters to see upcoming events.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                selectedCategory = null
                                selectedStatus = null
                                selectedMode = null
                                onSearchQueryChange("")
                            }
                        ) {
                            Text("Clear All Filters")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(events, key = { it.announcementId }) { event ->
                            RichEventCard(
                                event = event,
                                onClick = { onEventClick(event.announcementId) },
                                onOpenTeamsClick = onOpenTeamsClick?.let { callback -> { callback(event.announcementId) } },
                                renderImage = renderImage
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RichEventCard(
    event: EventListItem,
    onClick: () -> Unit,
    onOpenTeamsClick: (() -> Unit)? = null,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Header Image / Banner with overlay pills
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                if (!event.imageUrl.isNullOrBlank()) {
                    renderImage(
                        event.imageUrl,
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                        ContentScale.Crop
                    )
                } else {
                    // Fallback stylish gradient background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                // Status pill (Live / Upcoming / Ended)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (event.status) {
                        EventStatus.LIVE -> MaterialTheme.colorScheme.errorContainer
                        EventStatus.UPCOMING -> MaterialTheme.colorScheme.primaryContainer
                        EventStatus.ENDED -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (event.status == EventStatus.LIVE) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                            )
                        }
                        Text(
                            text = event.status.displayLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (event.status) {
                                EventStatus.LIVE -> MaterialTheme.colorScheme.onErrorContainer
                                EventStatus.UPCOMING -> MaterialTheme.colorScheme.onPrimaryContainer
                                EventStatus.ENDED -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Category pill on top right
                if (!event.eventCategory.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = event.eventCategory,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Card Body
            Column(modifier = Modifier.padding(16.dp)) {
                // Title
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Mode & Team Size strip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isTeam = event.eventMode.equals("Team", ignoreCase = true)
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = if (isTeam) {
                                    val size = event.eventMaxMembers?.let { " (Max $it)" } ?: ""
                                    "Team Event$size"
                                } else {
                                    "Solo Event"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (isTeam) Icons.Outlined.Groups else Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = null
                    )

                    if (!event.eventVenue.isNullOrBlank()) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = event.eventVenue,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = null
                        )
                    }
                }

                if (!event.eventTime.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = event.eventTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    thickness = 0.5.dp
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Footer: Registered count & View Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isTeam = event.eventMode.equals("Team", ignoreCase = true)
                    val countText = if (isTeam) {
                        "${event.teamCount} team(s) registered"
                    } else {
                        "${event.participantCount} participant(s)"
                    }

                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FilledTonalButton(
                        onClick = {
                            if (isTeam && onOpenTeamsClick != null) {
                                onOpenTeamsClick()
                            } else {
                                onClick()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            if (isTeam) Icons.Outlined.Groups else Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isTeam) "Create / Join Team" else "View Details",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
