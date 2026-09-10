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

        if (chatId != null && senderId != null && content != null) {

            insertMessageLocally(chatId, senderId, content, type, imageUrl)

            showNotification(
                data["title"] ?: "New Message",
                content
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        updateTokenInFirebase(token)
    }

    private fun updateTokenInFirebase(token: String) {
        FcmTokenSyncManager.syncTokenIfChanged(applicationContext, token)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "chat_notifications"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
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
