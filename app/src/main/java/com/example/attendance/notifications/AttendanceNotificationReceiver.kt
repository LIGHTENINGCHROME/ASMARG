package com.example.attendance.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.attendance.data.AttendanceDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AttendanceNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recordId = intent.getLongOfExtra("recordId", -1L)
        if (recordId == -1L) return

        if (intent.action == "ACTION_MARK_SUSPENDED") {
            CoroutineScope(Dispatchers.IO).launch {
                val database = AttendanceDatabase.getDatabase(context)
                val dao = database.attendanceDao()
                
                // Find and update the record
                // We don't have a getById in DAO, but we can update by creating a copy if we had the data
                // For simplicity, let's assume we can fetch all and find it
                val allRecords = dao.getAllAttendanceRecordsOnce()
                val record = allRecords.find { it.id == recordId }
                
                record?.let {
                    dao.insertAttendanceRecord(it.copy(status = "SUSPENDED"))
                    
                    // Dismiss the notification
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(recordId.toInt())
                }
            }
        }
    }

    private fun Intent.getLongOfExtra(name: String, defaultValue: Long): Long {
        return if (hasExtra(name)) getLongExtra(name, defaultValue) else defaultValue
    }
}
