package com.example.attendance.scheduler

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.attendance.data.AttendanceDao
import com.example.attendance.worker.AttendanceWorker
import java.util.*
import java.util.concurrent.TimeUnit

class AttendanceScheduler(private val context: Context) {

    suspend fun scheduleAllChecks(dao: AttendanceDao) {
        val workManager = WorkManager.getInstance(context)
        
        // Cancel all existing scheduled checks to avoid overlaps
        workManager.cancelAllWorkByTag("precision_check")

        val entries = dao.getAllTimetableEntriesOnce()
        val now = Calendar.getInstance()

        Log.d("AttendanceScheduler", "Scheduling checks for ${entries.size} subjects")

        entries.forEach { (entry, schedules) ->
            schedules.forEach { schedule ->
                val nextOccurrence = getNextOccurrence(schedule.dayOfWeek, schedule.startTime, entry.attendanceThresholdMinutes)
                val delay = nextOccurrence.timeInMillis - now.timeInMillis
                
                if (delay > 0) {
                    val workRequest = OneTimeWorkRequestBuilder<AttendanceWorker>()
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .setInputData(workDataOf(
                            "timetableId" to entry.id,
                            "scheduleId" to schedule.scheduleId
                        ))
                        .addTag("precision_check")
                        .addTag("targeted_check_${entry.id}")
                        .build()

                    workManager.enqueueUniqueWork(
                        "attendance_check_${entry.id}_${schedule.scheduleId}",
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                    )
                    Log.d("AttendanceScheduler", "Enqueued check for ${entry.subjectName} in ${delay/1000/60} mins")
                }
            }
        }
    }

    private fun getNextOccurrence(dayOfWeek: Int, startTimeStr: String, threshold: Int): Calendar {
        val calendar = Calendar.getInstance()
        val parts = startTimeStr.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.MINUTE, threshold)

        val currentDay = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        var daysDiff = dayOfWeek - currentDay
        // If it's today but the time has passed, move to next week
        if (daysDiff < 0 || (daysDiff == 0 && calendar.before(Calendar.getInstance()))) {
            daysDiff += 7
        }

        calendar.add(Calendar.DAY_OF_YEAR, daysDiff)
        return calendar
    }
}
