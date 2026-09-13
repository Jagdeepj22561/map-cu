package com.example.maps123.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.maps123.ui.components.AppAsyncImage
import com.example.maps123.data.local.*
import com.example.maps123.data.repository.UserRepository
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.maps123.data.repository.AnnouncementRepository
import com.example.maps123.utils.DateUtils
import com.example.shared.model.Announcement
import com.example.shared.model.Comment
import com.example.maps123.ui.components.InstagramCommentsSheet
import com.example.maps123.utils.ImageUtils
import com.example.maps123.utils.PostLinks
import com.example.maps123.data.repository.AuthRepository
import com.example.shared.ui.*
import kotlinx.coroutines.launch
import java.util.*
import androidx.compose.runtime.mutableIntStateOf
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: UserEntity,
    announcementRepository: AnnouncementRepository,
    onBack: () -> Unit,
    onFindFriends: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(user.name) }
    var editing by remember { mutableStateOf(false) }
    var profilePic by remember { mutableStateOf(user.profilePicUrl) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var ghostMode by remember { mutableStateOf(user.ghostMode) }

    // Academic details
    var course by remember { mutableStateOf(user.course) }
    var year by remember { mutableStateOf(user.year) }
    var semester by remember { mutableStateOf(user.semester) }
    var university by remember { mutableStateOf(user.university) }

    // Personal details
    var dob by remember { mutableStateOf(user.dob) }
    var phoneNumber by remember { mutableStateOf(user.phoneNumber) }
    var gender by remember { mutableStateOf(user.gender) }

    // Social links
    var instagramLink by remember { mutableStateOf(user.instagramLink) }
    var snapchatLink by remember { mutableStateOf(user.snapchatLink) }
    var linkedinLink by remember { mutableStateOf(user.linkedinLink) }

    val userRepository = remember { UserRepository(context) }
    val announcements by remember(announcementRepository) {
        announcementRepository.getAnnouncementsFlow(limit = 50)
    }.collectAsState(initial = emptyList())
    val isAnnouncementsLoading by announcementRepository.isInitialLoadRunning.collectAsState()
    val userAnnouncements = remember(announcements, user.uid) {
        announcements.filter { it.authorUid == user.uid }
    }
    val savedPostPrefs = remember { context.getSharedPreferences("saved_announcements", 0) }
    val savedAnnouncements = remember(announcements) {
        announcements.filter { savedPostPrefs.getBoolean(it.id, false) }
    }
    var showReportDialogId by remember { mutableStateOf<String?>(null) }
    var showShareDialogId by remember { mutableStateOf<String?>(null) }
    var localLikes by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var savedIds by remember { mutableStateOf(savedPostPrefs.all.keys.toSet()) }

    var commentPost by remember { mutableStateOf<Announcement?>(null) }
    var localCommentCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    val currentUid = AuthRepository.currentUserId()
    val canEditOwnProfile = !currentUid.isNullOrBlank() && user.uid == currentUid
    val isReadOnly = !canEditOwnProfile

    val currentUserProfile by remember(currentUid) {
        if (!currentUid.isNullOrBlank()) userRepository.getUserFlow(currentUid) else kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = if (canEditOwnProfile) user else null)

    LaunchedEffect(canEditOwnProfile, currentUid) {
        if (!canEditOwnProfile) {
            editing = false
        }
        if (!currentUid.isNullOrBlank()) {
            userRepository.syncUser(currentUid)
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val file = ImageUtils.uriToFile(context, uri)
                val url = ImageUtils.uploadImage(file)
                profilePic = url
                Toast.makeText(context, "Updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    PureProfileScreen(
        isReadOnly = isReadOnly,
        isLoading = false,
        isBlocked = false,
        isEditing = editing,

        onToggleEditing = { if (canEditOwnProfile) editing = it },
        onBack = onBack,
        onSave = {
            if (!canEditOwnProfile) return@PureProfileScreen
            scope.launch {
                try {
                    val updatedUser = user.copy(
                        name = name,
                        phoneNumber = phoneNumber,
                        year = year,
                        semester = semester,
                        course = course,
                        university = university,
                        dob = dob,
                        gender = gender,
                        profilePicUrl = profilePic,
                        instagramLink = instagramLink,
                        snapchatLink = snapchatLink,
                        linkedinLink = linkedinLink
                    )
                    userRepository.updateUserProfile(updatedUser)
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                    editing = false
                } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: "Save failed", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onImageClick = {
            if (canEditOwnProfile) {
                picker.launch("image/*")
            }
        },
        onFindFriends = onFindFriends,
        onShareProfile = {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Connect with $name on Campus Map")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share profile"))
        },

        name = name,
        onNameChange = { name = it },
        email = user.email,
        profilePicUrl = profilePic,

        course = course,
        onCourseChange = { course = it },
        year = year,
        onYearChange = { year = it },
        semester = semester,
        onSemesterChange = { semester = it },
        university = university,
        onUniversityChange = { university = it },
        dob = dob,
        onDobChange = { dob = it },
        gender = gender,
        onGenderChange = { gender = it },
        phoneNumber = phoneNumber,
        onPhoneNumberChange = { phoneNumber = it },

        instagramLink = instagramLink,
        onInstagramChange = { instagramLink = it },
        snapchatLink = snapchatLink,
        onSnapchatChange = { snapchatLink = it },
        linkedinLink = linkedinLink,
        onLinkedinChange = { linkedinLink = it },

        onSocialClick = { type, link ->
            if (link.isNotBlank()) {
                val uri = if (link.startsWith("http")) Uri.parse(link) 
                          else Uri.parse("https://$link")
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                }
            }
        },
        ghostMode = ghostMode,
        onGhostModeChange = { enabled ->
            if (!canEditOwnProfile) return@PureProfileScreen
            ghostMode = enabled
            scope.launch {
                runCatching {
                    userRepository.updateUserProfile(
                        user.copy(
                            name = name,
                            phoneNumber = phoneNumber,
                            year = year,
                            semester = semester,
                            course = course,
                            university = university,
                            dob = dob,
                            gender = gender,
                            profilePicUrl = profilePic,
                            instagramLink = instagramLink,
                            snapchatLink = snapchatLink,
                            linkedinLink = linkedinLink,
                            ghostMode = enabled
                        )
                    )
                }.onSuccess {
                    Toast.makeText(context, if (enabled) "Ghost mode enabled" else "Ghost mode disabled", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    ghostMode = !enabled
                    Toast.makeText(context, it.message ?: "Could not update ghost mode", Toast.LENGTH_SHORT).show()
                }
            }
        },

        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        tabs = if (canEditOwnProfile) listOf("Details", "Posts", "Saved") else listOf("Details", "Posts"),

        renderPosts = {
            if (isAnnouncementsLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (if (selectedTab == 2 && canEditOwnProfile) savedAnnouncements.isEmpty() else userAnnouncements.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (selectedTab == 2 && canEditOwnProfile) "No saved posts yet" else "No posts yet")
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val postsToShow = if (selectedTab == 2 && canEditOwnProfile) savedAnnouncements else userAnnouncements
                    postsToShow.forEach { announcement ->
                        val isLiked = localLikes[announcement.id]
                            ?: announcement.likes.containsKey(currentUid)
                        val isSaved = announcement.id in savedIds
                        val likeCount = announcement.likes.size + (
                            if (isLiked && !announcement.likes.containsKey(currentUid)) 1
                            else if (!isLiked && announcement.likes.containsKey(currentUid)) -1
                            else 0
                        )
                        val commentCount = localCommentCounts[announcement.id] ?: announcement.comments.size

                        PureAnnouncementCard(
                            announcement = announcement,
                            currentUid = currentUid,
                            isLikedByCurrentUser = isLiked,
                            isSaved = isSaved,
                            likeCount = likeCount,
                            commentCount = commentCount,
                            onLike = {
                                val wasLiked = localLikes[announcement.id]
                                    ?: announcement.likes.containsKey(currentUid)
                                localLikes = localLikes + (announcement.id to !wasLiked)
                                scope.launch {
                                    runCatching { announcementRepository.setLike(announcement.id, !wasLiked) }
                                        .onFailure {
                                            localLikes = localLikes - announcement.id
                                            Toast.makeText(context, it.message ?: "Could not update like", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            },
                            onComment = { commentPost = announcement },
                            onClick = { commentPost = announcement },
                            onSave = {
                                val wasSaved = savedPostPrefs.getBoolean(announcement.id, false)
                                savedPostPrefs.edit().putBoolean(announcement.id, !wasSaved).apply()
                                savedIds = if (wasSaved) savedIds - announcement.id else savedIds + announcement.id
                            },
                            onReport = { showReportDialogId = announcement.id },
                            onShare = { showShareDialogId = announcement.id },
                            onDelete = {
                                scope.launch {
                                    runCatching { announcementRepository.deleteAnnouncement(announcement.id) }
                                        .onSuccess { Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show() }
                                        .onFailure { Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show() }
                                }
                            },
                            formatTime = { timestamp -> DateUtils.getRelativeTime(timestamp) },
                            renderImage = { url, modifier, scale ->
                                AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale)
                            }
                        )
                    }
                }
            }
        },

        renderImage = { url, modifier ->
            AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
        }
    )

    if (commentPost != null) {
        var commentText by remember(commentPost?.id) { mutableStateOf("") }
        var replyingToComment by remember(commentPost?.id) { mutableStateOf<Comment?>(null) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val authorName = currentUserProfile?.name?.ifBlank { user.name } ?: user.name
        val authorPhoto = currentUserProfile?.profilePicUrl ?: user.profilePicUrl
        val clipboard = LocalClipboardManager.current
        ModalBottomSheet(
            onDismissRequest = { commentPost = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            InstagramCommentsSheet(
                comments = commentPost?.comments?.values?.sortedBy { it.timestamp }.orEmpty(),
                currentUid = currentUid,
                currentUserName = authorName,
                currentUserPhoto = authorPhoto,
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
                    commentPost = post.copy(comments = post.comments - commentToDelete.id)
                    localCommentCounts = localCommentCounts + (post.id to (post.comments.size - 1).coerceAtLeast(0))
                    scope.launch {
                        runCatching { announcementRepository.deleteComment(post.id, commentToDelete.id) }
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
                                announcementRepository.reportAnnouncement(
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
                        userName = authorName.ifBlank { "User" },
                        userProfilePic = authorPhoto,
                        text = body,
                        timestamp = System.currentTimeMillis()
                    )
                    // Display comment and update count optimistically
                    commentPost = post.copy(comments = post.comments + (localComment.id to localComment))
                    localCommentCounts = localCommentCounts + (post.id to (post.comments.size + 1))
                    commentText = ""
                    replyingToComment = null
                    scope.launch {
                        runCatching { announcementRepository.addComment(post.id, body, localComment) }
                            .onSuccess {
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
                    AppAsyncImage(model = url, contentDescription = "Comment author", modifier = modifier, contentScale = ContentScale.Crop)
                }
            )
        }
    }

    if (showReportDialogId != null) {
        PureReportDialog(
            onDismiss = { showReportDialogId = null },
            onReport = { reason ->
                val id = showReportDialogId
                if (id != null && currentUid != null) {
                    scope.launch {
                        runCatching { announcementRepository.reportAnnouncement(id, currentUid, reason) }
                            .onSuccess { Toast.makeText(context, "Report sent", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show() }
                        showReportDialogId = null
                    }
                }
            }
        )
    }

    if (showShareDialogId != null) {
        val link = showShareDialogId?.let { PostLinks.buildPostLink(it) }.orEmpty()
        val shareAnnouncement = announcements.find { it.id == showShareDialogId }
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
                        PostLinks.buildShareMessage(shareAnnouncement?.title, showShareDialogId ?: return@apply)
                    )
                }
                context.startActivity(Intent.createChooser(intent, "Share Post"))
            }
        )
    }
}
