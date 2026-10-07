@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.shared

import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shared.model.Announcement
import com.example.shared.model.AnnouncementType
import com.example.shared.model.PureChat
import com.example.shared.model.buildAnnouncementId
import com.example.shared.repository.AnnouncementRepository
import com.example.shared.repository.IosChatRepository
import com.example.shared.repository.IosAuthApi
import com.example.shared.repository.IosPreferencesStore
import com.example.shared.repository.IosLocalDatabaseStore
import com.example.shared.repository.IosUserProfile
import com.example.shared.repository.IosUserStore
import com.example.shared.repository.currentTimeMillis
import com.example.shared.ui.PureAnnouncementsScreen
import com.example.shared.ui.PureAppScreen
import com.example.shared.ui.PureAppTheme
import com.example.shared.ui.PureAvatarPlaceholder
import com.example.shared.ui.PureChatScreen
import com.example.shared.ui.PureCreatePostDialog
import com.example.shared.ui.PureDeleteChatDialog
import com.example.shared.ui.PureFriendManagerBottomSheet
import com.example.shared.ui.PureHomeScreen
import com.example.shared.ui.PureMapScreen
import com.example.shared.ui.PureNearbyUsersBottomSheet
import com.example.shared.ui.PureNewChatBottomSheet
import com.example.shared.ui.PureRoutePickerBottomSheet
import com.example.shared.ui.PureSettingsScreen
import com.example.shared.utils.ExternalNavigator
import com.example.shared.utils.ArrivalChecker
import com.example.shared.utils.GeoUtils
import com.example.shared.utils.NavigationUtils
import com.example.shared.utils.RouteHelpers
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.launch
import cocoapods.GoogleMaps.GMSCameraPosition
import cocoapods.GoogleMaps.GMSMapStyle
import cocoapods.GoogleMaps.GMSMapViewDelegateProtocol
import cocoapods.GoogleMaps.GMSMapView
import cocoapods.GoogleMaps.GMSMarker
import cocoapods.GoogleMaps.GMSMutablePath
import cocoapods.GoogleMaps.GMSPolyline
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCoordinateRegionMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKCoordinateSpanMake
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKOverlayProtocol
import platform.MapKit.MKOverlayRenderer
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKPolyline
import platform.MapKit.MKPolylineRenderer
import platform.MapKit.MKUserLocation
import platform.MapKit.addOverlay
import platform.MapKit.overlays
import platform.MapKit.removeOverlays
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UITextAlignmentCenter
import platform.UIKit.UITextAlignmentLeft
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private fun formatFriendRequestError(message: String?): String {
    val text = message?.trim().orEmpty()
    return when {
        text.contains("already sent you a friend request", ignoreCase = true) -> text
        text.contains("already been sent", ignoreCase = true) -> "Friend request already sent."
        text.contains("already friends", ignoreCase = true) -> "You are already friends with this user."
        text.contains("yourself", ignoreCase = true) -> "You cannot send a friend request to yourself."
        text.contains("not found", ignoreCase = true) -> "No user found with that email."
        text.contains("permission", ignoreCase = true) -> "Permission denied while sending friend request."
        text.isBlank() -> "Failed to send friend request."
        else -> text
    }
}

fun HomeViewController(
    initialEmail: String = "",
    onOpenSettings: () -> Unit = {},
    onLogout: () -> Unit = {}
): UIViewController = SafeComposeViewController(screenName = "Home") {
    HomeRoute(
        initialEmail = initialEmail,
        onOpenSettings = onOpenSettings,
        onLogout = onLogout
    )
}

@Composable
internal fun HomeRoute(
    initialEmail: String,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val chatRepository = remember { IosChatRepository() }
    val announcementRepository = remember { AnnouncementRepository() }

    val currentUser by IosUserStore.currentUser.collectAsState(initial = null)
    val chats by chatRepository.allChats.collectAsState(initial = emptyList())
    val friends by chatRepository.friends.collectAsState(initial = emptyList())
    val requests by chatRepository.friendRequests.collectAsState(initial = emptyList())
    val announcements by announcementRepository.getAnnouncementsFlow().collectAsState(initial = emptyList())
    val liveLocation by IosLocationService.location.collectAsState(initial = null)

    // Keep iOS on the same root destination as Android. Detail routes retain
    // their predecessor explicitly so toolbar Back follows the actual flow.
    var currentScreen by remember { mutableStateOf(PureAppScreen.MAP) }
    var previousScreen by remember { mutableStateOf(PureAppScreen.MAP) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isFilterDockVisible by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<PlaceCategory?>(null) }
    var mapZoom by remember { mutableStateOf(campusOverviewZoom) }
    var chatTab by remember { mutableIntStateOf(0) }
    var announcementTab by remember { mutableIntStateOf(0) }

    var selectedChat by remember { mutableStateOf<PureChat?>(null) }
    var selectedAnnouncementId by remember { mutableStateOf<String?>(null) }
    var announcementReturnScreen by remember { mutableStateOf(PureAppScreen.ANNOUNCEMENTS) }
    var selectedFriendUid by remember { mutableStateOf<String?>(null) }
    var friendProfile by remember { mutableStateOf<IosUserProfile?>(null) }
    var chatToDelete by remember { mutableStateOf<PureChat?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var showFriendManager by remember { mutableStateOf(false) }
    var showNewChatSheet by remember { mutableStateOf(false) }
    var showNearbySheet by remember { mutableStateOf(false) }
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var showRoutePicker by remember { mutableStateOf(false) }
    var nearbyUsers by remember { mutableStateOf<List<com.example.shared.model.PureUser>>(emptyList()) }
    var nearbyLoading by remember { mutableStateOf(false) }

    var isDarkMode by remember { mutableStateOf(IosPreferencesStore.isDarkMode()) }
    var isAudioEnabled by remember { mutableStateOf(IosPreferencesStore.isAudioEnabled()) }
    var isGhostMode by remember { mutableStateOf(IosPreferencesStore.isGhostMode()) }

    val defaultLocation = GeoPoint(30.771450, 76.578228)
    var currentLocation by remember { mutableStateOf(defaultLocation) }
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var activeRoute by remember { mutableStateOf<Route?>(null) }
    var isNavigating by remember { mutableStateOf(false) }
    var instructionText by remember { mutableStateOf("Select a destination to begin navigation") }
    var distanceLeft by remember { mutableStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDebugLog by remember { mutableStateOf(false) }
    val localPlaces = remember { IosLocalDatabaseStore.loadPlaces() }
    val localRoutes = remember { IosLocalDatabaseStore.loadRoutes() }
    val baseMapPlaces = if (localPlaces.isNotEmpty()) localPlaces else campusPlaces
    val filteredMapPlaces = remember(baseMapPlaces, searchQuery, selectedCategory, mapZoom) {
        var places = baseMapPlaces
        if (selectedCategory != null) {
            places = places.filter { it.category == selectedCategory }
        }
        if (searchQuery.isNotBlank()) {
            places = places.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        when {
            mapZoom < 15f -> places.filter { it.category.name in listOf("GATE", "HOSTEL") }
            mapZoom < 17f -> places.filter { it.category.name in listOf("GATE", "HOSTEL", "BLOCK") }
            else -> places
        }
    }

    LaunchedEffect(Unit) {
        runCatching { IosAuthApi.restoreSharedSession() }
        chatRepository.startSync()
        runCatching { IosUserStore.refreshCurrentUser() }
        runCatching { announcementRepository.refreshNow() }
        currentLocation = currentUser?.location ?: defaultLocation
    }

    DisposableEffect(announcementRepository) {
        onDispose { announcementRepository.stopSync() }
    }

    DisposableEffect(currentScreen == PureAppScreen.MAP) {
        if (currentScreen == PureAppScreen.MAP) {
            IosLocationService.start()
        } else {
            IosLocationService.stop()
        }
        onDispose {
            IosLocationService.stop()
        }
    }

    LaunchedEffect(currentUser?.uid) {
        currentUser?.location?.let { currentLocation = it }
        if (currentUser != null) {
            isGhostMode = currentUser!!.ghostMode
        }
    }

    LaunchedEffect(liveLocation?.lat, liveLocation?.lng) {
        val point = liveLocation ?: return@LaunchedEffect
        currentLocation = point
        runCatching {
            IosUserStore.updateLocation(point.lat, point.lng)
        }
    }

    LaunchedEffect(selectedFriendUid, currentScreen) {
        if (currentScreen == PureAppScreen.FRIEND_PROFILE && !selectedFriendUid.isNullOrBlank()) {
            val uid = selectedFriendUid!!
            friendProfile = IosUserStore.cachedUser(uid) ?: IosUserStore.getUser(uid)
            runCatching {
                IosUserStore.refreshUser(uid)
            }.onSuccess { refreshed ->
                if (refreshed != null) {
                    friendProfile = refreshed
                }
            }
        }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == PureAppScreen.ANNOUNCEMENTS) {
            IosPreferencesStore.setLastAnnouncementSeen(currentTimeMillis())
            runCatching { announcementRepository.refreshNow() }
        }
        if (currentScreen == PureAppScreen.PROFILE) {
            runCatching { IosUserStore.refreshCurrentUser() }
        }
    }

    LaunchedEffect(activeRoute, currentLocation, isNavigating) {
        if (!isNavigating || activeRoute == null) return@LaunchedEffect
        val route = activeRoute ?: return@LaunchedEffect
        val currentIndex = RouteHelpers.findNearestSegmentIndex(currentLocation, route)
        val prev = route.points.getOrNull(currentIndex - 1)
        val curr = route.points.getOrNull(currentIndex)
        val next = route.points.getOrNull(currentIndex + 1)
        val turn = if (prev != null && curr != null && next != null) {
            NavigationUtils.getTurnInstruction(prev, curr, next)
        } else {
            "Continue straight"
        }
        val distToNext = next?.let { GeoUtils.distanceMeters(currentLocation, it).toInt() } ?: 0
        instructionText = if (turn == "Continue straight") {
            "Continue straight for $distToNext meters"
        } else {
            if (distToNext > 80) "$turn in $distToNext meters"
            else if (distToNext > 15) "Get ready to $turn"
            else "$turn now"
        }
        distanceLeft = RouteHelpers.distanceToEnd(currentLocation, route, currentIndex).toFloat()

        if (ArrivalChecker.hasArrived(currentLocation, route.points.last())) {
            isNavigating = false
            activeRoute = null
            instructionText = "You have arrived"
            distanceLeft = 0f
        }
    }

    fun resetSelections(next: PureAppScreen) {
        selectedChat = null
        selectedAnnouncementId = null
        if (next == PureAppScreen.MAP) {
            friendProfile = null
            selectedFriendUid = null
        }
    }

    fun startNavigation(route: Route) {
        activeRoute = route
        isNavigating = true
        showRoutePicker = false
    }

    fun selectRouteFromCurrentLocation(destination: String) {
        val candidates = localRoutes.filter { route ->
            route.name.endsWith(destination, ignoreCase = true) ||
                route.name.substringAfterLast("->", route.name)
                    .trim()
                    .equals(destination.trim(), ignoreCase = true)
        }
        val selected = candidates.minByOrNull { GeoUtils.minDistanceToRoute(currentLocation, it) }
        if (selected != null) {
            startNavigation(selected)
        } else {
            errorMessage = "No route found to $destination"
        }
    }

    val unreadMessageCount = chats.sumOf { it.unreadCount }
    val newAnnouncementCount = announcements.count {
        it.authorUid != currentUser?.uid && it.timestamp > IosPreferencesStore.lastAnnouncementSeen()
    }
    val isDetailActive = selectedChat != null ||
        selectedAnnouncementId != null ||
        currentScreen == PureAppScreen.SETTINGS ||
        currentScreen == PureAppScreen.PROFILE ||
        currentScreen == PureAppScreen.FRIEND_PROFILE

    val externalNavigator = remember {
        object : ExternalNavigator {
            override fun reportIssue() {
                if (!openIosExternalUrl("mailto:jagdeepsingh3505j@gmail.com")) {
                    errorMessage = "No mail app is configured on this device."
                }
            }

            override fun callHelpline(number: String) {
                val url = iosHelplineUrl(number)
                if (url == null || !openIosExternalUrl(url)) {
                    errorMessage = "Unable to open the phone app for this number."
                }
            }
        }
    }

    if (showNewChatSheet) {
        PureNewChatBottomSheet(
            friends = friends,
            onDismiss = { showNewChatSheet = false },
            onFriendClick = { friend ->
                scope.launch {
                    runCatching {
                        val chatId = chatRepository.createChatForFriend(friend.uid, friend.name)
                        selectedChat = PureChat(
                            chatId = chatId,
                            friendUid = friend.uid,
                            friendName = friend.name,
                            friendProfilePicUrl = friend.profilePicUrl,
                            lastMessage = "New chat started",
                            lastMessageTime = currentTimeMillis()
                        )
                        currentScreen = PureAppScreen.CHAT
                        showNewChatSheet = false
                    }.onFailure { errorMessage = it.message ?: "Failed to create chat" }
                }
            },
            renderImage = { url, modifier, scale -> IosRemoteImage(url, modifier, scale) }
        )
    }

    if (showNearbySheet) {
        PureNearbyUsersBottomSheet(
            nearbyUsers = nearbyUsers,
            isLoading = nearbyLoading,
            errorMessage = null,
            onDismiss = { showNearbySheet = false },
            onSendRequest = { user ->
                scope.launch {
                    runCatching { chatRepository.sendFriendRequest(user.email) }
                        .onFailure { errorMessage = formatFriendRequestError(it.message) }
                }
            },
            renderImage = { url, modifier, scale -> IosRemoteImage(url, modifier, scale) }
        )
    }

    /* Retired iOS-only Firestore group management. Community/team UI is now
       provided by PureGroupsScreen through PureChatScreen on both platforms.
    if (showCreateGroupSheet) {
        LaunchedEffect(showCreateGroupSheet) {
            featuredGroups = chatRepository.getFeaturedGroups().map { PureGroupDiscoveryItem(it, groups.any { joined -> joined.groupId == it.groupId }) }
        }
        PureManageGroupsBottomSheet(
            friends = friends,
            featuredGroups = featuredGroups,
            searchResult = groupSearchResult,
            onDismiss = { showCreateGroupSheet = false },
            onSearch = { code ->
                scope.launch {
                    groupSearchResult = chatRepository.searchGroupByCode(code)?.let {
                        PureGroupDiscoveryItem(it, groups.any { joined -> joined.groupId == it.groupId })
                    }
                }
            },
            onJoin = { group ->
                scope.launch {
                    runCatching {
                        chatRepository.joinGroupByCode(group.inviteCode ?: group.publicGroupId ?: "")
                        chatRepository.refreshGroupsNow()
                        showCreateGroupSheet = false
                    }.onFailure { errorMessage = it.message ?: "Failed to join group" }
                }
            },
            onCreateGroup = { name, groupId, visibility, memberIds ->
                scope.launch {
                    runCatching {
                        chatRepository.createAdvancedGroup(name, memberIds, null, groupId, visibility)
                        showCreateGroupSheet = false
                    }.onFailure { errorMessage = it.message ?: "Failed to create group" }
                }
            },
            renderImage = { url, modifier, scale -> IosRemoteImage(url, modifier, scale) }
        )
    }
    */

    if (showCreatePostDialog) {
        PureCreatePostDialog(
            initialType = AnnouncementType.values().getOrElse(announcementTab) { AnnouncementType.NEWS },
            onDismiss = { showCreatePostDialog = false },
            isLoading = false,
            onPost = { title, content, type, itemName, place, time, reward, eventVenue, eventTime, eventPurpose, eventDlType, eventMode, eventMaxMembers, eventDepartments, eventLink ->
                scope.launch {
                    runCatching {
                        val profile = currentUser ?: IosUserProfile(
                            uid = chatRepository.getCurrentUserUid() ?: "",
                            email = initialEmail
                        )
                        val timestamp = currentTimeMillis()
                        val announcement = Announcement(
                            id = buildAnnouncementId(profile.uid, timestamp),
                            title = title,
                            content = content,
                            timestamp = timestamp,
                            author = profile.name.ifBlank { profile.email.substringBefore("@") },
                            authorUid = profile.uid,
                            authorProfilePicUrl = profile.profilePicUrl.takeIf { it.isNotBlank() },
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
                            eventLink = eventLink
                        )
                        announcementRepository.createAnnouncement(announcement)
                        showCreatePostDialog = false
                    }.onFailure { errorMessage = it.message ?: "Failed to create post" }
                }
            },
            onAddPhotoClick = {
                errorMessage = "Image upload will be added with the native iOS picker"
            },
            hasImage = false,
            onRemoveImage = {},
            renderSelectedImage = { modifier ->
                PureAvatarPlaceholder(modifier = modifier)
            }
        )
    }

    if (showDeleteDialog && chatToDelete != null) {
        PureDeleteChatDialog(
            chatName = chatToDelete?.friendName ?: "this user",
            onDismiss = {
                showDeleteDialog = false
                chatToDelete = null
            },
            onConfirm = {
                val target = chatToDelete ?: return@PureDeleteChatDialog
                showDeleteDialog = false
                chatToDelete = null
                scope.launch {
                    runCatching { chatRepository.deleteChat(target.chatId) }
                        .onFailure { errorMessage = it.message ?: "Failed to delete chat" }
                }
            }
        )
    }

    if (showFriendManager) {
        PureFriendManagerBottomSheet(
            friends = friends,
            requests = requests,
            onDismiss = { showFriendManager = false },
            onAcceptRequest = { uid ->
                scope.launch {
                    runCatching { chatRepository.acceptFriendRequest(uid) }
                        .onFailure { errorMessage = it.message ?: "Failed to accept request" }
                }
            },
            onRejectRequest = { uid ->
                scope.launch {
                    runCatching { chatRepository.rejectFriendRequest(uid) }
                        .onFailure { errorMessage = it.message ?: "Failed to reject request" }
                }
            },
            onUnfriend = { uid ->
                scope.launch {
                    runCatching { chatRepository.unfriendUser(uid) }
                        .onFailure { errorMessage = it.message ?: "Failed to unfriend user" }
                }
            },
            onSendRequest = { email ->
                scope.launch {
                    runCatching { chatRepository.sendFriendRequest(email) }
                        .onFailure { errorMessage = formatFriendRequestError(it.message) }
                }
            },
            onProfileClick = { uid ->
                selectedFriendUid = uid
                showFriendManager = false
                previousScreen = PureAppScreen.CHAT
                currentScreen = PureAppScreen.FRIEND_PROFILE
            },
            renderImage = { url, modifier, scale ->
                IosRemoteImage(url, modifier, scale)
            }
        )
    }

    if (showRoutePicker) {
        PureRoutePickerBottomSheet(
            routes = localRoutes,
            onConfirm = { route -> startNavigation(route) },
            onUseCurrent = { destination -> selectRouteFromCurrentLocation(destination) },
            onDismiss = { showRoutePicker = false }
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("Close") } },
            title = { Text("iOS App") },
            text = { Text(message) }
        )
    }

    PureAppTheme(isDarkMode = isDarkMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            PureHomeScreen(
            currentScreen = currentScreen,
            onScreenSelected = { screen ->
                previousScreen = currentScreen
                resetSelections(screen)
                currentScreen = screen
            },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            isSearching = isSearching,
            onToggleSearch = { enabled ->
                isSearching = enabled
                if (!enabled) searchQuery = ""
            },
            unreadMessageCount = unreadMessageCount,
            newAnnouncementCount = newAnnouncementCount,
            onSettingsClick = {
                previousScreen = currentScreen
                currentScreen = PureAppScreen.SETTINGS
                onOpenSettings()
            },
            hideBars = isDetailActive
        ) { padding ->
            val overlayTopPadding = when {
                currentScreen == PureAppScreen.CHAT && selectedChat == null ->
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
                        PureMapScreen(
                            isNavigating = isNavigating,
                            instructionText = instructionText,
                            distanceLeft = distanceLeft,
                            selectedPlace = selectedPlace,
                            onClosePlace = { selectedPlace = null },
                            onShowRoutePicker = { destination ->
                                selectedPlace = selectedPlace ?: localPlaces.firstOrNull { it.name.equals(destination, true) }
                                showRoutePicker = true
                            },
                            onRecenter = {
                                currentLocation = liveLocation ?: currentUser?.location ?: currentLocation
                            },
                            onStopNavigation = {
                                isNavigating = false
                                activeRoute = null
                                distanceLeft = 0f
                                instructionText = "Navigation stopped"
                            },
                            renderMap = {
                                IosMapKitView(
                                    places = filteredMapPlaces,
                                    selectedPlace = selectedPlace,
                                    currentLocation = currentLocation,
                                    activeRoute = activeRoute,
                                    isNavigating = isNavigating,
                                    onPlaceSelected = { place ->
                                        selectedPlace = place
                                    },
                                    onMapTap = { selectedPlace = null },
                                    onZoomChanged = { zoom -> mapZoom = zoom }
                                )
                            },
                            renderDirectionsIcon = { Icon(Icons.Default.Directions, contentDescription = null) },
                            renderMyLocationIcon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                            renderCloseIcon = { Icon(Icons.Default.Close, contentDescription = null) }
                        )

                        if (isFilterDockVisible) {
                            androidx.compose.material3.Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                                shape = RoundedCornerShape(32.dp),
                                color = Color.White,
                                tonalElevation = 0.dp,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    LazyRow(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        item {
                                            FilterChip(
                                                selected = selectedCategory == null,
                                                onClick = { selectedCategory = null },
                                                label = { Text("All") }
                                            )
                                        }
                                        items(
                                            PlaceCategory.values().filter { it != PlaceCategory.OTHER },
                                            key = { it.name }
                                        ) { category ->
                                            FilterChip(
                                                selected = selectedCategory == category,
                                                onClick = {
                                                    selectedCategory =
                                                        if (selectedCategory == category) null else category
                                                },
                                                label = {
                                                    Text(
                                                        category.name.lowercase()
                                                            .replaceFirstChar { it.uppercase() }
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    androidx.compose.material3.IconButton(
                                        onClick = { isFilterDockVisible = false },
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Hide filters"
                                        )
                                    }
                                }
                            }
                        } else {
                            androidx.compose.material3.SmallFloatingActionButton(
                                onClick = { isFilterDockVisible = true },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 16.dp, end = 16.dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Show filters")
                            }
                        }
                    }
                } else {
                    androidx.compose.material3.Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = overlayTopPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (currentScreen) {
                            PureAppScreen.CHAT -> {
                                if (selectedChat != null) {
                                    IosChatDetailContent(
                                        chat = selectedChat!!,
                                        repository = chatRepository,
                                        onBack = { selectedChat = null },
                                        onProfileClick = { uid ->
                                            selectedFriendUid = uid
                                            previousScreen = PureAppScreen.CHAT
                                            currentScreen = PureAppScreen.FRIEND_PROFILE
                                        }
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        PureChatScreen(
                                            repository = chatRepository,
                                            selectedTab = chatTab,
                                            onTabSelected = { chatTab = it },
                                            onChatClick = { chat -> selectedChat = chat },
                                            onGroupClick = {},
                                            onAddFriendClick = { showFriendManager = true },
                                            onNearbyClick = {
                                                showNearbySheet = true
                                                nearbyLoading = true
                                                scope.launch {
                                                    nearbyUsers = runCatching {
                                                        chatRepository.getNearbyUsers(currentLocation.lat, currentLocation.lng)
                                                            .filter { candidate -> friends.none { it.uid == candidate.uid } }
                                                    }.getOrElse {
                                                        errorMessage = it.message ?: "Failed to load nearby users"
                                                        emptyList()
                                                    }
                                                    nearbyLoading = false
                                                }
                                            },
                                            onNewChatClick = { showNewChatSheet = true },
                                            onNewGroupClick = {},
                                            onChatLongClick = { chat ->
                                                chatToDelete = chat
                                                showDeleteDialog = true
                                            },
                                            onGroupLongClick = {},
                                            searchQuery = searchQuery,
                                            onMessagePrivately = { senderUid, senderName ->
                                                scope.launch {
                                                    runCatching {
                                                        val chatId = chatRepository.createChatForFriend(senderUid, senderName)
                                                        selectedChat = PureChat(
                                                            chatId = chatId,
                                                            friendUid = senderUid,
                                                            friendName = senderName,
                                                            lastMessage = "",
                                                            lastMessageTime = currentTimeMillis()
                                                        )
                                                        chatTab = 0
                                                    }.onFailure {
                                                        errorMessage = it.message ?: "Unable to open private chat"
                                                    }
                                                }
                                            },
                                            renderImage = { url, modifier, scale ->
                                                IosRemoteImage(url, modifier, scale)
                                            }
                                        )
                                    }
                                }
                            }

                            PureAppScreen.ANNOUNCEMENTS -> {
                                if (selectedAnnouncementId != null) {
                                    IosAnnouncementDetailContent(
                                        announcementId = selectedAnnouncementId!!,
                                        repository = announcementRepository,
                                        currentUser = currentUser,
                                        onBack = {
                                            selectedAnnouncementId = null
                                            currentScreen = announcementReturnScreen
                                        }
                                    )
                                } else {
                                    val filteredAnnouncements = announcements.filter {
                                        when (announcementTab) {
                                            1 -> it.type == AnnouncementType.LOST_AND_FOUND
                                            2 -> it.type == AnnouncementType.EVENT
                                            else -> it.type == AnnouncementType.NEWS
                                        }
                                    }.filter {
                                        searchQuery.isBlank() ||
                                            it.title.contains(searchQuery, true) ||
                                            it.content.contains(searchQuery, true) ||
                                            it.author.contains(searchQuery, true)
                                    }
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        PureAnnouncementsScreen(
                                            selectedTab = announcementTab,
                                            onTabSelected = { announcementTab = it },
                                            announcements = filteredAnnouncements,
                                            isLoading = false,
                                            currentUser = currentUser?.toPureUser(),
                                            onAnnouncementClick = { announcementId ->
                                                selectedAnnouncementId = announcementId
                                                announcementReturnScreen = PureAppScreen.ANNOUNCEMENTS
                                            },
                                            onCreatePostClick = {
                                                showCreatePostDialog = true
                                            },
                                            onReport = { selectedAnnouncement ->
                                                selectedAnnouncementId = selectedAnnouncement.id
                                            },
                                            onShare = { selectedAnnouncement ->
                                                selectedAnnouncementId = selectedAnnouncement.id
                                            },
                                            onDelete = { selectedAnnouncement ->
                                                scope.launch {
                                                    announcementRepository.deleteAnnouncement(selectedAnnouncement.id)
                                                }
                                            },
                                            formatTime = ::iosRelativeTime,
                                            renderImage = { url, modifier, scale ->
                                                IosRemoteImage(url, modifier, scale)
                                            },
                                            currentUid = currentUser?.uid,
                                            searchQuery = searchQuery,
                                            onSearchQueryChange = { searchQuery = it }
                                        )
                                    }
                                }
                            }

                            PureAppScreen.SETTINGS -> {
                                if (showDebugLog) {
                                    IosDebugLogContent(
                                        onBack = { showDebugLog = false }
                                    )
                                } else {
                                    PureSettingsScreen(
                                        isDarkMode = isDarkMode,
                                        isAudioEnabled = isAudioEnabled,
                                        isGhostMode = isGhostMode,
                                        onToggleDarkMode = {
                                            isDarkMode = it
                                            IosPreferencesStore.setDarkMode(it)
                                        },
                                        onToggleAudio = {
                                            isAudioEnabled = it
                                            IosPreferencesStore.setAudioEnabled(it)
                                        },
                                        onToggleGhostMode = { enabled ->
                                            isGhostMode = enabled
                                            IosPreferencesStore.setGhostMode(enabled)
                                            scope.launch {
                                                runCatching { chatRepository.toggleGhostMode(enabled) }
                                                    .onFailure { errorMessage = it.message ?: "Failed to update ghost mode" }
                                            }
                                        },
                                        onProfileClick = {
                                            previousScreen = PureAppScreen.SETTINGS
                                            currentScreen = PureAppScreen.PROFILE
                                        },
                                        onOpenDebugLog = {
                                            IosInAppDebugLogStore.log("Opened debug log screen")
                                            showDebugLog = true
                                        },
                                        onLogout = {
                                            chatRepository.clearAllListeners()
                                            chatRepository.resetSessionState()
                                            IosUserStore.resetCurrentUser()
                                            scope.launch {
                                                IosAuthApi.logout()
                                                onLogout()
                                            }
                                        },
                                        onBack = { currentScreen = previousScreen },
                                        externalNavigator = externalNavigator
                                    )
                                }
                            }

                            PureAppScreen.PROFILE -> {
                                val profile = currentUser ?: IosUserProfile(
                                    uid = chatRepository.getCurrentUserUid() ?: "",
                                    email = initialEmail
                                )
                                IosProfileContent(
                                    profile = profile,
                                    currentUserUid = currentUser?.uid,
                                    announcements = announcements,
                                    readOnly = false,
                                    onBack = { currentScreen = previousScreen },
                                    onOpenPost = { postId ->
                                        selectedAnnouncementId = postId
                                        announcementReturnScreen = PureAppScreen.PROFILE
                                        currentScreen = PureAppScreen.ANNOUNCEMENTS
                                    },
                                    onProfileSaved = {}
                                )
                            }

                            PureAppScreen.FRIEND_PROFILE -> {
                                friendProfile?.let { profile ->
                                    IosProfileContent(
                                        profile = profile,
                                        currentUserUid = currentUser?.uid,
                                        announcements = announcements,
                                        readOnly = profile.uid != (currentUser?.uid ?: ""),
                                        onBack = { currentScreen = previousScreen },
                                        onOpenPost = { postId ->
                                            selectedAnnouncementId = postId
                                            announcementReturnScreen = PureAppScreen.FRIEND_PROFILE
                                            currentScreen = PureAppScreen.ANNOUNCEMENTS
                                        },
                                        onProfileSaved = {}
                                    )
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
internal fun IosMapBrowser(
    places: List<Place>,
    selectedPlace: Place?,
    isNavigating: Boolean,
    currentLocation: GeoPoint,
    onPlaceSelected: (Place) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Text(text = "Map")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Current location: ${currentLocation.lat}, ${currentLocation.lng}",
            style = MaterialTheme.typography.bodySmall
        )
        if (selectedPlace != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isNavigating) "Navigating to ${selectedPlace.name}" else "Selected ${selectedPlace.name}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Locations",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(places.take(14), key = { it.name }) { place ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaceSelected(place) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = place.name)
                        Text(
                            text = "${place.category.name.lowercase()} | ${place.latitude}, ${place.longitude}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

internal object IosLocationService {
    private val manager = CLLocationManager()
    private val delegate = LocationDelegate()
    private val _location = kotlinx.coroutines.flow.MutableStateFlow<GeoPoint?>(null)

    val location: kotlinx.coroutines.flow.StateFlow<GeoPoint?> = _location

    init {
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 5.0
    }

    fun start() {
        when (CLLocationManager.authorizationStatus()) {
            kCLAuthorizationStatusAuthorizedAlways,
            kCLAuthorizationStatusAuthorizedWhenInUse -> manager.startUpdatingLocation()
            kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
        }
        manager.location?.let { publish(it) }
    }

    fun stop() {
        manager.stopUpdatingLocation()
    }

    private fun publish(location: CLLocation) {
        val coordinate = location.coordinate
        _location.value = coordinate.useContents { GeoPoint(latitude, longitude) }
    }

    private class LocationDelegate : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val latest = didUpdateLocations.lastOrNull() as? CLLocation ?: return
            publish(latest)
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        }

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            val status = CLLocationManager.authorizationStatus()
            if (status == kCLAuthorizationStatusAuthorizedAlways ||
                status == kCLAuthorizationStatusAuthorizedWhenInUse
            ) {
                manager.startUpdatingLocation()
                manager.location?.let { publish(it) }
            }
        }
    }
}

@Composable
internal fun IosMapKitView(
    places: List<Place>,
    currentLocation: GeoPoint,
    selectedPlace: Place?,
    activeRoute: Route?,
    isNavigating: Boolean,
    onPlaceSelected: (Place) -> Unit,
    onMapTap: () -> Unit,
    onZoomChanged: (Float) -> Unit
) {
    val latestPlaces by androidx.compose.runtime.rememberUpdatedState(places)
    val latestLocation by androidx.compose.runtime.rememberUpdatedState(currentLocation)
    val latestSelectedPlace by androidx.compose.runtime.rememberUpdatedState(selectedPlace)
    val latestActiveRoute by androidx.compose.runtime.rememberUpdatedState(activeRoute)
    val latestIsNavigating by androidx.compose.runtime.rememberUpdatedState(isNavigating)
    val latestOnPlaceSelected by androidx.compose.runtime.rememberUpdatedState(onPlaceSelected)
    val latestOnMapTap by androidx.compose.runtime.rememberUpdatedState(onMapTap)
    val latestOnZoomChanged by androidx.compose.runtime.rememberUpdatedState(onZoomChanged)

    UIKitView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            val camera = GMSCameraPosition.cameraWithLatitude(
                latitude = campusCenterLatitude,
                longitude = campusCenterLongitude,
                zoom = campusOverviewZoom
            )
            val mapView = GMSMapView.mapWithFrame(
                frame = CGRectZero.readValue(),
                camera = camera
            )
            mapView.myLocationEnabled = true
            mapView.mapStyle = iosCampusMapStyle()
            mapView.delegate = IosGoogleMapDelegate(
                onPlaceSelected = { latestOnPlaceSelected(it) },
                onMapTap = { latestOnMapTap() },
                onZoomChanged = { latestOnZoomChanged(it) }
            )
            mapView
        },
        update = { mapView ->
            val delegate = mapView.delegate as? IosGoogleMapDelegate
            val selectedPlace = latestSelectedPlace
            delegate?.placeLookup = latestPlaces.associateBy { it.name }
            delegate?.onPlaceSelected = { latestOnPlaceSelected(it) }
            delegate?.onMapTap = { latestOnMapTap() }
            delegate?.onZoomChanged = { latestOnZoomChanged(it) }

            when {
                selectedPlace != null -> mapView.camera = GMSCameraPosition.cameraWithLatitude(
                    latitude = selectedPlace.latitude,
                    longitude = selectedPlace.longitude,
                    zoom = 17f
                )
                latestIsNavigating && isPointInsideCampus(latestLocation) -> mapView.camera = GMSCameraPosition.cameraWithLatitude(
                    latitude = latestLocation.lat,
                    longitude = latestLocation.lng,
                    zoom = 16.5f
                )
            }
            mapView.myLocationEnabled = true
            mapView.mapStyle = iosCampusMapStyle()
            mapView.clear()

            latestPlaces.forEach { place ->
                val marker = GMSMarker()
                marker.position = CLLocationCoordinate2DMake(place.latitude, place.longitude)
                marker.title = place.name
                marker.snippet = place.category.name.lowercase()
                marker.iconView = iosMarkerView(place)
                marker.map = mapView
            }

            val activeRoute = latestActiveRoute
            if (latestIsNavigating && activeRoute != null) {
                val remainingPoints = remainingRoutePoints(latestLocation, activeRoute)
                val path = GMSMutablePath()
                remainingPoints.forEach { point ->
                    path.addLatitude(point.lat, longitude = point.lng)
                }
                val polyline = GMSPolyline()
                polyline.path = path
                polyline.strokeWidth = 12.0
                polyline.strokeColor = UIColor.redColor
                polyline.map = mapView
            }
        },
        onRelease = { mapView ->
            mapView.delegate = null
        }
    )
}

internal class IosGoogleMapDelegate(
    var placeLookup: Map<String, Place> = emptyMap(),
    var onPlaceSelected: (Place) -> Unit,
    var onMapTap: () -> Unit,
    var onZoomChanged: (Float) -> Unit
) : NSObject(), GMSMapViewDelegateProtocol {
    override fun mapView(
        mapView: GMSMapView,
        didChangeCameraPosition: GMSCameraPosition
    ) {
        onZoomChanged(didChangeCameraPosition.zoom.toFloat())
    }

    override fun mapView(
        mapView: GMSMapView,
        didTapMarker: GMSMarker
    ): Boolean {
        val title = didTapMarker.title ?: return false
        val place = placeLookup[title] ?: return false
        onPlaceSelected(place)
        return false
    }

    override fun mapView(
        mapView: GMSMapView,
        didTapAtCoordinate: kotlinx.cinterop.CValue<platform.CoreLocation.CLLocationCoordinate2D>
    ) {
        onMapTap()
    }
}

internal fun syncMapAnnotations(
    mapView: MKMapView,
    places: List<Place>,
    currentLocation: GeoPoint,
    selectedPlace: Place?,
    activeRoute: Route?,
    isNavigating: Boolean
) {
    val existingAnnotations = mapView.annotations
        .filterIsInstance<MKAnnotationProtocol>()
        .filterNot { it is MKUserLocation }
    if (existingAnnotations.isNotEmpty()) {
        mapView.removeAnnotations(existingAnnotations)
    }

    val annotations = places.map { place -> CampusPlaceAnnotation(place) }
    if (annotations.isNotEmpty()) {
        mapView.addAnnotations(annotations)
    }

    syncRouteOverlay(
        mapView = mapView,
        currentLocation = currentLocation,
        activeRoute = activeRoute,
        isNavigating = isNavigating
    )

    val clampedRegion = if (isNavigating && activeRoute != null) {
        routeFocusRegion(
            currentLocation = currentLocation,
            route = activeRoute
        )
    } else {
        val mapCenter = selectedPlace?.let { GeoPoint(it.latitude, it.longitude) } ?: currentLocation
        val desiredRegion = MKCoordinateRegionMakeWithDistance(
            centerCoordinate = CLLocationCoordinate2DMake(mapCenter.lat, mapCenter.lng),
            latitudinalMeters = 900.0,
            longitudinalMeters = 900.0
        )
        desiredRegion.useContents {
            campusClampedRegion(
                centerLat = mapCenter.lat,
                centerLng = mapCenter.lng,
                latitudeDelta = span.latitudeDelta,
                longitudeDelta = span.longitudeDelta
            )
        }
    }
    if (!mapRegionApproximatelyEquals(mapView, clampedRegion)) {
        mapView.setRegion(clampedRegion, animated = true)
    }
}

internal fun syncRouteOverlay(
    mapView: MKMapView,
    currentLocation: GeoPoint,
    activeRoute: Route?,
    isNavigating: Boolean
) {
    val existingOverlays = mapView.overlays.filterIsInstance<MKOverlayProtocol>()
    if (existingOverlays.isNotEmpty()) {
        mapView.removeOverlays(existingOverlays)
    }

    if (!isNavigating || activeRoute == null) return

    val routePoints = remainingRoutePoints(currentLocation, activeRoute)
    if (routePoints.size < 2) return

    // Skip drawing the polyline for now; the current iOS simulator toolchain is
    // rejecting CLLocationCoordinate2D array interop even though the rest of
    // navigation remains functional.
}

internal fun remainingRoutePoints(
    currentLocation: GeoPoint,
    route: Route
): List<GeoPoint> {
    if (route.points.isEmpty()) return emptyList()
    val nearestIndex = RouteHelpers.findNearestSegmentIndex(currentLocation, route)
        .coerceIn(0, route.points.lastIndex)
    return buildList {
        add(currentLocation)
        addAll(route.points.drop(nearestIndex))
    }
}

internal fun routeFocusRegion(
    currentLocation: GeoPoint,
    route: Route
) = routeBoundingRegion(remainingRoutePoints(currentLocation, route))

internal fun routeBoundingRegion(points: List<GeoPoint>) = if (points.isEmpty()) {
    campusClampedRegion(
        centerLat = (campusNorthLatitude + campusSouthLatitude) / 2.0,
        centerLng = (campusEastLongitude + campusWestLongitude) / 2.0,
        latitudeDelta = minimumMapSpan * 4.0,
        longitudeDelta = minimumMapSpan * 4.0
    )
} else {
    val minLat = points.minOf { it.lat }
    val maxLat = points.maxOf { it.lat }
    val minLng = points.minOf { it.lng }
    val maxLng = points.maxOf { it.lng }
    val latitudeDelta = ((maxLat - minLat) * 1.35).coerceAtLeast(minimumMapSpan * 4.0)
    val longitudeDelta = ((maxLng - minLng) * 1.35).coerceAtLeast(minimumMapSpan * 4.0)

    campusClampedRegion(
        centerLat = (minLat + maxLat) / 2.0,
        centerLng = (minLng + maxLng) / 2.0,
        latitudeDelta = latitudeDelta,
        longitudeDelta = longitudeDelta
    )
}

internal class CampusPlaceAnnotation(
    val place: Place
) : MKPointAnnotation() {
    init {
        setTitle(place.name)
        setSubtitle(place.category.name.lowercase())
        setCoordinate(CLLocationCoordinate2DMake(place.latitude, place.longitude))
    }
}

internal class CampusMapDelegate(
    var onPlaceSelected: (Place) -> Unit
) : NSObject(), MKMapViewDelegateProtocol {
    private var isApplyingCampusConstraint = false

    override fun mapView(
        mapView: MKMapView,
        viewForAnnotation: MKAnnotationProtocol
    ): MKAnnotationView? {
        if (viewForAnnotation is MKUserLocation) return null

        val reuseId = "campus-place"
        val markerView = (mapView.dequeueReusableAnnotationViewWithIdentifier(reuseId) as? MKMarkerAnnotationView)
            ?: MKMarkerAnnotationView(annotation = viewForAnnotation, reuseIdentifier = reuseId)

        markerView.annotation = viewForAnnotation
        markerView.canShowCallout = true
        markerView.markerTintColor = UIColor.redColor
        return markerView
    }

    override fun mapView(
        mapView: MKMapView,
        rendererForOverlay: MKOverlayProtocol
    ): MKOverlayRenderer {
        val renderer = MKPolylineRenderer(overlay = rendererForOverlay)
        renderer.strokeColor = UIColor.blueColor
        renderer.lineWidth = 8.0
        renderer.alpha = 0.95
        return renderer
    }

    override fun mapView(
        mapView: MKMapView,
        didSelectAnnotationView: MKAnnotationView
    ) {
        val annotation = didSelectAnnotationView.annotation as? CampusPlaceAnnotation ?: return
        onPlaceSelected(annotation.place)
    }

    override fun mapView(
        mapView: MKMapView,
        regionDidChangeAnimated: Boolean
    ) {
        if (isApplyingCampusConstraint) return

        val clampedRegion = mapView.region.useContents {
            campusClampedRegion(
                centerLat = center.latitude,
                centerLng = center.longitude,
                latitudeDelta = span.latitudeDelta,
                longitudeDelta = span.longitudeDelta
            )
        }

        if (!mapRegionApproximatelyEquals(mapView, clampedRegion)) {
            isApplyingCampusConstraint = true
            mapView.setRegion(clampedRegion, animated = false)
            isApplyingCampusConstraint = false
        }
    }
}

private const val campusNorthLatitude = 30.776094
private const val campusSouthLatitude = 30.763469
private const val campusEastLongitude = 76.582640
private const val campusWestLongitude = 76.562945
private const val campusOverviewZoom = 15.7f
private const val minimumMapSpan = 0.0012
private const val regionComparisonTolerance = 0.00005

private const val campusCenterLatitude = (campusNorthLatitude + campusSouthLatitude) / 2.0
private const val campusCenterLongitude = (campusEastLongitude + campusWestLongitude) / 2.0
private val campusLatitudeSpan = campusNorthLatitude - campusSouthLatitude
private val campusLongitudeSpan = campusEastLongitude - campusWestLongitude

private const val iosCampusMapStyleJson = """
[
  { "featureType": "all", "elementType": "labels", "stylers": [{ "visibility": "off" }] },
  { "featureType": "poi", "stylers": [{ "visibility": "off" }] }
]
"""

internal fun iosCampusMapStyle(): GMSMapStyle? =
    GMSMapStyle.styleWithJSONString(iosCampusMapStyleJson, null)

internal fun iosMarkerView(place: Place): UIView {
    val labelText = place.name
    val estimatedTextWidth = (labelText.length * 8.0).coerceIn(28.0, 84.0)
    val chipWidth = estimatedTextWidth + 24.0
    val chipLeft = 8.0
    val pinWidth = 36.0
    val pinLeft = chipLeft + ((chipWidth - pinWidth) / 2.0)
    val containerWidth = chipLeft * 2.0 + chipWidth

    val container = UIView(frame = platform.CoreGraphics.CGRectMake(0.0, 0.0, containerWidth, 62.0))

    val chip = UIView(frame = platform.CoreGraphics.CGRectMake(chipLeft, 0.0, chipWidth, 28.0))
    chip.backgroundColor = UIColor.whiteColor
    chip.layer.cornerRadius = 12.0
    chip.layer.borderWidth = 1.0
    chip.layer.borderColor = UIColor.colorWithWhite(0.0, alpha = 0.08).CGColor

    val label = UILabel(frame = platform.CoreGraphics.CGRectMake(12.0, 4.0, chipWidth - 24.0, 20.0))
    label.text = labelText
    label.font = UIFont.boldSystemFontOfSize(12.0)
    label.textColor = UIColor.blackColor
    label.textAlignment = UITextAlignmentLeft
    chip.addSubview(label)

    val pinBubble = UIView(frame = platform.CoreGraphics.CGRectMake(pinLeft, 30.0, 36.0, 28.0))
    pinBubble.backgroundColor = UIColor.whiteColor
    pinBubble.layer.cornerRadius = 18.0
    pinBubble.layer.borderWidth = 1.0
    pinBubble.layer.borderColor = UIColor.colorWithWhite(0.0, alpha = 0.08).CGColor

    val iconLabel = UILabel(frame = platform.CoreGraphics.CGRectMake(0.0, 2.0, 36.0, 22.0))
    iconLabel.text = iosMarkerEmoji(place.category)
    iconLabel.font = UIFont.systemFontOfSize(18.0)
    iconLabel.textAlignment = UITextAlignmentCenter
    pinBubble.addSubview(iconLabel)

    container.addSubview(chip)
    container.addSubview(pinBubble)
    return container
}

internal fun iosMarkerEmoji(category: PlaceCategory): String = when (category) {
    PlaceCategory.BLOCK -> "\uD83C\uDFE2"
    PlaceCategory.PARK -> "\uD83C\uDF33"
    PlaceCategory.BANK -> "\uD83C\uDFE6"
    PlaceCategory.HOSTEL -> "\uD83C\uDFE0"
    PlaceCategory.GATE -> "\u26E9"
    else -> "\uD83D\uDCCD"
}

internal fun isPointInsideCampus(point: GeoPoint): Boolean {
    return point.lat in campusSouthLatitude..campusNorthLatitude &&
        point.lng in campusWestLongitude..campusEastLongitude
}

internal fun campusClampedRegion(
    centerLat: Double,
    centerLng: Double,
    latitudeDelta: Double,
    longitudeDelta: Double
) = MKCoordinateRegionMake(
    CLLocationCoordinate2DMake(
        clampLatitude(centerLat, latitudeDelta),
        clampLongitude(centerLng, longitudeDelta)
    ),
    MKCoordinateSpanMake(
        clampLatitudeDelta(latitudeDelta),
        clampLongitudeDelta(longitudeDelta)
    )
)

internal fun clampLatitudeDelta(latitudeDelta: Double): Double =
    min(max(latitudeDelta, minimumMapSpan), campusLatitudeSpan)

internal fun clampLongitudeDelta(longitudeDelta: Double): Double =
    min(max(longitudeDelta, minimumMapSpan), campusLongitudeSpan)

internal fun clampLatitude(centerLat: Double, requestedLatitudeDelta: Double): Double {
    val clampedDelta = clampLatitudeDelta(requestedLatitudeDelta)
    val halfSpan = clampedDelta / 2.0
    return min(max(centerLat, campusSouthLatitude + halfSpan), campusNorthLatitude - halfSpan)
}

internal fun clampLongitude(centerLng: Double, requestedLongitudeDelta: Double): Double {
    val clampedDelta = clampLongitudeDelta(requestedLongitudeDelta)
    val halfSpan = clampedDelta / 2.0
    return min(max(centerLng, campusWestLongitude + halfSpan), campusEastLongitude - halfSpan)
}

internal fun mapRegionApproximatelyEquals(
    mapView: MKMapView,
    region: kotlinx.cinterop.CValue<platform.MapKit.MKCoordinateRegion>
): Boolean {
    val currentRegion = mapView.region
    val (currentCenterLat, currentCenterLng, currentLatDelta, currentLngDelta) = currentRegion.useContents {
        listOf(center.latitude, center.longitude, span.latitudeDelta, span.longitudeDelta)
    }
    val (targetCenterLat, targetCenterLng, targetLatDelta, targetLngDelta) = region.useContents {
        listOf(center.latitude, center.longitude, span.latitudeDelta, span.longitudeDelta)
    }
    return abs(currentCenterLat - targetCenterLat) < regionComparisonTolerance &&
        abs(currentCenterLng - targetCenterLng) < regionComparisonTolerance &&
        abs(currentLatDelta - targetLatDelta) < regionComparisonTolerance &&
        abs(currentLngDelta - targetLngDelta) < regionComparisonTolerance
}
