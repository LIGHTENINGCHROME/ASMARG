package com.example.attendance.data

import androidx.room.Embedded
import androidx.room.Relation

data class TimetableWithSchedules(
    @Embedded val entry: TimetableEntry,
    @Relation(
        parentColumn = "id",
        entityColumn = "timetableEntryId"
    )
    val schedules: List<ClassSchedule>
)
