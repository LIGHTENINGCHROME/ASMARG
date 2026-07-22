package com.example.attendance.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "holidays",
    indices = [Index(value = ["date"])]
)
data class Holiday(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // "yyyy-MM-dd"
    val name: String,
    val isConfirmed: Boolean = true,
    val isWeekend: Boolean = false
)
