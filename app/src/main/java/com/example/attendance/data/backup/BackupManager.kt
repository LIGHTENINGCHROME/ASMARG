package com.example.attendance.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.attendance.data.AttendanceDatabase
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class BackupManager(private val context: Context) {
    private val gson = Gson()

    suspend fun exportData(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val dao = AttendanceDatabase.getDatabase(context).attendanceDao()
            val timetableWithSchedules = dao.getAllTimetableEntriesOnce()
            
            val backup = BackupData(
                timetableEntries = timetableWithSchedules.map { it.entry },
                classSchedules = timetableWithSchedules.flatMap { it.schedules },
                attendanceRecords = dao.getAllAttendanceRecordsOnce(),
                holidays = dao.getAllHolidaysOnce()
            )

            val json = gson.toJson(backup)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Export failed", e)
            false
        }
    }

    /**
     * Ultra-Safe Import that handles legacy IDs and potential file corruption
     */
    suspend fun importData(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("BackupManager", "Starting deep import for URI: $uri")
            
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext false
            val reader = JsonReader(InputStreamReader(inputStream))
            reader.isLenient = true // Key: handle trailing garbage or minor syntax errors

            val backup = try {
                gson.fromJson<BackupData>(reader, BackupData::class.java)
            } catch (e: Exception) {
                Log.e("BackupManager", "JSON Parse Error", e)
                null
            } finally {
                reader.close()
            }

            if (backup == null) {
                Log.e("BackupManager", "Import failed: Backup object is null after parsing")
                return@withContext false
            }

            val database = AttendanceDatabase.getDatabase(context)
            val dao = database.attendanceDao()

            // Destructive Restore Strategy
            // We clear in reverse dependency order
            Log.d("BackupManager", "Clearing current database...")
            dao.clearAllAttendanceRecords()
            dao.clearAllSchedules()
            dao.clearAllTimetableEntries()
            dao.clearAllHolidays()

            // 1. Restore Subjects (Parents) - Crucial to do this first
            backup.timetableEntries?.forEach { entry ->
                try {
                    dao.insertTimetableEntry(entry)
                    Log.d("BackupManager", "Restored subject: ${entry.subjectName} (ID: ${entry.id})")
                } catch (e: Exception) {
                    Log.w("BackupManager", "Skipping subject ${entry.subjectName}: ${e.message}")
                }
            }

            // 2. Restore Schedules (Children)
            backup.classSchedules?.forEach { schedule ->
                try {
                    dao.insertSchedule(schedule)
                } catch (e: Exception) {
                    Log.w("BackupManager", "Skipping schedule ${schedule.scheduleId}: ${e.message}")
                }
            }

            // 3. Restore History
            backup.attendanceRecords?.forEach { record ->
                try {
                    dao.insertAttendanceRecord(record)
                } catch (e: Exception) {
                    Log.w("BackupManager", "Skipping attendance record: ${e.message}")
                }
            }

            // 4. Restore Holidays
            backup.holidays?.forEach { holiday ->
                try {
                    dao.insertHoliday(holiday)
                } catch (e: Exception) {
                    Log.w("BackupManager", "Skipping holiday ${holiday.name}: ${e.message}")
                }
            }

            Log.d("BackupManager", "Deep import finished successfully.")
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "CRITICAL IMPORT ERROR", e)
            false
        }
    }
}
