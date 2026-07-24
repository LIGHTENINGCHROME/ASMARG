package com.example.attendance.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import com.example.attendance.data.AttendanceDao
import com.example.attendance.worker.AttendanceWorker
import java.util.*
import java.util.concurrent.TimeUnit

class AttendanceScheduler(private val context: Context) {

    suspend fun scheduleAllChecks(dao: AttendanceDao) {
        val workManager = WorkManager.getInstance(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        workManager.cancelAllWorkByTag("precision_check")

        val entries = dao.getAllTimetableEntriesOnce()
        val now = Calendar.getInstance()

        entries.forEach { (entry, schedules) ->
            schedules.forEach { schedule ->
                val nextOccurrence = getNextOccurrence(schedule.dayOfWeek, schedule.startTime, entry.attendanceThresholdMinutes)
                val delay = nextOccurrence.timeInMillis - now.timeInMillis
                
                if (delay > 0) {
                    // WorkManager fallback
                    val workRequest = OneTimeWorkRequestBuilder<AttendanceWorker>()
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .setInputData(workDataOf(
                            "timetableId" to entry.id,
                            "scheduleId" to schedule.scheduleId
                        ))
                        .addTag("precision_check")
                        .build()

                    workManager.enqueueUniqueWork(
                        "attendance_check_${entry.id}_${schedule.scheduleId}",
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                    )

                    // AlarmManager for guaranteed wake-up
                    val intent = Intent(context, PrecisionAlarmReceiver::class.java).apply {
                        putExtra("timetableId", entry.id)
                        putExtra("scheduleId", schedule.scheduleId)
                    }
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        "${entry.id}${schedule.scheduleId}".hashCode(),
                        intent,
                        flags
                    )

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (alarmManager.canScheduleExactAlarms()) {
                                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextOccurrence.timeInMillis, pendingIntent)
                            } else {
                                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextOccurrence.timeInMillis, pendingIntent)
                            }
                        } else {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextOccurrence.timeInMillis, pendingIntent)
                        }
                    } catch (e: SecurityException) {
                        Log.e("AttendanceScheduler", "Exact alarm permission missing", e)
                    }
                }
            }
        }
        scheduleDailyMaintenance()
    }

    private fun scheduleDailyMaintenance() {
        val workManager = WorkManager.getInstance(context)
        val now = Calendar.getInstance()
        val maintenanceTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        
        val delay = maintenanceTime.timeInMillis - now.timeInMillis
        val maintenanceRequest = PeriodicWorkRequestBuilder<DailyScheduleWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("daily_maintenance")
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()

        workManager.enqueueUniquePeriodicWork("daily_attendance_refresh", ExistingPeriodicWorkPolicy.KEEP, maintenanceRequest)
    }

    private fun getNextOccurrence(dayOfWeek: Int, startTimeStr: String, threshold: Int): Calendar {
        val calendar = Calendar.getInstance()
        val parts = startTimeStr.split(":")
        calendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
        calendar.set(Calendar.MINUTE, parts[1].toInt())
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.MINUTE, threshold)

        val currentDay = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3;
            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6; Calendar.SUNDAY -> 7; else -> 1
        }
        var daysDiff = dayOfWeek - currentDay
        if (daysDiff < 0 || (daysDiff == 0 && calendar.before(Calendar.getInstance()))) daysDiff += 7
        calendar.add(Calendar.DAY_OF_YEAR, daysDiff)
        return calendar
    }
}
