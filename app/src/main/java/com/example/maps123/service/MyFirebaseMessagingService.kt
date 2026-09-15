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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        if (data["kind"] == "custom_chat") {
            // Ignore a targeted message if Android is currently signed in as
            // a different account. Older chat payloads may omit this field.
            data["recipientId"]?.let { recipientId ->
                if (recipientId != com.example.maps123.data.repository.AuthRepository.currentUserId()) return
            }
            data["deep_link_chat_id"]?.let {
                showChatNotification(it, "New message", "Open Campus Map to read your message")
            }
            return
        }
        if (data["kind"] == "post_created") {
            val postId = data["deep_link_post_id"] ?: return
            showCampusNotification(
                notificationId = "$postId:post_created".hashCode(),
                postId = postId,
                title = remoteMessage.notification?.title ?: "New campus post",
                body = remoteMessage.notification?.body ?: "Open Campus Map to view it"
            )
            return
        }
        if (data["kind"] in setOf(
                "team_join_requested",
                "team_join_approved",
                "team_join_rejected",
                "team_member_joined"
            )
        ) {
            if (data["recipientId"] != com.example.maps123.data.repository.AuthRepository.currentUserId()) return
            val postId = data["deep_link_post_id"] ?: return
            showCampusNotification(
                notificationId = "$postId:${data["kind"]}".hashCode(),
                postId = postId,
                title = remoteMessage.notification?.title ?: "Campus Map update",
                body = remoteMessage.notification?.body ?: "Open Campus Map to view the update"
            )
            return
        }
        if (data["kind"] in setOf(
                "friend_request_created",
                "friend_request_accepted",
                "friend_request_rejected"
            )
        ) {
            if (data["recipientId"] != com.example.maps123.data.repository.AuthRepository.currentUserId()) return
            showCampusNotification(
                notificationId = "friends:${data["kind"]}:${data["requestId"]}".hashCode(),
                openFriends = true,
                title = remoteMessage.notification?.title ?: "Friend update",
                body = remoteMessage.notification?.body ?: "Open Campus Map to view it"
            )
            return
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenSyncManager.syncTokenIfChanged(applicationContext, token)
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
