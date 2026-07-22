package com.example.attendance.worker

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.attendance.data.AttendanceDatabase
import com.example.attendance.data.AttendanceRecord
import com.example.attendance.data.ClassSchedule
import com.example.attendance.data.TimetableEntry
import com.example.attendance.notifications.NotificationHelper
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class AttendanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("AttendanceWorker", "Worker triggered at ${System.currentTimeMillis()}")
        val database = AttendanceDatabase.getDatabase(applicationContext)
        val dao = database.attendanceDao()

        val calendar = Calendar.getInstance()
        val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val targetedId = inputData.getLong("timetableId", -1L)
        val targetedScheduleId = inputData.getLong("scheduleId", -1L)
        
        val holiday = dao.getHolidayForDate(currentDate)
        if (holiday != null && holiday.isConfirmed) {
            Log.d("AttendanceWorker", "Holiday today: ${holiday.name}. Skipping.")
            return Result.success()
        }

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val nowTime = sdf.parse(currentTime) ?: return Result.success()

        val allTimetable = dao.getAllTimetableEntriesOnce()
        val matchingSlots = mutableListOf<Pair<TimetableEntry, ClassSchedule>>()

        if (targetedId != -1L) {
            Log.d("AttendanceWorker", "Running targeted check for ID: $targetedId, Schedule: $targetedScheduleId")
            val entry = allTimetable.find { it.entry.id == targetedId }
            entry?.let { (t, schedules) ->
                val schedule = if (targetedScheduleId != -1L) {
                    schedules.find { it.scheduleId == targetedScheduleId }
                } else {
                    // Fallback for manual check from UI if targetedId given but not schedule
                    schedules.find { it.dayOfWeek == dayOfWeek }
                }
                schedule?.let { matchingSlots.add(t to it) }
            }
        } else {
            // Manual: Check all current slots across all subjects
            Log.d("AttendanceWorker", "Running global manual check")
            allTimetable.forEach { (t, schedules) ->
                schedules.filter { it.dayOfWeek == dayOfWeek }.forEach { s ->
                    val startTime = sdf.parse(s.startTime) ?: return@forEach
                    val endTime = sdf.parse(s.endTime) ?: return@forEach
                    
                    val isInHours = nowTime.after(startTime) && nowTime.before(endTime) || nowTime == startTime
                    if (isInHours) {
                        val thresholdCal = Calendar.getInstance().apply {
                            time = startTime
                            add(Calendar.MINUTE, t.attendanceThresholdMinutes)
                        }
                        if (nowTime.before(thresholdCal.time) || nowTime == thresholdCal.time) {
                            matchingSlots.add(t to s)
                        }
                    }
                }
            }
        }

        if (matchingSlots.isEmpty()) {
            Log.d("AttendanceWorker", "No classes found for the current time window.")
            return Result.success()
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        try {
            var location: Location? = fusedLocationClient.lastLocation.await()
            if (location == null) {
                Log.d("AttendanceWorker", "Last location null, requesting fresh location...")
                location = fusedLocationClient.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null
                ).await()
            }
            if (location == null) {
                Log.w("AttendanceWorker", "Location unavailable. Cannot mark attendance.")
                return Result.success() 
            }
            
            val notificationHelper = NotificationHelper(applicationContext)
            
            matchingSlots.forEach { (t, s) ->
                // Check if THIS SPECIFIC SESSION is already marked
                val existing = dao.getAttendanceForScheduleAndDate(t.id, s.scheduleId, currentDate)
                if (existing != null) {
                    Log.d("AttendanceWorker", "Session ${s.scheduleId} already marked for today.")
                    return@forEach
                }

                val results = FloatArray(1)
                Location.distanceBetween(location.latitude, location.longitude, t.latitude, t.longitude, results)
                val distance = results[0]

                if (distance <= t.radiusInMeters) {
                    val id = dao.insertAttendanceRecord(AttendanceRecord(timetableId = t.id, scheduleId = s.scheduleId, date = currentDate, status = "PRESENT"))
                    notificationHelper.sendAttendanceMarkedNotification(t.subjectName, "PRESENT", id)
                    Log.d("AttendanceWorker", "SUCCESS: Marked PRESENT for ${t.subjectName}")
                } else if (targetedId != -1L) {
                    // Only mark ABSENT for precision checks, not for random manual button clicks
                    val id = dao.insertAttendanceRecord(AttendanceRecord(timetableId = t.id, scheduleId = s.scheduleId, date = currentDate, status = "ABSENT"))
                    notificationHelper.sendAttendanceMarkedNotification(t.subjectName, "ABSENT", id)
                    Log.d("AttendanceWorker", "FAILURE: Marked ABSENT for ${t.subjectName}")
                }
            }
        } catch (e: SecurityException) {
            Log.e("AttendanceWorker", "Permission error", e)
            return Result.failure()
        }

        return Result.success()
    }
}
