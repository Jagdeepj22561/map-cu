@file:OptIn(ExperimentalForeignApi::class)

package com.example.shared.repository

import cocoapods.FirebaseCore.FIRApp
import cocoapods.FirebaseFirestore.FIRDocumentReference
import cocoapods.FirebaseFirestore.FIRFirestore
import cocoapods.FirebaseFirestore.FIRCollectionReference
import cocoapods.FirebaseFirestore.FIRQuery
import cocoapods.FirebaseFirestore.FIRFieldValue
import cocoapods.FirebaseFirestore.FIRSetOptions
import com.example.shared.GeoPoint
import com.example.shared.Place
import com.example.shared.PlaceCategory
import com.example.shared.Route
import com.example.shared.RoutesData
import com.example.shared.campusPlaces
import com.example.shared.data.SupabaseClientProvider
import com.example.shared.data.AuthSessionManager
import io.github.jan.supabase.auth.auth
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object IosPlatformConfig {
    const val backendBaseUrl = "https://campus-map-backend-fpz8.onrender.com"
    val supabaseUrl: String
        get() = (NSBundle.mainBundle.objectForInfoDictionaryKey("SUPABASE_URL") as? String).orEmpty().trimEnd('/')
    val supabasePublishableKey: String
        get() = (NSBundle.mainBundle.objectForInfoDictionaryKey("SUPABASE_PUBLISHABLE_KEY") as? String).orEmpty()
}

internal data class IosAuthSession(
    val uid: String,
    val email: String,
    val idToken: String = "",
    val refreshToken: String?
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "email" to email,
        "idToken" to idToken,
        "refreshToken" to refreshToken
    )
}

data class IosUserProfile(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val year: String = "",
    val semester: String = "",
    val course: String = "",
    val dob: String = "",
    val profilePicUrl: String = "",
    val instagramLink: String = "",
    val snapchatLink: String = "",
    val linkedinLink: String = "",
    val lastUpdated: Long = 0L,
    val location: GeoPoint? = null,
    val ghostMode: Boolean = false
) {
    fun toPureUser() = com.example.shared.model.PureUser(
        uid = uid,
        email = email,
        name = name,
        phoneNumber = phoneNumber,
        year = year,
        semester = semester,
        course = course,
        dob = dob,
        profilePicUrl = profilePicUrl,
        lastUpdated = lastUpdated,
        location = location
    )

    fun toFirebaseMap(): Map<String, Any?> = buildMap {
        put("uid", uid)
        put("email", email)
        put("name", name)
        put("phoneNumber", phoneNumber)
        put("year", year)
        put("semester", semester)
        put("course", course)
        put("dob", dob)
        put("profilePicUrl", profilePicUrl)
        put("instagramLink", instagramLink)
        put("snapchatLink", snapchatLink)
        put("linkedinLink", linkedinLink)
        put("lastUpdated", lastUpdated)
        put("ghostMode", ghostMode)
        val point = location
        if (point != null) {
            put("latitude", point.lat)
            put("longitude", point.lng)
            put(
                "location",
                mapOf(
                    "latitude" to point.lat,
                    "longitude" to point.lng
                )
            )
        }
    }

    fun toSupabaseMap(): Map<String, Any?> = mapOf(
        "id" to uid, "email" to email, "name" to name,
        "phone_number" to phoneNumber, "year" to year, "semester" to semester,
        "course" to course, "dob" to dob, "profile_pic_url" to profilePicUrl,
        "instagram_link" to instagramLink, "snapchat_link" to snapchatLink,
        "linkedin_link" to linkedinLink, "last_updated" to lastUpdated,
        "latitude" to (location?.lat ?: 0.0), "longitude" to (location?.lng ?: 0.0),
        "ghost_mode" to ghostMode
    )
}

internal object IosCacheStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    @OptIn(ExperimentalForeignApi::class)
    fun saveJson(key: String, value: Any?) {
        if (value == null) {
            defaults.removeObjectForKey(key)
            defaults.synchronize()
            return
        }
        val data = NSJSONSerialization.dataWithJSONObject(value, 0u, null) ?: return
        defaults.setObject(data, forKey = key)
        defaults.synchronize()
    }

    @OptIn(ExperimentalForeignApi::class)
    fun readJson(key: String): Any? {
        val data = defaults.objectForKey(key) as? NSData ?: return null
        return NSJSONSerialization.JSONObjectWithData(data, 0u, null)
    }

    fun saveString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key)
        else defaults.setObject(value, forKey = key)
        defaults.synchronize()
    }

    fun readString(key: String): String? = defaults.stringForKey(key)

    fun saveLong(key: String, value: Long) {
        defaults.setObject(value.toString(), forKey = key)
        defaults.synchronize()
    }

    fun readLong(key: String): Long? = defaults.stringForKey(key)?.toLongOrNull()

    fun saveBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
        defaults.synchronize()
    }

    fun readBoolean(key: String, default: Boolean = false): Boolean {
        return if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)
    }
}

internal object IosSessionStore {
    private const val sessionCacheKey = "ios.auth.session"

    private val _session = MutableStateFlow(loadFromCache())
    val session: StateFlow<IosAuthSession?> = _session

    fun current(): IosAuthSession? {
        // Supabase sessions are persisted locally after a successful login.
        // Firebase Auth is no longer the authority for an iOS session.
        return _session.value
    }

    fun save(session: IosAuthSession) {
        _session.value = session
        IosCacheStore.saveJson(sessionCacheKey, session.toMap())
    }

    fun clear() {
        _session.value = null
        IosCacheStore.saveJson(sessionCacheKey, null)
    }

    fun hasActiveSession(): Boolean = current() != null

    private fun loadFromCache(): IosAuthSession? {
        val map = IosCacheStore.readJson(sessionCacheKey).asStringMap() ?: return null
        val uid = map["uid"].stringValue().takeIf { it.isNotBlank() } ?: return null
        val email = map["email"].stringValue()
        val idToken = map["idToken"].stringValue()
        val refreshToken = map["refreshToken"].stringValue().takeIf { it.isNotBlank() }
        return IosAuthSession(
            uid = uid,
            email = email,
            idToken = idToken,
            refreshToken = refreshToken
        )
    }

}

internal object IosPreferencesStore {
    private const val preferencesTable = "preferences"
    private const val darkModeKey = "darkMode"
    private const val audioKey = "audio"
    private const val ghostModeKey = "ghostMode"
    private const val lastAnnouncementSeenKey = "lastAnnouncementSeen"

    fun isDarkMode(): Boolean = readPreferences()[darkModeKey].booleanValue(false)

    fun setDarkMode(enabled: Boolean) {
        writePreference(darkModeKey, enabled)
    }

    fun isAudioEnabled(): Boolean = readPreferences()[audioKey].booleanValue(true)

    fun setAudioEnabled(enabled: Boolean) {
        writePreference(audioKey, enabled)
    }

    fun isGhostMode(): Boolean = readPreferences()[ghostModeKey].booleanValue(false)

    fun setGhostMode(enabled: Boolean) {
        writePreference(ghostModeKey, enabled)
    }

    fun lastAnnouncementSeen(): Long = readPreferences()[lastAnnouncementSeenKey].longValue()

    fun setLastAnnouncementSeen(timestamp: Long) {
        writePreference(lastAnnouncementSeenKey, timestamp)
    }

    fun isGroupNotificationsEnabled(groupId: String): Boolean {
        return readPreferences()["group.notifications.$groupId"].booleanValue(true)
    }

    fun setGroupNotificationsEnabled(groupId: String, enabled: Boolean) {
        writePreference("group.notifications.$groupId", enabled)
    }

    private fun readPreferences(): MutableMap<String, Any?> {
        return (IosLocalDatabaseStore.readJson(preferencesTable).asStringMap() ?: emptyMap()).toMutableMap()
    }

    private fun writePreference(key: String, value: Any?) {
        val updated = readPreferences()
        updated[key] = value
        IosLocalDatabaseStore.saveJson(preferencesTable, updated)
    }
}

internal object IosLocalDatabaseStore {
    private const val rootFolderName = "maps123_local_db"
    private const val placesTable = "places"
    private const val routesTable = "routes"

    fun saveJson(table: String, value: Any?) {
        ensureMapSeedData()
        IosCacheStore.saveJson(localKey(table), value)
    }

    fun readJson(table: String): Any? {
        ensureMapSeedData()
        return IosCacheStore.readJson(localKey(table))
    }

    fun loadPlaces(): List<Place> {
        return readJson(placesTable).asList()
            ?.mapNotNull { it.asStringMap()?.toPlace() }
            ?.takeIf { it.isNotEmpty() }
            ?: campusPlaces
    }

    fun loadRoutes(): List<Route> {
        return readJson(routesTable).asList()
            ?.mapNotNull { it.asStringMap()?.toRoute() }
            ?.takeIf { it.isNotEmpty() }
            ?: RoutesData.allRoutes
    }

    private fun ensureMapSeedData() {
        if (readRawJson(placesTable) == null) {
            writeRawJson(localKey(placesTable), campusPlaces.map { it.toMap() })
        }
        if (readRawJson(routesTable) == null) {
            writeRawJson(localKey(routesTable), RoutesData.allRoutes.map { it.toMap() })
        }
    }

    private fun readRawJson(table: String): Any? {
        return IosCacheStore.readJson(localKey(table))
    }

    private fun writeRawJson(path: String, value: Any?) {
        IosCacheStore.saveJson(path, value)
    }

    private fun ensureDirectory() {
    }

    private fun tablePath(table: String): String {
        return localKey(table)
    }

    private fun currentDatabaseDirectory(): String {
        val baseDir = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() as? String
            ?: NSTemporaryDirectory()
        return "$baseDir/$rootFolderName/${currentDatabaseName()}"
    }

    private fun currentDatabaseName(): String {
        val uid = IosSessionStore.current()?.uid ?: "guest"
        return "chat_database_$uid"
    }

    private fun sanitizeTableName(table: String): String {
        return table.map { ch ->
            if (ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-') ch else '_'
        }.joinToString("")
    }

    private fun localKey(table: String): String {
        return "${rootFolderName}.${currentDatabaseName()}.${sanitizeTableName(table)}"
    }
}

internal data class IosHttpResponse(
    val statusCode: Int,
    val json: Any?,
    val data: NSData?
)

internal object IosFirebaseSdk {
    private var configured = false

    fun ensureConfigured() {
        if (configured) return
        if (FIRApp.defaultApp() == null) {
            FIRApp.configure()
        }
        configured = true
    }

}

internal object IosHttpClient {
    @OptIn(ExperimentalForeignApi::class)
    suspend fun requestJson(
        urlString: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: Any? = null,
        contentType: String? = if (body != null) "application/json" else null
    ): IosHttpResponse {
        val url = NSURL(string = urlString) ?: throw IllegalArgumentException("Invalid URL: $urlString")
        val request = NSMutableURLRequest.requestWithURL(url) as NSMutableURLRequest
        request.setHTTPMethod(method)
        headers.forEach { (key, value) ->
            request.setValue(value, forHTTPHeaderField = key)
        }
        if (contentType != null) {
            request.setValue(contentType, forHTTPHeaderField = "Content-Type")
        }
        if (body != null) {
            request.setHTTPBody(NSJSONSerialization.dataWithJSONObject(body, 0u, null) as NSData?)
        }

        return suspendCancellableCoroutine { continuation ->
            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data: NSData?, response: NSURLResponse?, error: NSError? ->
                if (error != null) {
                    continuation.resumeWithException(Throwable(error.toString()))
                } else {
                    val statusCode = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
                    val json = if (data != null && data.length > 0u) {
                        runCatching { NSJSONSerialization.JSONObjectWithData(data, NSJSONReadingAllowFragments, null) }.getOrNull()
                    } else {
                        null
                    }
                    continuation.resume(
                        IosHttpResponse(
                            statusCode = statusCode,
                            json = json,
                            data = data
                        )
                    )
                }
            }
            continuation.invokeOnCancellation {
                task.cancel()
            }
            task.resume()
        }
    }
}

internal object IosAuthApi {
    suspend fun login(email: String, password: String): IosAuthSession {
        requireSupabaseConfiguration()
        val response = IosHttpClient.requestJson(
            urlString = "${IosPlatformConfig.supabaseUrl}/auth/v1/token?grant_type=password",
            method = "POST",
            headers = supabaseHeaders(),
            body = mapOf("email" to email.trim(), "password" to password)
        )
        val payload = ensureSuccess(response)
        val user = payload["user"].asStringMap() ?: throw Exception("Login failed")
        val session = IosAuthSession(
            uid = user["id"].stringValue().ifBlank { throw Exception("Login failed") },
            email = user["email"].stringValue().ifBlank { email },
            idToken = payload["access_token"].stringValue(),
            refreshToken = payload["refresh_token"].stringValue().takeIf { it.isNotBlank() }
        )
        IosSessionStore.save(session)
        importSharedSession(session)
        return session
    }

    suspend fun restoreSharedSession() {
        val auth = SupabaseClientProvider.client.auth
        auth.awaitInitialization()
        val storedSession = IosSessionStore.current()
        AuthSessionManager.accessTokenOrNull()
        val sharedSession = auth.currentSessionOrNull()
        if (sharedSession != null) {
            IosSessionStore.save(
                IosAuthSession(
                    uid = sharedSession.user?.id ?: storedSession?.uid.orEmpty(),
                    email = sharedSession.user?.email ?: storedSession?.email.orEmpty(),
                    idToken = sharedSession.accessToken,
                    refreshToken = sharedSession.refreshToken.takeIf(String::isNotBlank)
                )
            )
        } else if (storedSession != null) {
            importSharedSession(storedSession)
        }
    }

    suspend fun logout() {
        runCatching { SupabaseClientProvider.client.auth.signOut() }
        IosSessionStore.clear()
    }

    private suspend fun importSharedSession(session: IosAuthSession) {
        SupabaseClientProvider.client.auth.importAuthToken(
            accessToken = session.idToken,
            refreshToken = session.refreshToken.orEmpty(),
            retrieveUser = true,
            autoRefresh = !session.refreshToken.isNullOrBlank()
        )
    }

    suspend fun sendPasswordReset(email: String) {
        requireSupabaseConfiguration()
        val response = IosHttpClient.requestJson(
            urlString = "${IosPlatformConfig.supabaseUrl}/auth/v1/recover",
            method = "POST",
            headers = supabaseHeaders(),
            body = mapOf("email" to email.trim(), "redirect_to" to "https://campus-map-backend-fpz8.onrender.com/auth/callback")
        )
        ensureSuccess(response)
    }

    suspend fun generateOtp(email: String) {
        val response = IosHttpClient.requestJson(
            urlString = "${IosPlatformConfig.backendBaseUrl}/generate-otp",
            method = "POST",
            headers = backendHeaders(),
            body = mapOf("email" to email)
        )
        val payload = ensureSuccess(response)
        val status = payload["status"].stringValue()
        if (status != "OTP_SENT") {
            throw Exception(payload.errorMessage())
        }
    }

    suspend fun verifyAndRegister(email: String, otp: String, name: String, password: String): IosAuthSession {
        val response = IosHttpClient.requestJson(
            urlString = "${IosPlatformConfig.backendBaseUrl}/verify-otp-create",
            method = "POST",
            headers = backendHeaders(),
            body = mapOf(
                "email" to email,
                "otp" to otp,
                "password" to password,
                "name" to name
            )
        )
        val payload = ensureSuccess(response)
        val status = payload["status"].stringValue()
        if (status != "USER_CREATED" && status != "VERIFIED") {
            throw Exception(payload.errorMessage())
        }
        return login(email, password)
    }

    private fun backendHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json"
    )

    private fun supabaseHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "apikey" to IosPlatformConfig.supabasePublishableKey
    )

    private fun requireSupabaseConfiguration() {
        check(IosPlatformConfig.supabaseUrl.isNotBlank() && IosPlatformConfig.supabasePublishableKey.isNotBlank()) {
            "Supabase is not configured in iOS Info.plist"
        }
    }

    private fun ensureSuccess(response: IosHttpResponse): Map<String, Any?> {
        val payload = response.json.asStringMap() ?: emptyMap()
        if (response.statusCode !in 200..299) {
            throw Exception(payload.errorMessage())
        }
        return payload
    }
}

/** Minimal PostgREST client so iOS does not need Firebase for profile data. */
internal object IosSupabase {
    private suspend fun headers(extra: Map<String, String> = emptyMap()): Map<String, String> {
        val accessToken = AuthSessionManager.accessTokenOrNull()
        return buildMap {
            put("apikey", IosPlatformConfig.supabasePublishableKey)
            put("Accept", "application/json")
            accessToken?.takeIf { it.isNotBlank() }?.let { put("Authorization", "Bearer $it") }
            putAll(extra)
        }
    }

    suspend fun request(path: String, method: String = "GET", body: Any? = null, extraHeaders: Map<String, String> = emptyMap()): IosHttpResponse {
        check(IosPlatformConfig.supabaseUrl.isNotBlank() && IosPlatformConfig.supabasePublishableKey.isNotBlank()) { "Supabase is not configured in iOS Info.plist" }
        return IosHttpClient.requestJson("${IosPlatformConfig.supabaseUrl}/rest/v1/$path", method, headers(extraHeaders), body)
    }

    fun requireSuccess(response: IosHttpResponse): Any? {
        if (response.statusCode !in 200..299) {
            val message = response.json.asStringMap()?.get("message").stringValue().ifBlank { "Supabase request failed" }
            throw Exception(message)
        }
        return response.json
    }
}

internal object IosFirestore {
    fun db(): FIRFirestore {
        IosFirebaseSdk.ensureConfigured()
        return FIRFirestore.firestore()
    }
}

internal object IosFirestoreRefs {
    val users: FIRCollectionReference get() = IosFirestore.db().collectionWithPath("users")
    val announcements: FIRCollectionReference get() = IosFirestore.db().collectionWithPath("announcements")
    val reports: FIRCollectionReference get() = IosFirestore.db().collectionWithPath("reports")
    val notifications: FIRCollectionReference get() = IosFirestore.db().collectionWithPath("notifications")
    val userChats: FIRCollectionReference get() = IosFirestore.db().collectionWithPath("user_chats")
    val friends: FIRCollectionReference get() = IosFirestore.db().collectionWithPath("friends")
    val friendRequests: FIRCollectionReference get() = IosFirestore.db().collectionWithPath("friend_requests")
    val messages: FIRCollectionReference get() = IosFirestore.db().collectionWithPath("messages")
    val groupMessages: FIRCollectionReference get() = IosFirestore.db().collectionWithPath("group_messages")
    val groups: FIRCollectionReference get() = IosFirestore.db().collectionWithPath("groups")

    fun user(uid: String): FIRDocumentReference = users.documentWithPath(uid)
    fun userChats(uid: String): FIRCollectionReference = userChats.documentWithPath(uid).collectionWithPath("chats")
    fun userGroups(uid: String): FIRCollectionReference = user(uid).collectionWithPath("groups")
    fun userFriends(uid: String): FIRCollectionReference = friends.documentWithPath(uid).collectionWithPath("list")
    fun userFriendRequests(uid: String): FIRCollectionReference = friendRequests.documentWithPath(uid).collectionWithPath("requests")
    fun userBlocks(uid: String): FIRCollectionReference = user(uid).collectionWithPath("blocks")
    fun chatMessages(chatId: String): FIRCollectionReference = messages.documentWithPath(chatId).collectionWithPath("msgs")
    fun group(groupId: String): FIRDocumentReference = groups.documentWithPath(groupId)
    fun groupMessages(groupId: String): FIRCollectionReference = groupMessages.documentWithPath(groupId).collectionWithPath("msgs")
    fun announcement(id: String): FIRDocumentReference = announcements.documentWithPath(id)
}

private fun String.trimQueryString(): String = trim().trim('"')

private fun String.toFirebaseQueryValue(): Any {
    val normalized = trimQueryString()
    return when {
        normalized.equals("true", ignoreCase = true) -> true
        normalized.equals("false", ignoreCase = true) -> false
        normalized.toLongOrNull() != null -> normalized.toLong()
        normalized.toDoubleOrNull() != null -> normalized.toDouble()
        else -> normalized
    }
}

internal object IosUserStore {
    private const val usersCacheKey = "ios.cache.users"
    internal const val userRefreshFreshMs = 2 * 60 * 1000L
    private const val minLocationSyncIntervalMs = 60 * 1000L
    private const val forcedLocationSyncIntervalMs = 5 * 60 * 1000L
    private const val minLocationDeltaMeters = 25.0
    private const val ignoreTinyLocationDeltaMeters = 8.0

    private val userCache = loadUserCache().toMutableMap()
    private var loadedCacheOwnerUid: String? = IosSessionStore.current()?.uid
    private var lastLocationSync: LocationSyncState? = null
    private val _currentUser = MutableStateFlow(currentSessionUser())
    val currentUser: StateFlow<IosUserProfile?> = _currentUser

    suspend fun refreshCurrentUser(): IosUserProfile? {
        ensureLocalCacheLoaded()
        val uid = IosSessionStore.current()?.uid ?: return null
        val cached = userCache[uid]
        if (cached != null && !cached.isUserRefreshStale()) {
            _currentUser.value = cached
            return cached
        }
        val remote = fetchRemoteUser(uid)
        if (remote != null) {
            cacheUser(remote)
            _currentUser.value = remote
            return remote
        }
        val fallback = cached
        _currentUser.value = fallback
        return fallback
    }

    fun resetCurrentUser() {
        ensureLocalCacheLoaded()
        _currentUser.value = currentSessionUser()
    }

    suspend fun saveUser(profile: IosUserProfile) {
        ensureLocalCacheLoaded()
        val updated = profile.copy(lastUpdated = currentTimeMillis())
        IosSupabase.requireSuccess(IosSupabase.request(
            path = "profiles?on_conflict=id",
            method = "POST",
            body = updated.toSupabaseMap(),
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
        ))
        cacheUser(updated)
        if (updated.uid == IosSessionStore.current()?.uid) {
            _currentUser.value = updated
        }
    }

    suspend fun updateUserProfile(profile: IosUserProfile) {
        ensureLocalCacheLoaded()
        val updated = profile.copy(lastUpdated = currentTimeMillis())
        IosSupabase.requireSuccess(IosSupabase.request(
            path = "profiles?id=eq.${updated.uid}", method = "PATCH", body = updated.toSupabaseMap(),
            extraHeaders = mapOf("Prefer" to "return=minimal")
        ))
        cacheUser(updated)
        if (updated.uid == IosSessionStore.current()?.uid) {
            _currentUser.value = updated
        }
    }

    suspend fun updateGhostMode(enabled: Boolean) {
        ensureLocalCacheLoaded()
        val session = IosSessionStore.current() ?: return
        val current = _currentUser.value ?: getUser(session.uid) ?: return
        val updated = current.copy(
            ghostMode = enabled,
            lastUpdated = currentTimeMillis()
        )
        IosSupabase.requireSuccess(IosSupabase.request(
            path = "profiles?id=eq.${session.uid}", method = "PATCH",
            body = mapOf("ghost_mode" to enabled, "last_updated" to updated.lastUpdated),
            extraHeaders = mapOf("Prefer" to "return=minimal")
        ))
        cacheUser(updated)
        _currentUser.value = updated
        IosPreferencesStore.setGhostMode(enabled)
    }

    suspend fun updateLocation(lat: Double, lng: Double) {
        ensureLocalCacheLoaded()
        val session = IosSessionStore.current() ?: return
        val now = currentTimeMillis()
        val current = (_currentUser.value ?: getUser(session.uid)) ?: return
        val updatedLocal = current.copy(location = GeoPoint(lat, lng))
        if (!shouldWriteLocation(lat, lng, now)) {
            cacheUser(updatedLocal)
            _currentUser.value = updatedLocal
            return
        }
        IosSupabase.requireSuccess(IosSupabase.request(
            path = "profiles?id=eq.${session.uid}", method = "PATCH",
            body = mapOf("latitude" to lat, "longitude" to lng, "last_updated" to now),
            extraHeaders = mapOf("Prefer" to "return=minimal")
        ))
        lastLocationSync = LocationSyncState(lat, lng, now)
        val updated = current.copy(
            lastUpdated = now,
            location = GeoPoint(lat, lng)
        )
        cacheUser(updated)
        _currentUser.value = updated
    }

    suspend fun getUser(uid: String): IosUserProfile? {
        ensureLocalCacheLoaded()
        userCache[uid]?.let { return it }
        val remote = fetchRemoteUser(uid) ?: return null
        cacheUser(remote)
        if (uid == IosSessionStore.current()?.uid) {
            _currentUser.value = remote
        }
        return remote
    }

    suspend fun refreshUser(uid: String): IosUserProfile? {
        ensureLocalCacheLoaded()
        userCache[uid]?.takeUnless { it.isUserRefreshStale() }?.let { cached ->
            if (uid == IosSessionStore.current()?.uid) {
                _currentUser.value = cached
            }
            return cached
        }
        val remote = fetchRemoteUser(uid)
        if (remote != null) {
            cacheUser(remote)
            if (uid == IosSessionStore.current()?.uid) {
                _currentUser.value = remote
            }
            return remote
        }
        return userCache[uid]
    }

    fun cachedUser(uid: String): IosUserProfile? {
        ensureLocalCacheLoaded()
        return userCache[uid]
    }

    private suspend fun fetchRemoteUser(uid: String): IosUserProfile? {
        val response = IosSupabase.request("profiles?id=eq.$uid&select=*")
        val data = (IosSupabase.requireSuccess(response) as? List<*>)
            ?.firstOrNull()?.asStringMap() ?: return null
        return data.toSupabaseUserProfile()
    }

    private fun cacheUser(user: IosUserProfile) {
        userCache[user.uid] = user
        persistCache()
    }

    private fun persistCache() {
        IosLocalDatabaseStore.saveJson(
            usersCacheKey,
            userCache.mapValues { (_, value) -> value.toFirebaseMap() }
        )
    }

    private fun loadUserCache(): Map<String, IosUserProfile> {
        val raw = IosLocalDatabaseStore.readJson(usersCacheKey).asStringMap() ?: return emptyMap()
        return raw.mapValuesNotNull { entry ->
            entry.value.asStringMap()?.toIosUserProfile()
        }
    }

    private fun currentSessionUser(): IosUserProfile? {
        val uid = IosSessionStore.current()?.uid ?: return null
        return userCache[uid]
    }

    private fun ensureLocalCacheLoaded() {
        val currentOwner = IosSessionStore.current()?.uid
        if (loadedCacheOwnerUid == currentOwner) return
        loadedCacheOwnerUid = currentOwner
        lastLocationSync = null
        userCache.clear()
        userCache.putAll(loadUserCache())
        _currentUser.value = currentSessionUser()
    }

    private fun shouldWriteLocation(lat: Double, lng: Double, now: Long): Boolean {
        val previous = lastLocationSync ?: return true
        val ageMs = now - previous.timestamp
        val movedMeters = locationDistanceMeters(previous.lat, previous.lng, lat, lng)

        if (ageMs >= forcedLocationSyncIntervalMs) return true
        if (movedMeters < ignoreTinyLocationDeltaMeters) return false
        if (ageMs < minLocationSyncIntervalMs && movedMeters < minLocationDeltaMeters) return false
        return true
    }

    private fun locationDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val latDelta = kotlin.math.sin(Math.toRadians(lat2 - lat1) / 2)
        val lngDelta = kotlin.math.sin(Math.toRadians(lng2 - lng1) / 2)
        val startLat = Math.toRadians(lat1)
        val endLat = Math.toRadians(lat2)
        val haversine = latDelta * latDelta +
            kotlin.math.cos(startLat) * kotlin.math.cos(endLat) * lngDelta * lngDelta
        return earthRadiusMeters * 2 * kotlin.math.atan2(kotlin.math.sqrt(haversine), kotlin.math.sqrt(1 - haversine))
    }

    private data class LocationSyncState(
        val lat: Double,
        val lng: Double,
        val timestamp: Long
    )
}

private fun IosUserProfile.isUserRefreshStale(): Boolean {
    if (lastUpdated <= 0L) return true
    return currentTimeMillis() - lastUpdated > IosUserStore.userRefreshFreshMs
}

internal fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970() * 1000.0).toLong()

internal fun <K, V, R : Any> Map<K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> R?): Map<K, R> {
    return entries.mapNotNull { entry ->
        val result = transform(entry) ?: return@mapNotNull null
        entry.key to result
    }.toMap()
}

internal fun Any?.asStringMap(): Map<String, Any?>? {
    val source = this as? Map<*, *> ?: return null
    return source.entries.associate { (key, value) -> key.toString() to value }
}

internal fun Any?.asList(): List<Any?>? = this as? List<Any?>

internal fun Any?.stringValue(): String = when (this) {
    null -> ""
    is String -> this
    is NSNumber -> stringValue
    else -> toString()
}

internal fun Any?.longValue(): Long = when (this) {
    null -> 0L
    is Long -> this
    is Int -> toLong()
    is Double -> toLong()
    is Float -> toLong()
    is Number -> toLong()
    is String -> toLongOrNull() ?: 0L
    is NSNumber -> longLongValue
    else -> 0L
}

internal fun Any?.doubleValue(): Double = when (this) {
    null -> 0.0
    is Double -> this
    is Float -> toDouble()
    is Int -> toDouble()
    is Long -> toDouble()
    is Number -> toDouble()
    is String -> toDoubleOrNull() ?: 0.0
    is NSNumber -> doubleValue
    else -> 0.0
}

internal fun Any?.booleanValue(default: Boolean = false): Boolean = when (this) {
    null -> default
    is Boolean -> this
    is String -> equals("true", ignoreCase = true) || this == "1"
    is NSNumber -> boolValue
    else -> default
}

internal fun Map<String, Any?>?.errorMessage(): String {
    if (this == null) return "Request failed"
    val direct = this["message"].stringValue().takeIf { it.isNotBlank() }
        ?: this["error"].stringValue().takeIf { it.isNotBlank() }
    if (direct != null) return direct.firebaseFriendlyMessage()
    val nested = this["error"].asStringMap()
    return nested?.get("message").stringValue().takeIf { it.isNotBlank() }?.firebaseFriendlyMessage()
        ?: "Request failed"
}

@OptIn(ExperimentalForeignApi::class)
internal fun String.urlEncode(): String {
    val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"
    return buildString(length) {
        this@urlEncode.forEach { ch ->
            if (allowed.indexOf(ch) >= 0) {
                append(ch)
            } else {
                ch.toString().encodeToByteArray().forEach { byte ->
                    append('%')
                    append((byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
    }
}

internal fun Map<String, Any?>.toIosUserProfile(): IosUserProfile {
    val locationMap = this["location"].asStringMap()
    val latitude = locationMap?.get("latitude").doubleValue().takeIf { it != 0.0 }
        ?: this["latitude"].doubleValue()
    val longitude = locationMap?.get("longitude").doubleValue().takeIf { it != 0.0 }
        ?: this["longitude"].doubleValue()
    return IosUserProfile(
        uid = this["uid"].stringValue(),
        email = this["email"].stringValue(),
        name = this["name"].stringValue(),
        phoneNumber = this["phoneNumber"].stringValue(),
        year = this["year"].stringValue(),
        semester = this["semester"].stringValue(),
        course = this["course"].stringValue(),
        dob = this["dob"].stringValue(),
        profilePicUrl = this["profilePicUrl"].stringValue(),
        instagramLink = this["instagramLink"].stringValue(),
        snapchatLink = this["snapchatLink"].stringValue(),
        linkedinLink = this["linkedinLink"].stringValue(),
        lastUpdated = this["lastUpdated"].longValue(),
        location = if (latitude != 0.0 || longitude != 0.0) GeoPoint(latitude, longitude) else null,
        ghostMode = this["ghostMode"].booleanValue(false)
    )
}

internal fun Map<String, Any?>.toIosUserProfile(fallbackUid: String): IosUserProfile {
    return toIosUserProfile().copy(uid = this["uid"].stringValue().ifBlank { fallbackUid })
}

internal fun Map<String, Any?>.toSupabaseUserProfile(): IosUserProfile {
    val latitude = this["latitude"].doubleValue()
    val longitude = this["longitude"].doubleValue()
    return IosUserProfile(
        uid = this["id"].stringValue(), email = this["email"].stringValue(), name = this["name"].stringValue(),
        phoneNumber = this["phone_number"].stringValue(), year = this["year"].stringValue(),
        semester = this["semester"].stringValue(), course = this["course"].stringValue(), dob = this["dob"].stringValue(),
        profilePicUrl = this["profile_pic_url"].stringValue(), instagramLink = this["instagram_link"].stringValue(),
        snapchatLink = this["snapchat_link"].stringValue(), linkedinLink = this["linkedin_link"].stringValue(),
        lastUpdated = this["last_updated"].longValue(), location = if (latitude != 0.0 || longitude != 0.0) GeoPoint(latitude, longitude) else null,
        ghostMode = this["ghost_mode"].booleanValue(false)
    )
}

internal fun Place.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "latitude" to latitude,
    "longitude" to longitude,
    "category" to category.name
)

internal fun Map<String, Any?>.toPlace(): Place {
    val categoryName = this["category"].stringValue()
    val category = runCatching { PlaceCategory.valueOf(categoryName) }.getOrElse { PlaceCategory.OTHER }
    return Place(
        name = this["name"].stringValue(),
        latitude = this["latitude"].doubleValue(),
        longitude = this["longitude"].doubleValue(),
        category = category
    )
}

internal fun Route.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "points" to points.map { point ->
        mapOf(
            "lat" to point.lat,
            "lng" to point.lng,
            "instruction" to point.instruction
        )
    }
)

internal fun Map<String, Any?>.toRoute(): Route {
    val points = this["points"].asList()
        ?.mapNotNull { raw ->
            raw.asStringMap()?.let { point ->
                GeoPoint(
                    lat = point["lat"].doubleValue(),
                    lng = point["lng"].doubleValue(),
                    instruction = point["instruction"].stringValue().takeIf { it.isNotBlank() }
                )
            }
        }
        ?: emptyList()
    return Route(
        id = this["id"].stringValue(),
        name = this["name"].stringValue(),
        points = points
    )
}


fun iosHasActiveSession(): Boolean = IosSessionStore.hasActiveSession()

fun iosCurrentSessionEmail(): String = IosSessionStore.current()?.email ?: ""

fun iosCurrentSessionUid(): String = IosSessionStore.current()?.uid ?: ""

fun iosCurrentSessionAccessToken(): String =
    SupabaseClientProvider.client.auth.currentAccessTokenOrNull()
        ?: IosSessionStore.current()?.idToken.orEmpty()

private fun String.firebaseFriendlyMessage(): String {
    return when (trim()) {
        "INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND" -> "Incorrect email or password"
        "USER_DISABLED" -> "This account has been disabled"
        "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Too many attempts. Please try again later"
        "INVALID_EMAIL" -> "Please enter a valid email"
        "WEAK_PASSWORD : Password should be at least 6 characters",
        "WEAK_PASSWORD" -> "Password must be at least 6 characters"
        "EMAIL_EXISTS" -> "This email is already registered"
        else -> replace('_', ' ').lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
