package com.example.attendance

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.attendance.data.AttendanceDatabase
import com.example.attendance.scheduler.AttendanceScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AttendanceApp : Application() {

    override fun onCreate() {
        super.onCreate()
        rescheduleAttendanceChecks()
    }

    private fun rescheduleAttendanceChecks() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AttendanceDatabase.getDatabase(this@AttendanceApp)
            val dao = database.attendanceDao()
            val scheduler = AttendanceScheduler(this@AttendanceApp)
            scheduler.scheduleAllChecks(dao)
        }
    }
}
