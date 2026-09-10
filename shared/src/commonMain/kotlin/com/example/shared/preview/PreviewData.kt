package com.example.shared.preview

import com.example.shared.model.Announcement
import com.example.shared.model.AnnouncementType
import com.example.shared.model.PureChat
import com.example.shared.model.PureUser
import com.example.shared.repository.IChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

const val PREVIEW_UID = "ios-preview-user"
const val PREVIEW_NOW_MS = 1711188360000L

val previewAnnouncements = listOf(
    Announcement(
        id = "news-own-1",
        title = "Welcome to my profile",
        content = "This post is shown in the profile tab so your own posts appear on iOS.",
        timestamp = PREVIEW_NOW_MS - 1_200_000L,
        author = "Campus Explorer",
        authorUid = PREVIEW_UID,
        type = AnnouncementType.NEWS,
        viewCount = 9
    ),
    Announcement(
        id = "news-1",
        title = "Library hours extended",
        content = "The central library will stay open until 10 PM during mid-semester exams.",
        timestamp = PREVIEW_NOW_MS - 3_600_000L,
        author = "CU Admin",
        authorUid = "admin-cu",
        type = AnnouncementType.NEWS,
        shareCount = 2,
        viewCount = 24
    ),
    Announcement(
        id = "lost-1",
        title = "Found ID card",
        content = "Found near Block A canteen. Reach out if it belongs to you.",
        timestamp = PREVIEW_NOW_MS - 7_200_000L,
        author = "Riya Sharma",
        authorUid = "student-riya",
        type = AnnouncementType.LOST_AND_FOUND,
        itemName = "University ID Card",
        place = "Block A Canteen",
        time = "Today, 12:20 PM",
        reward = "Coffee treat",
        viewCount = 16
    ),
    Announcement(
        id = "event-1",
        title = "Hackathon registrations open",
        content = "Register your team before Friday to join the 24-hour coding sprint.",
        timestamp = PREVIEW_NOW_MS - 10_800_000L,
        author = "Innovation Club",
        authorUid = "club-innovation",
        type = AnnouncementType.EVENT,
        eventVenue = "Seminar Hall 2",
        eventTime = "Friday, 9:00 AM",
        eventPurpose = "Build campus tools",
        eventDlType = "Any department",
        eventMode = "Team",
        eventMaxMembers = 4,
        eventDepartments = "CSE, AI, IT",
        viewCount = 31
    )
)

class PreviewChatRepository : IChatRepository {
    private val chatsState = MutableStateFlow(
        listOf(
            PureChat(
                chatId = "chat-1",
                friendUid = "friend-1",
                friendName = "Aarav Mehta",
                lastMessage = "Are you coming to the lab today?",
                lastMessageTime = PREVIEW_NOW_MS - 900_000L,
                unreadCount = 2
            ),
            PureChat(
                chatId = "chat-2",
                friendUid = "friend-2",
                friendName = "Priya Saini",
                lastMessage = "Sent you the notes.",
                lastMessageTime = PREVIEW_NOW_MS - 3_600_000L,
                unreadCount = 1
            )
        )
    )

    override val allChats: Flow<List<PureChat>> = chatsState

    override suspend fun getFriends(): List<PureUser> = emptyList()

    override suspend fun getNearbyUsers(lat: Double, lng: Double): List<PureUser> = emptyList()

    override suspend fun sendFriendRequest(email: String) = Unit

    override suspend fun createChatForFriend(friendUid: String, friendName: String): String {
        val chatId = "chat-${chatsState.value.size + 1}"
        chatsState.update { current ->
            listOf(
                PureChat(
                    chatId = chatId,
                    friendUid = friendUid,
                    friendName = friendName,
                    lastMessage = "New conversation started",
                    lastMessageTime = PREVIEW_NOW_MS,
                    unreadCount = 0
                )
            ) + current
        }
        return chatId
    }

    override suspend fun toggleGhostMode(isGhostMode: Boolean) = Unit

    override fun listenToUserChats() = Unit

    override fun startSync() = Unit

    override suspend fun getChatId(myUid: String, friendUid: String): String =
        chatsState.value.firstOrNull { it.friendUid == friendUid }?.chatId ?: "${myUid}_$friendUid"

    override fun getCurrentUserUid(): String = PREVIEW_UID

    fun markChatRead(chatId: String) {
        chatsState.update { current ->
            current.map { chat ->
                if (chat.chatId == chatId) chat.copy(unreadCount = 0) else chat
            }
        }
    }

    fun addPreviewChat() {
        val nextIndex = chatsState.value.size + 1
        chatsState.update { current ->
            listOf(
                PureChat(
                    chatId = "chat-$nextIndex",
                    friendUid = "friend-$nextIndex",
                    friendName = "Preview Friend $nextIndex",
                    lastMessage = "This chat is now connected on iOS.",
                    lastMessageTime = PREVIEW_NOW_MS,
                    unreadCount = 1
                )
            ) + current
        }
    }

    fun removeChat(chatId: String) {
        chatsState.update { current -> current.filterNot { it.chatId == chatId } }
    }

    fun totalUnreadCount(): Int = chatsState.value.sumOf { it.unreadCount }
}

fun previewRelativeTime(timestamp: Long, nowMs: Long = PREVIEW_NOW_MS): String {
    val minutes = ((nowMs - timestamp).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1_440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1_440}d ago"
    }
}
