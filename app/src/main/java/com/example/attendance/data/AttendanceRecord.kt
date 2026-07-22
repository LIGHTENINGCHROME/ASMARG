package com.example.attendance.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_records",
    indices = [Index(value = ["timetableId"]), Index(value = ["date"]), Index(value = ["scheduleId"])]
)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timetableId: Long,
    val scheduleId: Long = -1, // Links to specific time slot
    val date: String, // "yyyy-MM-dd"
    val status: String, // "PRESENT", "ABSENT", "HOLIDAY", "SUSPENDED"
    val timestamp: Long = System.currentTimeMillis()
)
