package com.example.attendance.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.attendance.data.AttendanceDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val scheduler = AttendanceScheduler(context)
            val dao = AttendanceDatabase.getDatabase(context).attendanceDao()
            CoroutineScope(Dispatchers.IO).launch {
                scheduler.scheduleAllChecks(dao)
            }
        }
    }
}
