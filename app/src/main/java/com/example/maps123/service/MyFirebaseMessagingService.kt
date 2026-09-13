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
import com.example.maps123.data.local.AppDatabase
import com.example.maps123.data.local.MessageEntity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val chatId = data["chatId"]
        val senderId = data["senderId"]
        val content = data["content"]
        val type = data["type"] ?: "TEXT"
        val imageUrl = data["imageUrl"]
        val senderName = data["senderName"]

        if (chatId != null && senderId != null && content != null) {
            insertMessageLocally(chatId, senderId, content, type, imageUrl)
            showChatNotification(
                chatId = chatId,
                title = senderName ?: "New Message",
                body = if (type == "IMAGE" && content.isBlank()) "Sent an image" else content
            )
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

    private fun insertMessageLocally(
        chatId: String,
        senderId: String,
        content: String,
        type: String,
        imageUrl: String?
    ) {
        val db = AppDatabase.getInstance(applicationContext)
        val chatDao = db.chatDao()

        CoroutineScope(Dispatchers.IO).launch {
            chatDao.insertMessage(
                MessageEntity(
                    messageId = UUID.randomUUID().toString(),
                    chatId = chatId,
                    senderId = senderId,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    isSynced = true,
                    imageUrl = imageUrl,
                    type = type
                )
            )
            chatDao.incrementUnreadCount(chatId)
        }
    }
}
