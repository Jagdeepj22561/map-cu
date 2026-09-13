package com.example.maps123.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.maps123.ui.components.AppAsyncImage
import com.example.maps123.ui.components.InstagramCommentsSheet
import com.example.maps123.data.repository.AnnouncementRepository
import com.example.maps123.data.repository.ChatRepository
import com.example.maps123.data.repository.UserRepository
import com.example.maps123.data.local.UserEntity
import com.example.maps123.utils.ImageUtils
import com.example.maps123.utils.PostLinks
import com.example.maps123.utils.toPureUser
import com.example.shared.model.Announcement
import com.example.shared.model.AnnouncementType
import com.example.shared.model.Comment
import com.example.shared.model.buildAnnouncementId
import com.example.maps123.data.repository.AuthRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.*
import java.text.SimpleDateFormat

import com.example.shared.ui.*

private fun Throwable.isFirestorePermissionDenied(): Boolean =
    message?.contains("permission", ignoreCase = true) == true ||
        message?.contains("row-level security", ignoreCase = true) == true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsScreen(
    repository: AnnouncementRepository,
    chatRepository: ChatRepository,
    searchQuery: String = "",
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onAddFriendClick: () -> Unit,
    onAnnouncementClick: (String) -> Unit,
    onOpenTeamsClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val announcements by remember(repository) {
        repository.getAnnouncementsFlow(limit = 20)
    }.collectAsState(initial = emptyList())
    val isLoading by repository.isInitialLoadRunning.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var isPosting by remember { mutableStateOf(false) }
    var announcementRuleDialogMessage by remember { mutableStateOf<String?>(null) }
    var showReportDialogId by remember { mutableStateOf<String?>(null) }
    var showShareDialogId by remember { mutableStateOf<String?>(null) }
    var showShareToFriendSheet by remember { mutableStateOf(false) }
    var friends by remember { mutableStateOf<List<UserEntity>>(emptyList()) }
    var commentPost by remember { mutableStateOf<Announcement?>(null) }
    val savedPosts = remember { context.getSharedPreferences("saved_announcements", 0) }
    var savedIds by remember { mutableStateOf(savedPosts.all.keys.toSet()) }
    var localLikes by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var localCommentCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    val currentUid = AuthRepository.currentUserId()

    val userRepository = remember { UserRepository(context) }
    val currentUserFlow = remember(currentUid, userRepository) {
        if (currentUid.isNullOrBlank()) flowOf<UserEntity?>(null)
        else userRepository.getUserFlow(currentUid)
    }
    val currentUser by currentUserFlow.collectAsState(initial = null)

    LaunchedEffect(currentUid) {
        if (!currentUid.isNullOrBlank()) {
            userRepository.syncUser(currentUid)
        }
    }

    LaunchedEffect(Unit, friends) {
        if (friends.isEmpty()) {
            friends = runCatching { chatRepository.getFriendsImplementation() }.getOrDefault(emptyList())
        }
    }

    val filteredAnnouncements = remember(announcements, selectedTab, searchQuery, localLikes, localCommentCounts) {
        val type = AnnouncementType.values().getOrElse(selectedTab) { AnnouncementType.NEWS }
        announcements.map { post ->
            val liked = localLikes[post.id]
            val comments = localCommentCounts[post.id]
            post.copy(
                likes = if (liked == null) post.likes else post.likes.toMutableMap().apply { if (liked) put(currentUid ?: "local", true) else remove(currentUid ?: "local") },
                comments = if (comments == null) post.comments else post.comments.entries.take(comments).associate { it.toPair() }
            )
        }.filter {
            it.type == type &&
                (searchQuery.isBlank() ||
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.content.contains(searchQuery, ignoreCase = true))
        }
    }

    fun contactAuthorPrivately(announcement: Announcement) {
        val authorUid = announcement.authorUid
        if (authorUid.isNullOrBlank()) {
            Toast.makeText(context, "Author details unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        if (authorUid == currentUid) {
            Toast.makeText(context, "This is your post", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            try {
                if (!chatRepository.isFriend(authorUid)) {
                    Toast.makeText(context, "Add this user as a friend first to message them", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                chatRepository.createChatForFriend(authorUid, announcement.author.ifBlank { "User" })
                Toast.makeText(context, "Private chat added in Chats", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Failed to open chat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun showAnnouncementRuleDialog() {
        announcementRuleDialogMessage =
            "Posting limit reached.\n\nThis action is currently blocked by your Firebase announcement rules. Try again after your server-side reset window."
    }

    PureAnnouncementsScreen(
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        announcements = filteredAnnouncements,
        isLoading = isLoading && announcements.isEmpty(),
        currentUser = currentUser?.toPureUser(),
        onAnnouncementClick = onAnnouncementClick,
        onOpenTeamsClick = onOpenTeamsClick,
        onCreatePostClick = {
            if (currentUid.isNullOrBlank()) {
                Toast.makeText(context, "Login required to create a post", Toast.LENGTH_SHORT).show()
                return@PureAnnouncementsScreen
            }
            showCreateDialog = true
        },
        onContactAuthor = { announcement -> contactAuthorPrivately(announcement) },
        onReport = { announcement -> showReportDialogId = announcement.id },
        onShare = { announcement -> showShareDialogId = announcement.id },
        onDelete = { announcement ->
            scope.launch {
                try {
                    repository.deleteAnnouncement(announcement.id)
                    Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onLike = { announcement ->
            val wasLiked = localLikes[announcement.id]
                ?: announcement.likes.containsKey(currentUid)
            val shouldLike = !wasLiked
            // Instant feedback; network work happens after the card changes.
            localLikes = localLikes + (announcement.id to shouldLike)
            scope.launch {
                runCatching { repository.setLike(announcement.id, shouldLike) }
                    .onFailure {
                        // Restore the previous remote/cache value when Supabase rejects the write.
                        localLikes = localLikes - announcement.id
                        Toast.makeText(context, it.message ?: "Could not update like", Toast.LENGTH_SHORT).show()
                    }
            }
        },
        onComment = { announcement -> commentPost = announcement },
        onSave = { announcement ->
            val wasSaved = savedPosts.getBoolean(announcement.id, false)
            savedPosts.edit().putBoolean(announcement.id, !wasSaved).apply()
            savedIds = if (wasSaved) savedIds - announcement.id else savedIds + announcement.id
        },
        formatTime = { timestamp ->
            SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(timestamp))
        },
        renderImage = { url, modifier, scale ->
            AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale)
        },
        currentUid = currentUid
        , isLikedByCurrentUser = { post -> localLikes[post.id] ?: post.likes.containsKey(currentUid) }
        , isSaved = { post -> post.id in savedIds }
        , likeCount = { post -> post.likes.size }
        , commentCount = { post -> localCommentCounts[post.id] ?: post.comments.size }
    )

    if (showCreateDialog) {
        var imageUri by remember { mutableStateOf<Uri?>(null) }
        val imagePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? -> imageUri = uri }

        PureCreatePostDialog(
            initialType = AnnouncementType.values().getOrElse(selectedTab) { AnnouncementType.NEWS },
            onDismiss = { showCreateDialog = false },
            isLoading = isPosting,
            onPost = { title, content, type, itemName, place, time, reward, eventVenue, eventTime, eventPurpose, eventDlType, eventMode, eventMaxMembers, eventDepartments, eventLink, eventCategory ->
                isPosting = true
                scope.launch {
                    try {
                        val uid = currentUid
                        if (uid.isNullOrBlank()) {
                            Toast.makeText(context, "Login required to create a post", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        var imageUrl: String? = null
                        if (imageUri != null) {
                            val file = ImageUtils.uriToFile(context, imageUri!!)
                            val compressed = ImageUtils.compressPostImage(context, file)
                            imageUrl = ImageUtils.uploadImage(compressed)
                        }

                        val timestamp = System.currentTimeMillis()
                        val id = buildAnnouncementId(uid, timestamp)
                        val announcement = Announcement(
                            id = id,
                            title = title,
                            content = content,
                            imageUrl = imageUrl,
                            timestamp = timestamp,
                            author = currentUser?.name ?: "User",
                            authorUid = uid,
                            authorProfilePicUrl = currentUser?.profilePicUrl,
                            type = type,
                            itemName = itemName,
                            place = place,
                            time = time,
                            reward = reward,
                            eventVenue = eventVenue,
                            eventTime = eventTime,
                            eventPurpose = eventPurpose,
                            eventDlType = eventDlType,
                            eventMode = eventMode,
                            eventMaxMembers = eventMaxMembers,
                            eventDepartments = eventDepartments,
                            eventLink = eventLink,
                            eventCategory = eventCategory
                        )

                        repository.createAnnouncement(announcement)
                        showCreateDialog = false
                    } catch (e: Exception) {
                        if (e.isFirestorePermissionDenied()) {
                            showCreateDialog = false
                            showAnnouncementRuleDialog()
                        } else {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        isPosting = false
                    }
                }
            },
            onAddPhotoClick = { imagePicker.launch("image/*") },
            hasImage = imageUri != null,
            onRemoveImage = { imageUri = null },
            renderSelectedImage = { modifier ->
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = null,
                    modifier = modifier,
                    contentScale = ContentScale.Crop
                )
            }
        )
    }

    if (commentPost != null) {
        var commentText by remember(commentPost?.id) { mutableStateOf("") }
        var replyingToComment by remember(commentPost?.id) { mutableStateOf<Comment?>(null) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val clipboard = LocalClipboardManager.current
        ModalBottomSheet(
            onDismissRequest = { commentPost = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            InstagramCommentsSheet(
                comments = commentPost?.comments?.values?.sortedBy { it.timestamp }.orEmpty(),
                currentUid = currentUid,
                currentUserName = currentUser?.name.orEmpty(),
                currentUserPhoto = currentUser?.profilePicUrl,
                commentText = commentText,
                onCommentTextChange = { commentText = it },
                replyingToComment = replyingToComment,
                onCancelReply = { replyingToComment = null },
                onReply = { commentToReply ->
                    replyingToComment = commentToReply
                    val mention = "@${commentToReply.userName.ifBlank { "User" }} "
                    if (!commentText.startsWith(mention)) {
                        commentText = mention + commentText.replace(Regex("^@[^\\s]+\\s+"), "")
                    }
                },
                onDeleteComment = { commentToDelete ->
                    val post = commentPost ?: return@InstagramCommentsSheet
                    // Optimistic update
                    commentPost = post.copy(comments = post.comments - commentToDelete.id)
                    localCommentCounts = localCommentCounts + (post.id to (post.comments.size - 1).coerceAtLeast(0))
                    scope.launch {
                        runCatching { repository.deleteComment(post.id, commentToDelete.id) }
                            .onSuccess {
                                localCommentCounts = localCommentCounts - post.id
                                Toast.makeText(context, "Comment deleted", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                commentPost = post
                                localCommentCounts = localCommentCounts - post.id
                                Toast.makeText(context, "Could not delete comment: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                onReportComment = { commentToReport ->
                    val post = commentPost ?: return@InstagramCommentsSheet
                    if (currentUid != null) {
                        scope.launch {
                            runCatching {
                                repository.reportAnnouncement(
                                    announcementId = post.id,
                                    reporterUid = currentUid,
                                    reason = "Reported comment: ${commentToReport.text.take(100)}"
                                )
                            }.onSuccess {
                                Toast.makeText(context, "Comment reported", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "Could not report: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onCopyComment = { commentToCopy ->
                    clipboard.setText(AnnotatedString(commentToCopy.text))
                    Toast.makeText(context, "Comment copied", Toast.LENGTH_SHORT).show()
                },
                onPost = {
                    val post = commentPost ?: return@InstagramCommentsSheet
                    val body = commentText.trim()
                    if (body.isBlank()) return@InstagramCommentsSheet
                    val localComment = Comment(
                        id = "local-${System.currentTimeMillis()}",
                        userId = currentUid.orEmpty(),
                        userName = currentUser?.name.orEmpty().ifBlank { "User" },
                        userProfilePic = currentUser?.profilePicUrl,
                        text = body,
                        timestamp = System.currentTimeMillis()
                    )
                    // Display the new comment and its count before the request finishes.
                    commentPost = post.copy(comments = post.comments + (localComment.id to localComment))
                    localCommentCounts = localCommentCounts + (post.id to (post.comments.size + 1))
                    commentText = ""
                    replyingToComment = null
                    scope.launch {
                        runCatching { repository.addComment(post.id, body, localComment) }
                            .onSuccess {
                                // The repository has now placed the same comment in Room.
                                // Remove the UI-only override and let its cached value render.
                                localCommentCounts = localCommentCounts - post.id
                            }
                            .onFailure {
                                commentPost = post
                                localCommentCounts = localCommentCounts - post.id
                                Toast.makeText(context, it.message ?: "Could not post comment", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                renderAvatar = { url, modifier ->
                    AppAsyncImage(url, "Comment author", modifier, ContentScale.Crop)
                }
            )
        }
    }

    if (announcementRuleDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { announcementRuleDialogMessage = null },
            confirmButton = {
                TextButton(onClick = { announcementRuleDialogMessage = null }) {
                    Text("OK")
                }
            },
            title = { Text("Posting Limit Reached") },
            text = { Text(announcementRuleDialogMessage.orEmpty()) }
        )
    }

    if (showReportDialogId != null) {
        PureReportDialog(
            onDismiss = { showReportDialogId = null },
            onReport = { reason ->
                val id = showReportDialogId
                if (id != null && currentUid != null) {
                    scope.launch {
                        try {
                            repository.reportAnnouncement(id, currentUid, reason)
                            Toast.makeText(context, "Report sent", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        showReportDialogId = null
                    }
                }
            }
        )
    }

    if (showShareDialogId != null) {
        val announcement = announcements.find { it.id == showShareDialogId }
        val link = showShareDialogId?.let { PostLinks.buildPostLink(it) }.orEmpty()
        val clipboardManager = LocalClipboardManager.current
        
        PureSharePostDialog(
            link = link,
            onDismiss = { showShareDialogId = null },
            onCopy = {
                clipboardManager.setText(AnnotatedString(link))
                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
            },
            onShareViaApp = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        PostLinks.buildShareMessage(announcement?.title, showShareDialogId ?: return@apply)
                    )
                }
                context.startActivity(Intent.createChooser(intent, "Share Post"))
            },
            onShareToFriend = {
                showShareToFriendSheet = true
            }
        )
    }

    if (showShareToFriendSheet && showShareDialogId != null) {
        val pureFriends = remember(friends) { friends.map { it.toPureUser() } }
        val announcement = announcements.find { it.id == showShareDialogId }
        PureNewChatBottomSheet(
            title = "Send Post To Friend",
            friends = pureFriends,
            onDismiss = { showShareToFriendSheet = false },
            onFriendClick = { friend ->
                val postId = showShareDialogId ?: return@PureNewChatBottomSheet
                scope.launch {
                    try {
                        val chatId = chatRepository.createChatForFriend(friend.uid, friend.name)
                        chatRepository.sendMessage(
                            chatId = chatId,
                            content = PostLinks.buildShareMessage(announcement?.title, postId),
                            friendUid = friend.uid
                        )
                        Toast.makeText(context, "Post sent to ${friend.name}", Toast.LENGTH_SHORT).show()
                        showShareToFriendSheet = false
                        showShareDialogId = null
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Failed to send post", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            renderImage = { url, modifier, scale ->
                AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale)
            }
        )
    }
}
