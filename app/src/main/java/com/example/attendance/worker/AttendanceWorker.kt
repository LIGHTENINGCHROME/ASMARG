package com.example.attendance.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.attendance.data.AttendanceDatabase
import com.example.attendance.data.AttendanceRecord
import com.example.attendance.data.ClassSchedule
import com.example.attendance.data.TimetableEntry
import com.example.attendance.notifications.NotificationHelper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class AttendanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(100, createForegroundNotification())
    }

    private fun createForegroundNotification(): Notification {
        val channelId = "attendance_check_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Attendance Check", NotificationManager.IMPORTANCE_LOW)
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Verifying Attendance...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override suspend fun doWork(): Result {
        val isBackground = inputData.getBoolean("isBackgroundTrigger", false)
        Log.d("AttendanceWorker", "Starting Check. Background: $isBackground")
        
        // If background, promote to foreground immediately to prevent being killed
        if (isBackground) {
            setForeground(getForegroundInfo())
        }

        val database = AttendanceDatabase.getDatabase(applicationContext)
        val dao = database.attendanceDao()

        val calendar = Calendar.getInstance()
        val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3;
            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6; Calendar.SUNDAY -> 7; else -> 1
        }
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val targetedId = inputData.getLong("timetableId", -1L)
        val targetedScheduleId = inputData.getLong("scheduleId", -1L)
        
        val holiday = dao.getHolidayForDate(currentDate)
        if (holiday != null && holiday.isConfirmed) {
            Log.d("AttendanceWorker", "Holiday: ${holiday.name}. Skipping.")
            return Result.success()
        }

        val allTimetable = dao.getAllTimetableEntriesOnce()
        val matchingSlots = mutableListOf<Pair<TimetableEntry, ClassSchedule>>()

        if (targetedId != -1L) {
            val entry = allTimetable.find { it.entry.id == targetedId }
            entry?.let { (t, schedules) ->
                val schedule = if (targetedScheduleId != -1L) {
                    schedules.find { it.scheduleId == targetedScheduleId }
                } else {
                    schedules.find { it.dayOfWeek == dayOfWeek }
                }
                schedule?.let { matchingSlots.add(t to it) }
            }
        } else {
            // Manual/Global fallback
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val nowTime = sdf.parse(currentTime) ?: return Result.success()
            
            allTimetable.forEach { (t, schedules) ->
                schedules.filter { it.dayOfWeek == dayOfWeek }.forEach { s ->
                    val startTime = sdf.parse(s.startTime) ?: return@forEach
                    val endTime = sdf.parse(s.endTime) ?: return@forEach
                    if (nowTime.after(startTime) && nowTime.before(endTime) || nowTime == startTime) {
                        matchingSlots.add(t to s)
                    }
                }
            }
        }

        if (matchingSlots.isEmpty()) return Result.success()

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        try {
            // Priority: High Accuracy forced for autonomous marking
            val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            if (location == null) {
                Log.w("AttendanceWorker", "GPS unavailable. Cannot verify.")
                return Result.retry() 
            }
            
            val notificationHelper = NotificationHelper(applicationContext)
            
            matchingSlots.forEach { (t, s) ->
                val existing = dao.getAttendanceForScheduleAndDate(t.id, s.scheduleId, currentDate)
                if (existing != null) return@forEach

                val results = FloatArray(1)
                Location.distanceBetween(location.latitude, location.longitude, t.latitude, t.longitude, results)
                val distance = results[0]

                if (distance <= t.radiusInMeters) {
                    val id = dao.insertAttendanceRecord(AttendanceRecord(timetableId = t.id, scheduleId = s.scheduleId, date = currentDate, status = "PRESENT"))
                    notificationHelper.sendAttendanceMarkedNotification(t.subjectName, "PRESENT", id)
                    Log.d("AttendanceWorker", "Marked PRESENT for ${t.subjectName}")
                } else if (targetedId != -1L) {
                    val id = dao.insertAttendanceRecord(AttendanceRecord(timetableId = t.id, scheduleId = s.scheduleId, date = currentDate, status = "ABSENT"))
                    notificationHelper.sendAttendanceMarkedNotification(t.subjectName, "ABSENT", id)
                    Log.d("AttendanceWorker", "Marked ABSENT for ${t.subjectName}")
                }
            }
        } catch (e: SecurityException) {
            Log.e("AttendanceWorker", "Permission missing", e)
            return Result.failure()
        }

        return Result.success()
    }
}
