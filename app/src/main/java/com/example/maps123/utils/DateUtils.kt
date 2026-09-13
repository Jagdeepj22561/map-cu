package com.example.maps123.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours hr ago"
            days < 7 -> "$days days ago"
            else -> formatTime(timestamp)
        }
    }

    fun getDateHeader(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatMessageTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatChatTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val now = System.currentTimeMillis()
        val diff = (now - timestamp).coerceAtLeast(0L)

        // Sent within the last minute
        if (diff < 60_000L) {
            return "Just now"
        }

        val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }

        val isSameDay = msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                msgCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        if (isSameDay) {
            return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        }

        nowCal.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday = msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                msgCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) {
            return "Yesterday"
        }

        val daysDiff = diff / (24 * 60 * 60 * 1000L)
        if (daysDiff < 7) {
            return SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
        }

        return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
