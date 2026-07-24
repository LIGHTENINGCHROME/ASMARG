package com.example.attendance.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    // Timetable
    @Transaction
    @Query("SELECT * FROM timetable_entries")
    fun getAllTimetableEntries(): Flow<List<TimetableWithSchedules>>

    @Transaction
    @Query("SELECT * FROM timetable_entries")
    suspend fun getAllTimetableEntriesOnce(): List<TimetableWithSchedules>

    @Query("SELECT * FROM timetable_entries WHERE subjectName = :name LIMIT 1")
    suspend fun getEntryByName(name: String): TimetableEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimetableEntry(entry: TimetableEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ClassSchedule)

    @Delete
    suspend fun deleteTimetableEntry(entry: TimetableEntry)

    @Query("DELETE FROM class_schedules WHERE timetableEntryId = :entryId")
    suspend fun deleteSchedulesForEntry(entryId: Long)

    @Query("DELETE FROM timetable_entries")
    suspend fun clearAllTimetables()

    @Query("DELETE FROM class_schedules")
    suspend fun clearAllSchedules()

    // Attendance
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecord(record: AttendanceRecord): Long

    @Query("SELECT * FROM attendance_records ORDER BY date DESC")
    fun getAllAttendanceRecords(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records")
    suspend fun getAllAttendanceRecordsOnce(): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE timetableId = :timetableId AND scheduleId = :scheduleId AND date = :date LIMIT 1")
    suspend fun getAttendanceForScheduleAndDate(timetableId: Long, scheduleId: Long, date: String): AttendanceRecord?

    @Delete
    suspend fun deleteAttendanceRecord(record: AttendanceRecord)

    @Query("DELETE FROM attendance_records")
    suspend fun clearAllAttendance()

    // Holidays
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHoliday(holiday: Holiday)

    @Update
    suspend fun updateHoliday(holiday: Holiday)

    @Query("SELECT * FROM holidays ORDER BY date ASC")
    fun getAllHolidays(): Flow<List<Holiday>>

    @Query("SELECT * FROM holidays")
    suspend fun getAllHolidaysOnce(): List<Holiday>

    @Query("SELECT * FROM holidays WHERE date = :date LIMIT 1")
    suspend fun getHolidayForDate(date: String): Holiday?

    @Query("SELECT * FROM holidays WHERE date = :date AND name = :name LIMIT 1")
    suspend fun getHolidayByDateAndName(date: String, name: String): Holiday?

    @Delete
    suspend fun deleteHoliday(holiday: Holiday)

    @Query("DELETE FROM holidays")
    suspend fun clearAllHolidays()

    @Query("DELETE FROM holidays WHERE isWeekend = 1")
    suspend fun clearWeekendHolidays()

    @Transaction
    suspend fun clearDatabase() {
        clearAllAttendance()
        clearAllSchedules()
        clearAllTimetables()
        clearAllHolidays()
    }
}
