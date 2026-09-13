package com.example.maps123.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import com.example.maps123.data.local.AppDatabase
import com.example.maps123.data.local.FriendRequestEntity
import com.example.maps123.data.local.UserEntity
import com.example.maps123.data.repository.ChatRepository
import com.example.maps123.data.repository.UserRepository
import com.example.maps123.ui.MainViewModel
import com.example.maps123.ui.theme.CampusTheme
import com.example.maps123.ui.theme.ThemePrefs
import com.example.shared.utils.GeoUtils
import com.example.maps123.utils.LocationHelper
import com.example.maps123.utils.toGeoPoint
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.example.maps123.data.repository.AuthRepository
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.shared.RoutesData

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.shared.PlaceCategory
import com.example.shared.campusPlaces
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.graphicsLayer


import com.example.shared.ui.*
import com.example.shared.model.PureUser
import com.example.shared.model.PureFriendRequest
import com.example.maps123.utils.DateUtils
import com.example.maps123.utils.toPureUser
import com.example.maps123.utils.toPureFriendRequest
import com.example.maps123.ui.components.AppAsyncImage

private fun formatFriendRequestError(message: String?): String {
    val text = message?.trim().orEmpty()
    return when {
        text.contains("already sent you a friend request", ignoreCase = true) -> text
        text.contains("bidirectional pending", ignoreCase = true) -> "This user has already sent you a friend request."
        text.contains("already been sent", ignoreCase = true) -> "Friend request already sent."
        text.contains("already friends", ignoreCase = true) -> "You are already friends with this user."
        text.contains("yourself", ignoreCase = true) -> "You cannot send a friend request to yourself."
        text.contains("not found", ignoreCase = true) -> "No user found with that email."
        text.contains("permission", ignoreCase = true) -> "Permission denied while sending friend request."
        text.isBlank() -> "Failed to send friend request."
        else -> text
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    isGuest: Boolean,
    pendingChatId: String? = null,
    onLoginRequested: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    // Navigation state
    var currentScreen by rememberSaveable { mutableStateOf(PureAppScreen.MAP) }
    var previousScreen by rememberSaveable { mutableStateOf(PureAppScreen.MAP) }
    var announcementTab by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf<PlaceCategory?>(null) }
    var selectedAnnouncementId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAnnouncementInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var announcementOpenedFromChat by rememberSaveable { mutableStateOf(false) }

    var requests by remember { mutableStateOf<List<FriendRequestEntity>>(emptyList()) }
    var currentUser by remember { mutableStateOf<UserEntity?>(null) }
    var selectedFriendProfile by remember { mutableStateOf<UserEntity?>(null) }
    var isCurrentUserLoading by remember { mutableStateOf(false) }
    var currentUserLoadError by remember { mutableStateOf<String?>(null) }
    var showFriendManager by remember { mutableStateOf(false) }
    var pendingRequestEmails by remember { mutableStateOf<Set<String>>(emptySet()) }

    val userRepository = viewModel.userRepository
    val chatRepository = viewModel.chatRepository
    val announcementRepository = viewModel.announcementRepository
    val db = AppDatabase.getInstance(context)
    val currentAuthUid = AuthRepository.currentUserId()
    val currentUserFlow = remember(currentAuthUid, userRepository) {
        if (currentAuthUid.isNullOrBlank()) flowOf<UserEntity?>(null)
        else userRepository.getUserFlow(currentAuthUid)
    }
    val cachedCurrentUser by currentUserFlow.collectAsState(initial = null)
    val friends by viewModel.friends.collectAsState()

    LaunchedEffect(Unit) {
        if (!isGuest) {
            runCatching { pendingRequestEmails = chatRepository.getOutgoingPendingRequestEmails() }
        }
    }

    LaunchedEffect(showFriendManager, isGuest) {
        if (!isGuest && showFriendManager) {
            runCatching { chatRepository.refreshFriendRequestsNow(forceRefresh = true) }
        }
    }

    suspend fun loadCurrentUser() {
        val authUserId = AuthRepository.currentUserId() ?: run {
            currentUser = null
            currentUserLoadError = "No signed-in user found."
            return
        }
        if (isCurrentUserLoading) return

        isCurrentUserLoading = true
        currentUserLoadError = null

        val fallbackUser = UserEntity(
            uid = authUserId,
            email = AuthRepository.currentUserEmail().orEmpty(),
            name = AuthRepository.currentUserEmail()?.substringBefore("@").orEmpty()
        )

        val loadedUser = runCatching {
            userRepository.loadUser(authUserId)
        }
            .onFailure { currentUserLoadError = it.message ?: "Failed to load profile." }
            .getOrNull()

        currentUser = loadedUser ?: currentUser ?: fallbackUser
        isCurrentUserLoading = false
    }

    LaunchedEffect(cachedCurrentUser, isGuest) {
        if (!isGuest && cachedCurrentUser != null) {
            currentUser = cachedCurrentUser
        }
    }

    // Sync friends & requests
    LaunchedEffect(isGuest) {
        if (isGuest) {
            currentUser = null
            currentUserLoadError = null
            requests = emptyList()
            return@LaunchedEffect
        }

        loadCurrentUser()

        launch {
            db.chatDao().getPendingRequests().collectLatest { requests = it }
        }
    }

    LaunchedEffect(currentScreen) {
        if (!isGuest && currentScreen == PureAppScreen.PROFILE) {
            loadCurrentUser()
        }
    }

    // Chat navigation
    var selectedChatId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFriendUid by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFriendName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFriendPicUrl by rememberSaveable { mutableStateOf<String?>(null) }

    // Handle notification deep link to open specific chat
    LaunchedEffect(pendingChatId, friends) {
        if (pendingChatId != null && pendingChatId.isNotBlank() && friends.isNotEmpty()) {
            val chat = runCatching {
                AppDatabase.getInstance(context).chatDao().getChat(pendingChatId)
            }.getOrNull()
            
            if (chat != null) {
                selectedChatId = pendingChatId
                selectedFriendUid = chat.friendUid
                selectedFriendName = chat.friendName
                selectedFriendPicUrl = chat.friendProfilePicUrl
                currentScreen = PureAppScreen.CHAT
            }
        }
    }

    var chatTabSelection by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(currentScreen, selectedFriendUid) {
        if (currentScreen != PureAppScreen.FRIEND_PROFILE || selectedFriendUid.isNullOrBlank()) {
            selectedFriendProfile = null
            return@LaunchedEffect
        }

        val uid = selectedFriendUid ?: return@LaunchedEffect

        if (selectedFriendProfile == null) {
            selectedFriendProfile = friends.find { it.uid == uid }

            if (selectedFriendProfile != null) {
                return@LaunchedEffect
            }

            val fetched = runCatching { userRepository.loadUser(uid) }.getOrNull()

            if (fetched != null) {
                selectedFriendProfile = fetched
            } else if (selectedFriendProfile == null) {
                currentUserLoadError = "Failed to load friend profile."
            }
        }
    }

    // Theme
    val themePrefs = remember { ThemePrefs(context) }
    val isDarkMode by themePrefs.isDarkMode.collectAsState(initial = false)
    val isVoiceEnabled by themePrefs.isVoiceEnabled.collectAsState(initial = false)
    val isGhostMode by themePrefs.isGhostMode.collectAsState(initial = false)

    // Camera
    val defaultCamera = LatLng(30.771450, 76.578228)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCamera, 16f)
    }

    // Location
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationHelper = remember { LocationHelper(fusedLocationClient) }

    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val hasLocationPermission = fineGranted || coarseGranted

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            locationHelper.startLocationUpdates { lat, lng ->
                viewModel.onLocationUpdate(lat, lng)
            }
        }
    }



    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            locationHelper.startLocationUpdates { lat, lng ->
                viewModel.onLocationUpdate(lat, lng)
            }
        }
    }

    // Camera follow
    LaunchedEffect(uiState.userLocation, uiState.isNavigating) {
        val loc = uiState.userLocation ?: return@LaunchedEffect
        if (!uiState.isNavigating) return@LaunchedEffect

        val last = uiState.lastCameraLocation
        if (last == null || GeoUtils.distanceMeters(last.toGeoPoint(), loc.toGeoPoint()) > 6.0) {
            viewModel.onCameraMoved(loc)
            scope.launch {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(loc, 17f)
                )
            }
        }
    }

    // Mark announcements as seen when tab is active
    LaunchedEffect(currentScreen) {
        if (!isGuest && currentScreen == PureAppScreen.ANNOUNCEMENTS) {
            viewModel.markAnnouncementsAsSeen()
        }
    }

    CampusTheme(darkMode = isDarkMode) {
        val clipboard = LocalClipboardManager.current
        var friendRequestError by remember { mutableStateOf<String?>(null) }
        var acceptRejectError by remember { mutableStateOf<String?>(null) }
        val isDetailActive = selectedChatId != null || selectedAnnouncementId != null || 
                            currentScreen == PureAppScreen.SETTINGS || currentScreen == PureAppScreen.PROFILE || 
                            currentScreen == PureAppScreen.FRIEND_PROFILE || currentScreen == PureAppScreen.SEARCH

        PureHomeScreen(
            currentScreen = currentScreen,
                onScreenSelected = { screen ->
                    selectedChatId = null
                    selectedAnnouncementId = null
                    selectedAnnouncementInitialTab = 0
                    announcementOpenedFromChat = false
                    previousScreen = currentScreen
                    currentScreen = screen
                },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            isSearching = isSearching,
            onToggleSearch = { isSearching = it },
            unreadMessageCount = uiState.unreadMessageCount,
            newAnnouncementCount = uiState.newAnnouncementCount,
            onSettingsClick = {
                previousScreen = currentScreen
                currentScreen = PureAppScreen.SETTINGS
            },
            hideBars = isDetailActive
        ) { padding ->
            val overlayTopPadding = when {
                currentScreen == PureAppScreen.CHAT && selectedChatId == null ->
                    padding.calculateTopPadding()
                currentScreen == PureAppScreen.ANNOUNCEMENTS && selectedAnnouncementId == null ->
                    padding.calculateTopPadding()
                else -> 0.dp
            }
            Box(modifier = Modifier.fillMaxSize()) {
                if (currentScreen == PureAppScreen.MAP) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding())
                    ) {
                    MapScreen(
                        uiState = uiState,
                        cameraPositionState = cameraPositionState,
                        hasLocationPermission = hasLocationPermission,
                        searchQuery = if (currentScreen == PureAppScreen.MAP) searchQuery else "",
                        selectedCategory = selectedCategory,
                        onCameraMoved = { viewModel.onCameraMoved(it) },
                        onShowRoutePicker = { viewModel.showRoutePicker(it) },
                        onStartNavigation = { viewModel.startNavigation(it) },
                        onStopNavigation = { viewModel.stopNavigation() },
                        onSettingsClick = {
                            previousScreen = currentScreen
                            currentScreen = PureAppScreen.SETTINGS
                        },
                        onAddFriendClick = {
                            if (isGuest) {
                                previousScreen = currentScreen
                                currentScreen = PureAppScreen.CHAT
                            } else {
                                showFriendManager = true
                            }
                        },
                        onMapTap = { focusManager.clearFocus() }
                    )

                    // Top quick filter chips: Block, Park, Gates, ATM, Hostel
                    val primaryCategories = remember {
                        listOf(
                            PlaceCategory.BLOCK,
                            PlaceCategory.PARK,
                            PlaceCategory.GATE,
                            PlaceCategory.ATM,
                            PlaceCategory.HOSTEL
                        )
                    }
                    val allCategoriesWithCounts = remember {
                        val remaining = PlaceCategory.values()
                            .filter { it != PlaceCategory.OTHER && it !in primaryCategories && campusPlaces.any { p -> p.category == it } }
                        (primaryCategories + remaining).map { cat ->
                            cat to campusPlaces.count { it.category == cat }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            tonalElevation = 4.dp,
                            shadowElevation = 6.dp,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedCategory == null,
                                        onClick = { selectedCategory = null },
                                        label = { Text("All", style = MaterialTheme.typography.labelMedium) },
                                        leadingIcon = if (selectedCategory == null) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                                items(
                                    items = allCategoriesWithCounts,
                                    key = { it.first.name }
                                ) { (category, count) ->
                                    val isSelected = selectedCategory == category
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedCategory = if (isSelected) null else category
                                        },
                                        label = {
                                            Text(
                                                if (count > 0) "${category.displayLabel} ($count)" else category.displayLabel,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                }

                if (currentScreen != PureAppScreen.MAP) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = overlayTopPadding,
                                bottom = padding.calculateBottomPadding()
                            ),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Crossfade(targetState = currentScreen) { screen ->
                            when (screen) {
                                PureAppScreen.CHAT -> {
                                    if (isGuest) {
                                        GuestAccessRequiredScreen(
                                            title = "Login Required",
                                            message = "Please log in to open chats and nearby friends.",
                                            onLoginClick = onLoginRequested
                                        )
                                    } else if (selectedChatId != null) {
                                        ChatDetailScreen(
                                            chatId = selectedChatId!!,
                                            repo = chatRepository,
                                            friendUid = selectedFriendUid,
                                            // A chat can arrive from Supabase before its profile lookup
                                            // completes. Never crash navigation for a missing display name.
                                            friendName = selectedFriendName?.takeIf { it.isNotBlank() } ?: "User",
                                            friendProfilePicUrl = selectedFriendPicUrl,
                                            onProfileClick = { uid ->
                                                selectedFriendUid = uid
                                                currentScreen = PureAppScreen.FRIEND_PROFILE
                                            },
                                            onOpenPost = { postId ->
                                                if (postId.isBlank()) {
                                                    Toast.makeText(context, "Invalid post link", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    selectedAnnouncementId = postId
                                                    announcementOpenedFromChat = true
                                                    currentScreen = PureAppScreen.ANNOUNCEMENTS
                                                }
                                            },
                                            onBack = { selectedChatId = null }
                                        )
                                    } else {
                                        chatRepository?.let { repo ->
                                            ChatScreen(
                                                chatRepository = repo,
                                                friends = friends,
                                                selectedTab = chatTabSelection,
                                                onTabSelected = { chatTabSelection = it },
                                                onChatClick = { id, uid, name, pic ->
                                                    selectedChatId = id
                                                    selectedFriendUid = uid
                                                    selectedFriendName = name
                                                    selectedFriendPicUrl = pic
                                                },
                                                onAddFriendClick = { showFriendManager = true },
                                                searchQuery = searchQuery,
                                                onMessagePrivately = { id, uid, name, pic ->
                                                    selectedChatId = id
                                                    selectedFriendUid = uid
                                                    selectedFriendName = name
                                                    selectedFriendPicUrl = pic
                                                },
                                                currentUser = currentUser,
                                                userLocation = uiState.userLocation
                                            )
                                        }
                                    }
                                }

                                PureAppScreen.ANNOUNCEMENTS -> {
                                    if (isGuest) {
                                        GuestAccessRequiredScreen(
                                            title = "Login Required",
                                            message = "Please log in to view announcements, create posts, and interact with updates.",
                                            onLoginClick = onLoginRequested
                                        )
                                    } else if (selectedAnnouncementId != null) {
                                        AnnouncementDetailScreen(
                                            announcementId = selectedAnnouncementId!!,
                                            repo = announcementRepository,
                                            chatRepository = chatRepository,
                                            initialTab = selectedAnnouncementInitialTab,
                                            onBack = {
                                                selectedAnnouncementId = null
                                                selectedAnnouncementInitialTab = 0
                                                if (announcementOpenedFromChat && selectedChatId != null) {
                                                    announcementOpenedFromChat = false
                                                    currentScreen = PureAppScreen.CHAT
                                                } else {
                                                    announcementOpenedFromChat = false
                                                }
                                            }
                                        )
                                    } else {
                                        AnnouncementsScreen(
                                            repository = announcementRepository,
                                            chatRepository = chatRepository,
                                            searchQuery = searchQuery,
                                            selectedTab = announcementTab,
                                            onTabSelected = { announcementTab = it },
                                            onSettingsClick = { currentScreen = PureAppScreen.SETTINGS },
                                            onAddFriendClick = { showFriendManager = true },
                                            onAnnouncementClick = { id ->
                                                selectedAnnouncementId = id
                                                selectedAnnouncementInitialTab = 0
                                            },
                                            onOpenTeamsClick = { id ->
                                                selectedAnnouncementId = id
                                                selectedAnnouncementInitialTab = 2
                                            }
                                        )
                                    }
                                }

                                PureAppScreen.EVENTS -> {
                                    if (selectedAnnouncementId != null) {
                                        AnnouncementDetailScreen(
                                            announcementId = selectedAnnouncementId!!,
                                            repo = announcementRepository,
                                            chatRepository = chatRepository,
                                            initialTab = selectedAnnouncementInitialTab,
                                            onBack = {
                                                selectedAnnouncementId = null
                                                selectedAnnouncementInitialTab = 0
                                            }
                                        )
                                    } else {
                                        PureEventsHubScreen(
                                            onEventClick = { id ->
                                                selectedAnnouncementId = id
                                                selectedAnnouncementInitialTab = 0
                                            },
                                            onOpenTeamsClick = { id ->
                                                selectedAnnouncementId = id
                                                selectedAnnouncementInitialTab = 2
                                            },
                                            onCreateEventClick = {
                                                if (isGuest) {
                                                    onLoginRequested()
                                                } else {
                                                    announcementTab = 2
                                                    currentScreen = PureAppScreen.ANNOUNCEMENTS
                                                }
                                            },
                                            searchQuery = searchQuery,
                                            onSearchQueryChange = { searchQuery = it },
                                            renderImage = { url, modifier, scale ->
                                                AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale)
                                            }
                                        )
                                    }
                                }

                                PureAppScreen.SETTINGS -> {
                                    if (isGuest) {
                                        GuestAccessRequiredScreen(
                                            title = "Login Required",
                                            message = "Please log in to open settings and manage your account.",
                                            onLoginClick = onLoginRequested
                                        )
                                    } else {
                                        SettingsScreen(
                                            isDarkMode = isDarkMode,
                                            isAudioEnabled = isVoiceEnabled,
                                            isGhostMode = isGhostMode,
                                            onToggleDarkMode = { scope.launch { themePrefs.saveDarkMode(it) } },
                                            onToggleAudio = { scope.launch { themePrefs.saveVoiceEnabled(it) } },
                                            onToggleGhostMode = {
                                                scope.launch {
                                                    themePrefs.saveGhostMode(it)
                                                    chatRepository?.updateGhostMode(it)
                                                }
                                            },
                                            onProfileClick = { currentScreen = PureAppScreen.PROFILE },
                                            onLogout = onLogout,
                                            onBack = { currentScreen = previousScreen }
                                        )
                                    }
                                }

                                PureAppScreen.PROFILE -> {
                                    when {
                                        isGuest -> {
                                            GuestAccessRequiredScreen(
                                                title = "Login Required",
                                                message = "Please log in to view and edit your profile.",
                                                onLoginClick = onLoginRequested
                                            )
                                        }

                                        currentUser != null -> {
                                            ProfileScreen(
                                                user = currentUser!!,
                                                announcementRepository = announcementRepository,
                                                onBack = { currentScreen = previousScreen },
                                                onFindFriends = {
                                                    currentScreen = PureAppScreen.CHAT
                                                    showFriendManager = true
                                                }
                                            )
                                        }

                                        isCurrentUserLoading -> {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator()
                                            }
                                        }

                                        else -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(24.dp),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = currentUserLoadError ?: "Could not load your profile.",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                Spacer(Modifier.height(16.dp))
                                                Button(
                                                    onClick = {
                                                        scope.launch { loadCurrentUser() }
                                                    }
                                                ) {
                                                    Text("Retry")
                                                }
                                            }
                                        }
                                    }
                                }

                                PureAppScreen.FRIEND_PROFILE -> {
                                    val friend = selectedFriendProfile ?: friends.find { it.uid == selectedFriendUid }
                                    if (friend != null) {
                                        ProfileScreen(
                                            user = friend,
                                            announcementRepository = announcementRepository,
                                            onBack = { currentScreen = PureAppScreen.CHAT }
                                        )
                                    }
                                }

                                PureAppScreen.SEARCH -> {
                                    GlobalSearchNav(
                                        onBack = { currentScreen = previousScreen },
                                        onUserClick = { uid ->
                                            selectedFriendUid = uid
                                            currentScreen = PureAppScreen.FRIEND_PROFILE
                                        },
                                        onPostClick = { postId ->
                                            selectedAnnouncementId = postId
                                            currentScreen = PureAppScreen.ANNOUNCEMENTS
                                        },
                                        onGroupClick = { _ ->
                                            currentScreen = previousScreen
                                        }
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }

                // Dialogs
                if (showFriendManager) {
                    PureFriendManagerBottomSheet(
                        friends = friends.map { it.toPureUser() },
                        requests = requests.map { it.toPureFriendRequest() },
                        pendingRequestEmails = pendingRequestEmails,
                        onDismiss = { showFriendManager = false },
                        onAcceptRequest = { uid ->
                            scope.launch {
                                try {
                                    val acceptedFriend = chatRepository.acceptFriendRequest(uid)
                                    if (acceptedFriend != null) {
                                        viewModel.addFriendLocally(acceptedFriend)
                                    }
                                    Toast.makeText(context, "Friend request accepted", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    acceptRejectError = e.message ?: "Failed to accept friend request."
                                    Toast.makeText(context, acceptRejectError, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onRejectRequest = { uid ->
                            scope.launch {
                                try {
                                    chatRepository.rejectFriendRequest(uid)
                                    Toast.makeText(context, "Friend request rejected", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    acceptRejectError = e.message ?: "Failed to reject friend request."
                                    Toast.makeText(context, acceptRejectError, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onUnfriend = { uid ->
                            scope.launch {
                                try {
                                    chatRepository.unfriendUser(uid)
                                    viewModel.removeFriendLocally(uid)
                                    Toast.makeText(context, "Unfriended", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onSendRequest = { email ->
                            scope.launch {
                                try {
                                    friendRequestError = null
                                    val normalizedEmail = email.trim()
                                    if (friends.any { it.email.trim().equals(normalizedEmail, ignoreCase = true) }) {
                                        friendRequestError = "Already added."
                                        Toast.makeText(context, friendRequestError, Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    chatRepository.sendFriendRequest(normalizedEmail)
                                    pendingRequestEmails = pendingRequestEmails + normalizedEmail.lowercase()
                                    Toast.makeText(context, "Friend request sent", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    friendRequestError = formatFriendRequestError(e.message)
                                    Toast.makeText(context, friendRequestError, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onProfileClick = { uid ->
                            selectedFriendUid = uid
                            showFriendManager = false
                            currentScreen = PureAppScreen.FRIEND_PROFILE
                        },
                        renderImage = { url, modifier, scale ->
                            AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale)
                        }
                    )
                }

                if (friendRequestError != null) {
                    AlertDialog(
                        onDismissRequest = { friendRequestError = null },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(friendRequestError ?: ""))
                                    Toast.makeText(context, "Error copied", Toast.LENGTH_SHORT).show()
                                    friendRequestError = null
                                }
                            ) { Text("Copy") }
                        },
                        dismissButton = { TextButton(onClick = { friendRequestError = null }) { Text("Close") } },
                        title = { Text("Friend Request Error") },
                        text = { Text(friendRequestError ?: "") }
                    )
                }

                if (acceptRejectError != null) {
                    AlertDialog(
                        onDismissRequest = { acceptRejectError = null },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(acceptRejectError ?: ""))
                                    Toast.makeText(context, "Error copied", Toast.LENGTH_SHORT).show()
                                    acceptRejectError = null
                                }
                            ) { Text("Copy") }
                        },
                        dismissButton = { TextButton(onClick = { acceptRejectError = null }) { Text("Close") } },
                        title = { Text("Friend Request Action Failed") },
                        text = { Text(acceptRejectError ?: "") }
                    )
                }

                if (uiState.showRoutePicker) {
                    PureRoutePickerBottomSheet(
                        routes = RoutesData.allRoutes,
                        onConfirm = { route ->
                            viewModel.startNavigation(route)
                            viewModel.hideRoutePicker()
                        },
                        onUseCurrent = { destination ->
                            viewModel.selectRouteFromCurrentLocation(destination)
                        },
                        onDismiss = { viewModel.hideRoutePicker() }
                    )
                }
            }
        }
    }
}
