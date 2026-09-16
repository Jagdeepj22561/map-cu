package com.example.maps123.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.maps123.MainActivity
import com.example.maps123.data.firebase.FcmTokenSyncManager
import com.example.maps123.data.repository.AnnouncementRepository
import com.example.maps123.ui.CampusUpdateBus
import com.example.maps123.ui.CampusUpdateEvent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        if (data["kind"] == "custom_chat") {
            // Ignore a targeted message if Android is currently signed in as
            // a different account. Older chat payloads may omit this field.
            if (!isCurrentRecipient(data["recipientId"])) return
            data["deep_link_chat_id"]?.let {
                showChatNotification(it, "New message", "Open Campus Map to read your message")
                CampusUpdateBus.publish(
                    CampusUpdateEvent("chat:$it", "New chat message")
                )
            }
            return
        }
        if (data["kind"] == "post_created") {
            val postId = data["deep_link_post_id"] ?: return
            refreshAnnouncementCache()
            CampusUpdateBus.publish(
                CampusUpdateEvent(
                    key = "post:$postId",
                    message = notificationTitle(remoteMessage, "New campus post"),
                    postId = postId
                )
            )
            showCampusNotification(
                notificationId = "$postId:post_created".hashCode(),
                postId = postId,
                title = notificationTitle(remoteMessage, "New campus post"),
                body = notificationBody(remoteMessage, "Open Campus Map to view it")
            )
            return
        }
        if (data["kind"] in setOf(
                "team_join_requested",
                "team_join_approved",
                "team_join_rejected",
                "team_member_joined",
                "team_created",
                "team_requirement_created",
                "team_requirement_interest"
            )
        ) {
            if (!isCurrentRecipient(data["recipientId"])) return
            val postId = data["deep_link_post_id"] ?: return
            CampusUpdateBus.publish(
                CampusUpdateEvent(
                    key = "${data["kind"]}:$postId",
                    message = notificationTitle(remoteMessage, "Campus Map update"),
                    postId = postId
                )
            )
            showCampusNotification(
                notificationId = "$postId:${data["kind"]}".hashCode(),
                postId = postId,
                title = notificationTitle(remoteMessage, "Campus Map update"),
                body = notificationBody(remoteMessage, "Open Campus Map to view the update")
            )
            return
        }
        if (data["kind"] in setOf(
                "friend_request_created",
                "friend_request_accepted",
                "friend_request_rejected"
            )
        ) {
            if (!isCurrentRecipient(data["recipientId"])) return
            CampusUpdateBus.publish(
                CampusUpdateEvent(
                    key = "${data["kind"]}:${data["requestId"]}",
                    message = notificationTitle(remoteMessage, "Friend update")
                )
            )
            showCampusNotification(
                notificationId = "friends:${data["kind"]}:${data["requestId"]}".hashCode(),
                openFriends = true,
                title = notificationTitle(remoteMessage, "Friend update"),
                body = notificationBody(remoteMessage, "Open Campus Map to view it")
            )
            return
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenSyncManager.syncTokenIfChanged(applicationContext, token)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /** Refresh Room while Firebase keeps the service process alive. */
    private fun refreshAnnouncementCache() {
        serviceScope.launch {
            runCatching { AnnouncementRepository(applicationContext).refreshNow() }
        }
    }

    private fun notificationTitle(message: RemoteMessage, fallback: String) =
        message.notification?.title ?: message.data["notification_title"] ?: fallback

    private fun notificationBody(message: RemoteMessage, fallback: String) =
        message.notification?.body ?: message.data["notification_body"] ?: fallback

    /** A killed process must restore Supabase Auth before checking a targeted FCM payload. */
    private fun isCurrentRecipient(recipientId: String?): Boolean {
        if (recipientId.isNullOrBlank()) return true
        val currentUid = runBlocking {
            withTimeoutOrNull(2_500L) {
                com.example.maps123.data.repository.AuthRepository.awaitCurrentUserId()
            }
        }
        return currentUid == recipientId
    }

    private fun showChatNotification(chatId: String, title: String, body: String) {
        val channelId = "chat_notifications"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New message notifications"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("deep_link_chat_id", chatId)
        }

        val requestCode = chatId.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup("chat_$chatId")
            .build()

        val notificationId = chatId.hashCode()
        notificationManager.notify(notificationId, notification)
    }

    private fun showCampusNotification(
        notificationId: Int,
        postId: String? = null,
        openFriends: Boolean = false,
        title: String,
        body: String
    ) {
        val channelId = "campus_updates"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Campus updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Posts and team activity"
                    enableVibration(true)
                }
            )
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            postId?.let { putExtra("deep_link_post_id", it) }
            if (openFriends) putExtra("deep_link_friends", "true")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        notificationManager.notify(
            notificationId,
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .build()
        )
    }

}
