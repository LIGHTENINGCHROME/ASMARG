package com.example.attendance.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.attendance.data.AttendanceDatabase
import com.example.attendance.service.TrackingService
import java.text.SimpleDateFormat
import java.util.*

class DailyScheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AttendanceDatabase.getDatabase(applicationContext)
        val dao = database.attendanceDao()
        val allTimetable = dao.getAllTimetableEntriesOnce()
        
        val calendar = Calendar.getInstance()
        val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3;
            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6; Calendar.SUNDAY -> 7; else -> 1
        }

        val todayClasses = allTimetable.flatMap { it.schedules }.filter { it.dayOfWeek == dayOfWeek }
        
        if (todayClasses.isNotEmpty()) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            
            // To solve the "Gap" issue, we'll keep the service alive from 
            // 10 mins before the VERY FIRST class until the VERY LAST class ends.
            val sortedStart = todayClasses.map { sdf.parse(it.startTime)!! }.sorted()
            val sortedEnd = todayClasses.map { sdf.parse(it.endTime)!! }.sorted()
            
            val firstClassTime = sortedStart.first()
            val lastClassTime = sortedEnd.last()

            val startCal = Calendar.getInstance().apply {
                time = firstClassTime
                val now = Calendar.getInstance()
                set(Calendar.YEAR, now.get(Calendar.YEAR))
                set(Calendar.DAY_OF_YEAR, now.get(Calendar.DAY_OF_YEAR))
                add(Calendar.MINUTE, -10)
            }
            
            val stopCal = Calendar.getInstance().apply {
                time = lastClassTime
                val now = Calendar.getInstance()
                set(Calendar.YEAR, now.get(Calendar.YEAR))
                set(Calendar.DAY_OF_YEAR, now.get(Calendar.DAY_OF_YEAR))
                add(Calendar.MINUTE, 5) // Stop 5 mins after last class
            }

            val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // Start Intent
            val startIntent = Intent(applicationContext, TrackingService::class.java)
            val startPending = PendingIntent.getService(applicationContext, 101, startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            
            // Stop Intent
            val stopIntent = Intent(applicationContext, TrackingService::class.java).apply { action = "STOP_SERVICE" }
            val stopPending = PendingIntent.getService(applicationContext, 102, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            try {
                val now = Calendar.getInstance()
                
                // 1. Handle START
                if (now.after(startCal) && now.before(stopCal)) {
                    // We are currently in the class window, start immediately
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        applicationContext.startForegroundService(startIntent)
                    } else {
                        applicationContext.startService(startIntent)
                    }
                } else if (now.before(startCal)) {
                    // Schedule for later today
                    scheduleAlarm(alarmManager, startCal.timeInMillis, startPending)
                }

                // 2. Handle STOP (Always schedule if classes exist)
                scheduleAlarm(alarmManager, stopCal.timeInMillis, stopPending)
                
                Log.d("DailyWorker", "Service window: ${startCal.time} to ${stopCal.time}")

            } catch (e: SecurityException) {
                Log.e("DailyScheduleWorker", "Exact alarm permission missing", e)
            }
        } else {
            // No classes today - stop service if running
            val stopIntent = Intent(applicationContext, TrackingService::class.java).apply { action = "STOP_SERVICE" }
            applicationContext.startService(stopIntent)
        }

        return Result.success()
    }

    private fun scheduleAlarm(am: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }
}
