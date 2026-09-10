package com.example.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.model.PureMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureChatDetailScreen(
    friendName: String?,
    friendProfilePicUrl: String?,
    messages: List<PureMessage>,
    myUid: String,
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onSendMessage: (String) -> Unit,
    onPickImage: () -> Unit,
    onDeleteMessages: (Set<String>, Boolean) -> Unit,
    onCopyMessages: (Set<String>) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onBlockUser: () -> Unit,
    onClearChat: () -> Unit,
    onDeleteChat: () -> Unit,
    onReportUser: () -> Unit,
    isBlocked: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    formatTime: (Long) -> String,
    formatDateHeader: (Long) -> String,
    onOpenPostLink: (String) -> Unit,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {

    val listState = rememberLazyListState()
    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode by remember { derivedStateOf { selectedMessageIds.isNotEmpty() } }
    var showMenu by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    val selectedMessages = remember(selectedMessageIds, messages) {
        messages.filter { it.messageId in selectedMessageIds }
    }
    val canEditSelected by remember(selectedMessages, myUid) {
        derivedStateOf {
            selectedMessages.size == 1 && selectedMessages.firstOrNull()?.senderId == myUid
        }
    }

    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.content.contains(searchQuery, true) }
    }
    val displayedMessages = remember(filteredMessages) { filteredMessages }
    val chatItems = remember(displayedMessages) {
        val items = mutableListOf<ChatUiItem>()
        for (i in displayedMessages.indices) {
            val msg = displayedMessages[i]
            items.add(ChatUiItem.Msg(msg))
            val isBoundary = i == displayedMessages.lastIndex ||
                    formatDateHeader(msg.timestamp) != formatDateHeader(displayedMessages[i + 1].timestamp)
            if (isBoundary) {
                items.add(ChatUiItem.Header(formatDateHeader(msg.timestamp)))
            }
        }
        items
    }

    // Scroll to the latest message when new content arrives.
    LaunchedEffect(filteredMessages.size) {
        if (chatItems.isNotEmpty()) {
            listState.animateScrollToItem(chatItems.lastIndex)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search messages") },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onProfileClick() }
                        ) {
                            renderImage(
                                friendProfilePicUrl,
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = friendName ?: "Chat",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    PureStudentBadge()
                                }
                                Text(
                                    text = "Tap to view profile",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            onCopyMessages(selectedMessageIds)
                            selectedMessageIds = emptySet()
                        }) {
                            Icon(Icons.Default.ContentCopy, null)
                        }
                        if (canEditSelected) {
                            IconButton(onClick = {
                                val target = selectedMessages.first()
                                editingMessageId = target.messageId
                                input = target.content
                                selectedMessageIds = emptySet()
                            }) {
                                Icon(Icons.Default.Edit, null)
                            }
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, null)
                        }
                    } else if (isSearchActive) {
                        IconButton(onClick = {
                            onSearchQueryChange("")
                            onToggleSearch(false)
                        }) {
                            Icon(Icons.Default.Close, null)
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, null)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Search") },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    onClick = {
                                        showMenu = false
                                        onToggleSearch(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("View Profile") },
                                    leadingIcon = { Icon(Icons.Default.Person, null) },
                                    onClick = {
                                        showMenu = false
                                        onProfileClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isBlocked) "Unblock User" else "Block User") },
                                    leadingIcon = { Icon(Icons.Default.Block, null) },
                                    onClick = {
                                        showMenu = false
                                        onBlockUser()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Chat") },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                                    onClick = {
                                        showMenu = false
                                        onClearChat()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Chat") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = {
                                        showMenu = false
                                        onDeleteChat()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Report User") },
                                    leadingIcon = { Icon(Icons.Default.Report, null) },
                                    onClick = {
                                        showMenu = false
                                        onReportUser()
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
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
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

                    IconButton(onClick = onPickImage) {
                        Icon(
                            Icons.Default.Image,
                            null,
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
                            if (input.isNotBlank()) {
                                val editingTargetId = editingMessageId
                                if (editingTargetId != null) {
                                    onEditMessage(editingTargetId, input)
                                } else {
                                    onSendMessage(input)
                                }
                                input = ""
                                selectedMessageIds = emptySet()
                                editingMessageId = null
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

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    start = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                    end = paddingValues.calculateEndPadding(LayoutDirection.Ltr),
                    bottom = paddingValues.calculateBottomPadding()
                )
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(chatItems) { item ->
                when (item) {
                    is ChatUiItem.Header -> DateHeader(item.text)
                    is ChatUiItem.Msg -> {
                        val isMe = item.message.senderId == myUid
                        PureMessageBubble(
                            message = item.message,
                            isMe = isMe,
                            isSelected = item.message.messageId in selectedMessageIds,
                            onLongClick = {
                                selectedMessageIds = selectedMessageIds.toggle(item.message.messageId)
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedMessageIds = selectedMessageIds.toggle(item.message.messageId)
                                }
                            },
                            formatTime = formatTime,
                            onOpenPostLink = onOpenPostLink,
                            renderImage = renderImage
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleteForEveryone = selectedMessages.isNotEmpty() &&
                            selectedMessages.all { it.senderId == myUid }
                        onDeleteMessages(selectedMessageIds, deleteForEveryone)
                        selectedMessageIds = emptySet()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Delete selected messages?") },
            text = {
                Text(
                    if (selectedMessages.all { it.senderId == myUid }) {
                        "Selected messages will be removed."
                    } else {
                        "Only your local copy will be removed for messages sent by others."
                    }
                )
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PureMessageBubble(
    message: PureMessage,
    isMe: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    formatTime: (Long) -> String,
    onOpenPostLink: (String) -> Unit,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isMe) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 2.dp),
        contentAlignment = alignment
    ) {
        BoxWithConstraints {
            val maxBubbleWidth = maxWidth * 0.72f
            Column(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
            ) {
                Surface(
                    color = bubbleColor,
                    shape = shape,
                    tonalElevation = 1.dp
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        MessageBubbleText(
                            message = message,
                            textColor = textColor,
                            onOpenPostLink = onOpenPostLink,
                            renderImage = renderImage,
                            modifier = Modifier.padding(end = 36.dp, bottom = 12.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        ) {
                            Text(
                                text = formatTime(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                            if (isMe) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (message.isRead) Icons.Default.DoneAll else Icons.Default.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = textColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubbleText(
    message: PureMessage,
    textColor: Color,
    onOpenPostLink: (String) -> Unit,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!message.imageUrl.isNullOrBlank()) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            renderImage(
                message.imageUrl,
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp)
                    .clip(RoundedCornerShape(14.dp)),
                ContentScale.Crop
            )
            if (message.content.isNotBlank()) {
                Text(
                    text = message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    val postLinkMatch = remember(message.content) { POST_LINK_REGEX.find(message.content) }
    if (postLinkMatch == null) {
        Text(
            text = message.content,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
        )
        return
    }

    val link = postLinkMatch.value
    val annotatedText = remember(message.content, link) {
        buildAnnotatedString {
            append(message.content)
            val start = message.content.indexOf(link)
            if (start >= 0) {
                val end = start + link.length
                addStyle(
                    style = SpanStyle(
                        color = textColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    ),
                    start = start,
                    end = end
                )
                addStringAnnotation(
                    tag = POST_LINK_TAG,
                    annotation = link,
                    start = start,
                    end = end
                )
            }
        }
    }

    ClickableText(
        text = annotatedText,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(color = textColor)
    ) { offset ->
        annotatedText
            .getStringAnnotations(tag = POST_LINK_TAG, start = offset, end = offset)
            .firstOrNull()
            ?.let { onOpenPostLink(it.item) }
    }
}

@Composable
private fun DateHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

private sealed interface ChatUiItem {
    data class Header(val text: String) : ChatUiItem
    data class Msg(val message: PureMessage) : ChatUiItem
}

private fun Set<String>.toggle(id: String): Set<String> =
    if (contains(id)) this - id else this + id

private const val POST_LINK_TAG = "post_link"
private val POST_LINK_REGEX = Regex("""(?:maps123://post/|https://maps123\.example\.com/post/)[A-Za-z0-9-]+""")
