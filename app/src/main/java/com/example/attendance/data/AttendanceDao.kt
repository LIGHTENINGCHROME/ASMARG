package com.example.attendance.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    // Timetable
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimetableEntry(entry: TimetableEntry): Long

    @Delete
    suspend fun deleteTimetableEntry(entry: TimetableEntry)

    @Transaction
    @Query("SELECT * FROM timetable_entries")
    fun getAllTimetableEntries(): Flow<List<TimetableWithSchedules>>

    @Transaction
    @Query("SELECT * FROM timetable_entries")
    suspend fun getAllTimetableEntriesOnce(): List<TimetableWithSchedules>

    @Query("SELECT * FROM timetable_entries WHERE subjectName = :name LIMIT 1")
    suspend fun getEntryByName(name: String): TimetableEntry?

    // Schedules
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ClassSchedule)

    @Query("DELETE FROM class_schedules WHERE timetableEntryId = :entryId")
    suspend fun deleteSchedulesForEntry(entryId: Long)

    @Query("SELECT * FROM class_schedules WHERE dayOfWeek = :day")
    suspend fun getSchedulesForDay(day: Int): List<ClassSchedule>

    // Attendance
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecord(record: AttendanceRecord): Long

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records ORDER BY date DESC")
    fun getAllAttendanceRecords(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records")
    suspend fun getAllAttendanceRecordsOnce(): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE timetableId = :timetableId AND scheduleId = :scheduleId AND date = :date LIMIT 1")
    suspend fun getAttendanceForScheduleAndDate(timetableId: Long, scheduleId: Long, date: String): AttendanceRecord?

    @Delete
    suspend fun deleteAttendanceRecord(record: AttendanceRecord)

    // Holidays
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHoliday(holiday: Holiday)

    @Query("SELECT * FROM holidays")
    fun getAllHolidays(): Flow<List<Holiday>>

    @Query("SELECT * FROM holidays")
    suspend fun getAllHolidaysOnce(): List<Holiday>

    @Query("SELECT * FROM holidays WHERE date = :date LIMIT 1")
    suspend fun getHolidayForDate(date: String): Holiday?

    @Query("SELECT * FROM holidays WHERE date = :date AND name = :name LIMIT 1")
    suspend fun getHolidayByDateAndName(date: String, name: String): Holiday?

    @Update
    suspend fun updateHoliday(holiday: Holiday)

    @Delete
    suspend fun deleteHoliday(holiday: Holiday)

    @Query("DELETE FROM holidays WHERE isWeekend = 1")
    suspend fun clearWeekendHolidays()

    @Query("DELETE FROM timetable_entries")
    suspend fun clearAllTimetableEntries()

    @Query("DELETE FROM class_schedules")
    suspend fun clearAllSchedules()

    @Query("DELETE FROM attendance_records")
    suspend fun clearAllAttendanceRecords()

    @Query("DELETE FROM holidays")
    suspend fun clearAllHolidays()
}
