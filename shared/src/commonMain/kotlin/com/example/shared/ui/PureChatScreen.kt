package com.example.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.example.shared.model.PureChat
import com.example.shared.repository.IChatRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureChatScreen(
    repository: IChatRepository,
    onChatClick: (PureChat) -> Unit,
    onAddFriendClick: () -> Unit,
    onNearbyClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onChatLongClick: (PureChat) -> Unit,
    searchQuery: String = "",
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit
) {
    val chats by repository.allChats.collectAsState(emptyList())

    val filteredChats = remember(chats, searchQuery) {
        if (searchQuery.isBlank()) chats
        else chats.filter {
            it.friendName.contains(searchQuery, true) ||
                    it.lastMessage.contains(searchQuery, true)
        }
    }

    Box(Modifier.fillMaxSize()) {

        Column(Modifier.fillMaxSize()) {

            TabRow(
                selectedTabIndex = 0,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions.first()),
                        height = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = true,
                    onClick = {},
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(
                        "Chats",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (filteredChats.isEmpty()) EmptyState("No chats yet")
            else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
            ) {
                items(filteredChats, key = { it.chatId }) { chat ->
                    PureChatItem(
                        chat = chat,
                        onClick = { onChatClick(chat) },
                        onLongClick = { onChatLongClick(chat) },
                        renderImage = renderImage
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }

        FabStack(
            onNearbyClick,
            onAddFriendClick,
            onNewChatClick,
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = 20.dp, end = 20.dp)
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(text)
    }
}

@Composable
private fun FabStack(
    onNearbyClick: () -> Unit,
    onAddFriendClick: () -> Unit,
    onNewChatClick: () -> Unit,
    modifier: Modifier
) {
    val containerColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onPrimary

    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End
    ) {
        SmallFloatingActionButton(
            onClick = onNearbyClick,
            containerColor = containerColor,
            contentColor = contentColor
        ) {
            Icon(Icons.Default.LocationOn, null)
        }

        SmallFloatingActionButton(
            onClick = onAddFriendClick,
            containerColor = containerColor,
            contentColor = contentColor
        ) {
            Icon(Icons.Default.PersonAdd, null)
        }

        FloatingActionButton(
            onClick = onNewChatClick,
            containerColor = containerColor,
            contentColor = contentColor
        ) {
            Icon(Icons.Default.Edit, null)
        }
    }
}
