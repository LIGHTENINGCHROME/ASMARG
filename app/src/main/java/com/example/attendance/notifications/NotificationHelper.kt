package com.example.attendance.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {
    private val channelId = "attendance_channel"
    private val channelName = "Attendance Notifications"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH // Use HIGH for better visibility
            ).apply {
                description = "Notifications for automatic attendance marking"
                enableLights(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d("NotificationHelper", "Notification channel created: $channelId")
        }
    }

    fun sendAttendanceMarkedNotification(subjectName: String, status: String, recordId: Long) {
        Log.d("NotificationHelper", "Preparing notification for $subjectName, Status: $status, ID: $recordId")
        
        val suspendIntent = Intent(context, AttendanceNotificationReceiver::class.java).apply {
            action = "ACTION_MARK_SUSPENDED"
            putExtra("recordId", recordId)
        }
        
        val suspendPendingIntent = PendingIntent.getBroadcast(
            context,
            recordId.toInt(),
            suspendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Using a more standard system icon
            .setContentTitle("Attendance Marked")
            .setContentText("You were marked $status for $subjectName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Mark Suspended", suspendPendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(recordId.toInt(), builder.build())
            Log.d("NotificationHelper", "Notification sent successfully for ID: $recordId")
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed to send notification", e)
        }
    }
}
