package com.example.attendance.data.backup

import com.example.attendance.data.AttendanceRecord
import com.example.attendance.data.ClassSchedule
import com.example.attendance.data.Holiday
import com.example.attendance.data.TimetableEntry

data class BackupData(
    val timetableEntries: List<TimetableEntry>? = null,
    val classSchedules: List<ClassSchedule>? = null,
    val attendanceRecords: List<AttendanceRecord>? = null,
    val holidays: List<Holiday>? = null,
    val backupTimestamp: Long = System.currentTimeMillis()
)
