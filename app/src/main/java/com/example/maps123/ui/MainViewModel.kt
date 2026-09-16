package com.example.maps123.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.shared.Route
import com.example.shared.RoutesData
import com.example.shared.utils.*
import com.example.shared.repository.RouteIndex
import com.example.maps123.data.local.UserEntity
import com.example.maps123.data.repository.UserRepository
import com.example.maps123.utils.VoiceGuideWrapper
import com.example.maps123.utils.toLatLng
import com.example.maps123.utils.toGeoPoint
import com.example.maps123.ui.theme.ThemePrefs
import com.google.android.gms.maps.model.LatLng
import com.example.maps123.data.repository.AuthRepository
import com.example.maps123.data.supabase.SupabaseProvider
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

import com.example.maps123.data.repository.ChatRepository
import com.example.maps123.data.repository.AnnouncementRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val _friends = MutableStateFlow<List<UserEntity>>(emptyList())
    val friends: StateFlow<List<UserEntity>> = _friends.asStateFlow()

    val userRepository = UserRepository(application)
    val chatRepository = ChatRepository(application)
    val announcementRepository = AnnouncementRepository(application)
    private val themePrefs = ThemePrefs(application)
    private var voiceGuide: VoiceGuideWrapper? = null
    private val smartController = SmartInstructionController()
    
    private var userSessionJob: Job? = null
    private var announcementWatchJob: Job? = null
    private var isChatInitialized = false

    init {
        Log.d("DEBUG_VM", "ViewModel INIT called")
        loadRoutes()
        
        // Supabase restores a persisted session asynchronously. Always start
        // initialization and let it await Auth before deciding whether a user
        // is signed in. A synchronous currentUserOrNull() check loses chat
        // recovery when Android cold-starts from a notification.
        initializeChat()
        
        viewModelScope.launch {
            themePrefs.isVoiceEnabled.collect { enabled ->
                _uiState.update { it.copy(isAudioEnabled = enabled) }
            }
        }
    }

    fun initializeChat() {
        Log.d("DEBUG_VM", "initializeChat CALLED")
        if (isChatInitialized) return
        isChatInitialized = true

        userSessionJob?.cancel()
        
        userSessionJob = viewModelScope.launch {
            try {
                val auth = SupabaseProvider.client.auth
                auth.awaitInitialization()
                if (auth.currentUserOrNull() == null) {
                    isChatInitialized = false
                    return@launch
                }
                com.example.maps123.data.firebase.FcmTokenSyncManager.syncCurrentToken(getApplication())
                startAnnouncementWatch(auth.currentUserOrNull()?.id.orEmpty())
                Log.d("DEBUG_VM", "startSync CALLED")
                chatRepository.startSync()
                _friends.value = runCatching {
                    chatRepository.getFriendsImplementation(forceRefresh = false)
                }.getOrDefault(emptyList())

                runCatching { chatRepository.refreshUserChatsNow(forceRefresh = true) }
                    .onFailure { Log.w("MainViewModel", "Initial chat refresh failed", it) }

                launch {
                    chatRepository.getTotalUnreadCount().collect { count ->
                        _uiState.update { it.copy(unreadMessageCount = count) }
                    }
                }

                launch {
                    chatRepository.getPendingRequestCount().collect { count ->
                        _uiState.update { it.copy(pendingFriendRequestCount = count) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                isChatInitialized = false
                Log.e("MainViewModel", "Chat initialization failed", e)
            }
        }
    }

    fun resetChatSession() {
        userSessionJob?.cancel()
        userSessionJob = null
        isChatInitialized = false
        announcementWatchJob?.cancel()
        announcementWatchJob = null
        _friends.value = emptyList()
        chatRepository.clearAllListeners()
        _uiState.update {
            it.copy(
                unreadMessageCount = 0,
                pendingFriendRequestCount = 0,
                newAnnouncementCount = 0
            )
        }
    }

    fun pauseRealtimeSync() {
        chatRepository.clearAllListeners()
        announcementWatchJob?.cancel()
        announcementWatchJob = null
        announcementRepository.stopSync()
        isChatInitialized = false
    }

    fun resumeRealtimeSync() {
        initializeChat()
    }

    /**
     * Keeps the 20-post Room window current while the app is foregrounded,
     * regardless of whether Map, Chat, News, or another screen is visible.
     * FCM remains responsible when the process is backgrounded.
     */
    private fun startAnnouncementWatch(currentUid: String) {
        if (currentUid.isBlank() || announcementWatchJob?.isActive == true) return
        announcementWatchJob = viewModelScope.launch {
            val knownIds = announcementRepository.getCachedAnnouncements(20)
                .mapTo(mutableSetOf()) { it.id }
            var baselineReady = knownIds.isNotEmpty()
            announcementRepository.getAnnouncementsFlow(20).collect { posts ->
                if (!baselineReady) {
                    if (posts.isNotEmpty()) {
                        knownIds.addAll(posts.map { it.id })
                        baselineReady = true
                    }
                    return@collect
                }
                val newPosts = posts.filter { it.id !in knownIds && it.authorUid != currentUid }
                knownIds.addAll(posts.map { it.id })
                if (newPosts.isNotEmpty()) {
                    _uiState.update { state ->
                        state.copy(newAnnouncementCount = state.newAnnouncementCount + newPosts.size)
                    }
                    newPosts.asReversed().forEach { post ->
                        CampusUpdateBus.publish(
                            CampusUpdateEvent(
                                key = "post:${post.id}",
                                message = "New campus post: ${post.title.ifBlank { "Open News to view it" }}",
                                postId = post.id
                            )
                        )
                    }
                }
            }
        }
    }

    fun refreshFriends(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _friends.value = runCatching {
                chatRepository.getFriendsImplementation(forceRefresh = forceRefresh)
            }.getOrDefault(emptyList())
        }
    }

    fun addFriendLocally(friend: UserEntity) {
        _friends.update { existing ->
            (existing.filterNot { it.uid == friend.uid } + friend)
                .sortedBy { it.name.lowercase() }
        }
    }

    fun removeFriendLocally(friendUid: String) {
        _friends.update { existing -> existing.filterNot { it.uid == friendUid } }
    }

    fun setVoiceGuide(guide: VoiceGuideWrapper) {
        voiceGuide = guide
    }

    // ---------------- ROUTE LOADING (COMMON MAIN) ----------------

    private fun loadRoutes() {
        val loaded = RoutesData.allRoutes
        val index = RouteIndex(loaded)

        val endMap = mutableMapOf<String, MutableList<Route>>()
        for (r in loaded) {
            val parts = r.name.split("→", "->").map { it.trim() }
            val end = parts.getOrNull(1)?.lowercase()
            if (!end.isNullOrEmpty()) {
                endMap.getOrPut(end) { mutableListOf() }.add(r)
            }
        }

        _uiState.update {
            it.copy(
                allRoutes = loaded,
                routeIndex = index,
                endIndex = endMap
            )
        }
    }

    // ---------------- LOCATION UPDATES ----------------

    fun onLocationUpdate(lat: Double, lng: Double) {
        val loc = LatLng(lat, lng)
        _uiState.update { it.copy(userLocation = loc) }

        val uid = AuthRepository.currentUserId()
        if (!uid.isNullOrBlank()) {
            viewModelScope.launch {
                userRepository.updateUserLocation(uid, lat, lng)
            }
        }

        val state = _uiState.value
        if (state.isNavigating && state.activeRoute != null) {
            updateNavigation(loc, state.activeRoute, state.nearestIndex)
        }
    }

    // ---------------- NAVIGATION CORE ----------------

    private fun updateNavigation(
        loc: LatLng,
        route: Route,
        currentNearestIndex: Int
    ) {
        val searchRadius = 10
        val end = (currentNearestIndex + searchRadius)
            .coerceAtMost(route.points.size - 1)

        var bestIndex = currentNearestIndex
        var minDist = Double.MAX_VALUE

        for (i in currentNearestIndex..end) {
            val p = route.points[i]
            val d = GeoUtils.distanceMeters(loc.toGeoPoint(), p)
            if (d < minDist) {
                minDist = d
                bestIndex = i
            }
        }

        val idx = bestIndex.coerceIn(0, route.points.lastIndex)

        val prev = route.points.getOrNull(idx - 1)
        val curr = route.points.getOrNull(idx)
        val next = route.points.getOrNull(idx + 1)

        val turn = if (prev != null && curr != null && next != null) {
            NavigationUtils.getTurnInstruction(prev, curr, next)
        } else "Continue straight"

        val distToNext = next?.let { GeoUtils.distanceMeters(loc.toGeoPoint(), it).toInt() } ?: 0

        val instruction = if (turn == "Continue straight") {
            "Continue straight for $distToNext meters"
        } else {
            buildInstructionMessage(turn, distToNext)
        }

        val distanceLeft =
            RouteHelpers.distanceToEnd(loc.toGeoPoint(), route, max(0, idx))

        if (ArrivalChecker.hasArrived(loc.toGeoPoint(), route.points.last())) {
            voiceGuide?.speak("You have arrived at ${route.name}")
            _uiState.update {
                it.copy(
                    isNavigating = false,
                    activeRoute = null,
                    instructionText = "You have arrived 🎉",
                    distanceLeft = 0.0,
                    nearestIndex = 0
                )
            }
            return
        }

        if (_uiState.value.isAudioEnabled &&
            smartController.shouldSpeak(instruction, System.currentTimeMillis())
        ) {
            voiceGuide?.speak(instruction)
        }

        _uiState.update {
            it.copy(
                nearestIndex = bestIndex,
                instructionText = instruction,
                distanceLeft = distanceLeft
            )
        }
    }

    private fun buildInstructionMessage(turn: String, dist: Int): String =
        when {
            dist > 80 -> "$turn in $dist meters"
            dist > 40 -> "In $dist meters, $turn"
            dist > 15 -> "Get ready to $turn"
            else -> "$turn now"
        }

    // ---------------- CONTROLS ----------------

    fun startNavigation(route: Route) {
        val loc = _uiState.value.userLocation
        val startIdx = if (loc != null)
            RouteHelpers.findNearestSegmentIndex(loc.toGeoPoint(), route)
        else 0

        _uiState.update {
            it.copy(
                activeRoute = route,
                isNavigating = true,
                nearestIndex = startIdx,
                showRoutePicker = false,
                instructionText = "Starting navigation..."
            )
        }
    }

    fun stopNavigation() {
        _uiState.update {
            it.copy(
                isNavigating = false,
                activeRoute = null,
                instructionText = "Navigation stopped",
                nearestIndex = 0
            )
        }
    }

    fun toggleAudio() {
        val newState = !_uiState.value.isAudioEnabled
        _uiState.update { it.copy(isAudioEnabled = newState) }
        viewModelScope.launch {
            themePrefs.saveVoiceEnabled(newState)
        }
    }

    fun showRoutePicker(dest: String? = null) {
        _uiState.update {
            it.copy(showRoutePicker = true, initialRoutePickerDestination = dest)
        }
    }

    fun hideRoutePicker() {
        _uiState.update {
            it.copy(showRoutePicker = false, initialRoutePickerDestination = null)
        }
    }

    fun selectRouteFromCurrentLocation(destination: String) {
        val loc = _uiState.value.userLocation
        val key = destination.lowercase().trim()
        val candidates = _uiState.value.endIndex[key].orEmpty()

        if (loc == null) {
            _uiState.update {
                it.copy(
                    instructionText = "Current location not available yet. Please wait and try again.",
                    showRoutePicker = true
                )
            }
            return
        }

        if (candidates.isEmpty()) {
            _uiState.update {
                it.copy(
                    instructionText = "No route found to $destination",
                    showRoutePicker = false
                )
            }
            return
        }

        val selected = candidates.minByOrNull { GeoUtils.minDistanceToRoute(loc.toGeoPoint(), it) }

        if (selected != null) {
            startNavigation(selected)
        } else {
            _uiState.update {
                it.copy(
                    instructionText = "No suitable route found from your current location",
                    showRoutePicker = false
                )
            }
        }
    }

    fun onCameraMoved(loc: LatLng) {
        _uiState.update { it.copy(lastCameraLocation = loc) }
    }

    fun markAnnouncementsAsSeen() {
        viewModelScope.launch {
            themePrefs.saveLastAnnouncementTime(System.currentTimeMillis())
            _uiState.update { it.copy(newAnnouncementCount = 0) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        announcementRepository.stopSync()
        resetChatSession()
    }
}
