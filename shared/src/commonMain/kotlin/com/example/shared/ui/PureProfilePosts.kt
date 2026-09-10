package com.example.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shared.model.Announcement

@Composable
fun PureAvatarPlaceholder(
    modifier: Modifier
) {
    Box(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(100.dp)
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PureProfilePosts(
    posts: List<Announcement>,
    currentUid: String?,
    formatTime: (Long) -> String,
    renderImage: @Composable (String?, Modifier, ContentScale) -> Unit,
    onPostClick: (Announcement) -> Unit = {},
    onReport: (Announcement) -> Unit = {},
    onShare: (Announcement) -> Unit = {},
    onDelete: (Announcement) -> Unit = {}
) {
    if (posts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No posts yet")
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        posts.forEach { post ->
            PureAnnouncementCard(
                announcement = post,
                currentUid = currentUid,
                onReport = { onReport(post) },
                onShare = { onShare(post) },
                onDelete = { onDelete(post) },
                onClick = { onPostClick(post) },
                formatTime = formatTime,
                renderImage = renderImage
            )
        }
    }
}
