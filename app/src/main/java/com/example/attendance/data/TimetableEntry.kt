package com.example.attendance.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "timetable_entries",
    indices = [Index(value = ["subjectName"])]
)
data class TimetableEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectName: String,
    val subjectFullName: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusInMeters: Float = 100f,
    val attendanceThresholdMinutes: Int = 15
)

@Entity(
    tableName = "class_schedules",
    indices = [Index(value = ["timetableEntryId"]), Index(value = ["dayOfWeek"])]
)
data class ClassSchedule(
    @PrimaryKey(autoGenerate = true) val scheduleId: Long = 0,
    val timetableEntryId: Long,
    val dayOfWeek: Int, // 1-7
    val startTime: String, // "HH:mm"
    val endTime: String
)
