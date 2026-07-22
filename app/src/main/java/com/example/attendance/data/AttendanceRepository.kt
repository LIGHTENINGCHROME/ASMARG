package com.example.attendance.data

import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class AttendanceRepository(private val attendanceDao: AttendanceDao) {

    val allTimetableEntries: Flow<List<TimetableWithSchedules>> = attendanceDao.getAllTimetableEntries()
    val allHolidays: Flow<List<Holiday>> = attendanceDao.getAllHolidays()
    val allAttendanceRecords: Flow<List<AttendanceRecord>> = attendanceDao.getAllAttendanceRecords()

    suspend fun insertTimetableWithSchedules(entry: TimetableEntry, schedules: List<ClassSchedule>) {
        val normalizedName = entry.subjectName.uppercase().trim()
        
        val targetId = if (entry.id > 0) {
            entry.id
        } else {
            attendanceDao.getEntryByName(normalizedName)?.id
        }
        
        val finalEntry = if (targetId != null) {
            entry.copy(id = targetId, subjectName = normalizedName)
        } else {
            entry.copy(subjectName = normalizedName)
        }

        val entryId = attendanceDao.insertTimetableEntry(finalEntry)
        
        attendanceDao.deleteSchedulesForEntry(entryId)
        schedules.forEach { 
            attendanceDao.insertSchedule(it.copy(timetableEntryId = entryId, scheduleId = 0))
        }
    }

    suspend fun deleteTimetableEntry(entry: TimetableEntry) {
        attendanceDao.deleteSchedulesForEntry(entry.id)
        attendanceDao.deleteTimetableEntry(entry)
    }

    suspend fun insertHoliday(holiday: Holiday) {
        val existing = attendanceDao.getHolidayByDateAndName(holiday.date, holiday.name)
        if (existing == null) {
            attendanceDao.insertHoliday(holiday)
        }
    }

    suspend fun deleteHoliday(holiday: Holiday) {
        attendanceDao.deleteHoliday(holiday)
    }

    suspend fun updateHoliday(holiday: Holiday) {
        attendanceDao.updateHoliday(holiday)
    }

    suspend fun syncWeekendHolidays(saturday: Boolean, sunday: Boolean) {
        attendanceDao.clearWeekendHolidays()
        if (!saturday && !sunday) return

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        calendar.set(currentYear, 0, 1)
        while (calendar.get(Calendar.YEAR) == currentYear) {
            val day = calendar.get(Calendar.DAY_OF_WEEK)
            if ((saturday && day == Calendar.SATURDAY) || (sunday && day == Calendar.SUNDAY)) {
                attendanceDao.insertHoliday(
                    Holiday(
                        date = sdf.format(calendar.time),
                        name = if (day == Calendar.SATURDAY) "Saturday" else "Sunday",
                        isWeekend = true,
                        isConfirmed = true
                    )
                )
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>> {
        return attendanceDao.getAttendanceForDate(date)
    }

    suspend fun insertAttendanceRecord(record: AttendanceRecord): Long {
        return attendanceDao.insertAttendanceRecord(record)
    }

    suspend fun deleteAttendanceRecord(record: AttendanceRecord) {
        attendanceDao.deleteAttendanceRecord(record)
    }
}
