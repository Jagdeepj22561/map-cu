package com.example.maps123.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import com.example.maps123.data.supabase.SupabaseProvider
import com.example.maps123.ui.components.AppAsyncImage
import com.example.shared.ui.GlobalSearchScreen
import com.example.shared.ui.SearchResult
import com.example.shared.ui.SearchResultType
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class ProfileRow(
    val id: String,
    val name: String = "",
    val email: String = "",
    @SerialName("profile_pic_url") val profilePicUrl: String? = null,
    val course: String = "",
    val university: String = ""
)

@Serializable
private data class AnnouncementRow(
    val id: String,
    val title: String = "",
    val content: String = "",
    @SerialName("author_name") val authorName: String? = null
)

@Serializable
private data class GroupRow(
    val id: String,
    val name: String = "",
    val description: String = ""
)

@Composable
fun GlobalSearchNav(
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onPostClick: (String) -> Unit,
    onGroupClick: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(query) {
        searchJob?.cancel()
        if (query.length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        searchJob = scope.launch {
            delay(300)
            val client = SupabaseProvider.client
            val pattern = "%${query.trim()}%"

            val userResults = runCatching {
                client.from("profiles").select {
                    filter { ilike("name", pattern) }
                    limit(5)
                }.decodeList<ProfileRow>()
            }.getOrDefault(emptyList())

            val postResults = runCatching {
                client.from("announcements").select {
                    filter { ilike("title", pattern) }
                    order("created_at", Order.DESCENDING)
                    limit(5)
                }.decodeList<AnnouncementRow>()
            }.getOrDefault(emptyList())

            val groupResults = runCatching {
                client.from("groups").select {
                    filter { ilike("name", pattern) }
                    limit(5)
                }.decodeList<GroupRow>()
            }.getOrDefault(emptyList())

            results = buildList {
                userResults.forEach { p ->
                    add(SearchResult(
                        id = p.id,
                        type = SearchResultType.USER,
                        title = p.name.ifBlank { p.email },
                        subtitle = listOfNotNull(
                            p.course.takeIf { it.isNotBlank() },
                            p.university.takeIf { it.isNotBlank() }
                        ).joinToString(" • "),
                        imageUrl = p.profilePicUrl
                    ))
                }
                postResults.forEach { a ->
                    add(SearchResult(
                        id = a.id,
                        type = SearchResultType.POST,
                        title = a.title.ifBlank { "Untitled" },
                        subtitle = a.content.take(80) + if (a.content.length > 80) "..." else ""
                    ))
                }
                groupResults.forEach { g ->
                    add(SearchResult(
                        id = g.id,
                        type = SearchResultType.GROUP,
                        title = g.name,
                        subtitle = g.description.take(80) + if (g.description.length > 80) "..." else ""
                    ))
                }
            }
            searching = false
        }
    }

    GlobalSearchScreen(
        query = query,
        onQueryChange = { query = it },
        results = results,
        isSearching = searching,
        onResultClick = { result ->
            when (result.type) {
                SearchResultType.USER -> onUserClick(result.id)
                SearchResultType.POST -> onPostClick(result.id)
                SearchResultType.GROUP -> onGroupClick(result.id)
            }
        },
        onBack = onBack,
        renderImage = { url, modifier, scale ->
            AppAsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = scale)
        }
    )
}
