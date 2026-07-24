package com.example.attendance.data.backup

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import com.example.attendance.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class BackupManager(private val context: Context) {

    suspend fun exportData(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val database = AttendanceDatabase.getDatabase(context)
            val dao = database.attendanceDao()
            
            val timetables = dao.getAllTimetableEntriesOnce()
            val holidays = dao.getAllHolidaysOnce()
            val attendance = dao.getAllAttendanceRecordsOnce()

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                JsonWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                    writer.setIndent("  ")
                    writer.beginObject()

                    writer.name("timetables")
                    writer.beginArray()
                    timetables.forEach { item ->
                        writer.beginObject()
                        writer.name("entry")
                        writer.beginObject()
                        writer.name("id").value(item.entry.id)
                        writer.name("subjectName").value(item.entry.subjectName)
                        writer.name("subjectFullName").value(item.entry.subjectFullName)
                        writer.name("latitude").value(item.entry.latitude)
                        writer.name("longitude").value(item.entry.longitude)
                        writer.name("radiusInMeters").value(item.entry.radiusInMeters.toLong())
                        writer.name("attendanceThresholdMinutes").value(item.entry.attendanceThresholdMinutes.toLong())
                        writer.endObject()

                        writer.name("schedules")
                        writer.beginArray()
                        item.schedules.forEach { s ->
                            writer.beginObject()
                            writer.name("scheduleId").value(s.scheduleId)
                            writer.name("timetableEntryId").value(s.timetableEntryId)
                            writer.name("dayOfWeek").value(s.dayOfWeek.toLong())
                            writer.name("startTime").value(s.startTime)
                            writer.name("endTime").value(s.endTime)
                            writer.endObject()
                        }
                        writer.endArray()
                        writer.endObject()
                    }
                    writer.endArray()

                    writer.name("holidays")
                    writer.beginArray()
                    holidays.forEach { h ->
                        writer.beginObject()
                        writer.name("id").value(h.id)
                        writer.name("date").value(h.date)
                        writer.name("name").value(h.name)
                        writer.name("isConfirmed").value(h.isConfirmed)
                        writer.name("isWeekend").value(h.isWeekend)
                        writer.endObject()
                    }
                    writer.endArray()

                    writer.name("attendance")
                    writer.beginArray()
                    attendance.forEach { r ->
                        writer.beginObject()
                        writer.name("id").value(r.id)
                        writer.name("timetableId").value(r.timetableId)
                        writer.name("scheduleId").value(r.scheduleId)
                        writer.name("date").value(r.date)
                        writer.name("status").value(r.status)
                        writer.name("timestamp").value(r.timestamp)
                        writer.endObject()
                    }
                    writer.endArray()

                    writer.endObject()
                }
            }
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Export failed", e)
            false
        }
    }

    suspend fun importData(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val database = AttendanceDatabase.getDatabase(context)
            val dao = database.attendanceDao()

            // CRITICAL: Clear existing data before import to avoid overlaps and duplicates
            dao.clearDatabase()

            // Map to track old timetableId to NEW timetableId
            val subjectIdMap = mutableMapOf<Long, Long>()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                JsonReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "timetables" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    importTimetableItem(reader, dao, subjectIdMap)
                                }
                                reader.endArray()
                            }
                            "holidays" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    importHoliday(reader, dao)
                                }
                                reader.endArray()
                            }
                            "attendance" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    importAttendanceRecord(reader, dao, subjectIdMap)
                                }
                                reader.endArray()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Import failed", e)
            false
        }
    }

    private suspend fun importTimetableItem(reader: JsonReader, dao: AttendanceDao, idMap: MutableMap<Long, Long>) {
        var oldId = -1L
        var entry: TimetableEntry? = null
        val schedules = mutableListOf<ClassSchedule>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "entry" -> {
                    // Extract old ID from JSON before reading the entry object
                    // We need to read the entry object fields manually to get the ID
                    var subjectName = ""; var subjectFullName: String? = null; var lat = 0.0; var lon = 0.0; var radius = 50.0; var threshold = 15
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "id" -> oldId = reader.nextLong()
                            "subjectName" -> subjectName = reader.nextString()
                            "subjectFullName" -> subjectFullName = if (reader.peek() == android.util.JsonToken.NULL) { reader.nextNull(); null } else reader.nextString()
                            "latitude" -> lat = reader.nextDouble()
                            "longitude" -> lon = reader.nextDouble()
                            "radiusInMeters" -> radius = reader.nextDouble()
                            "attendanceThresholdMinutes" -> threshold = reader.nextInt()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    entry = TimetableEntry(subjectName = subjectName, subjectFullName = subjectFullName, latitude = lat, longitude = lon, radiusInMeters = radius.toFloat(), attendanceThresholdMinutes = threshold)
                }
                "schedules" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var day = 1; var start = ""; var end = ""
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "dayOfWeek" -> day = reader.nextInt()
                                "startTime" -> start = reader.nextString()
                                "endTime" -> end = reader.nextString()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        schedules.add(ClassSchedule(timetableEntryId = 0, dayOfWeek = day, startTime = start, endTime = end))
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        entry?.let {
            val newId = dao.insertTimetableEntry(it.copy(id = 0))
            if (oldId != -1L) idMap[oldId] = newId
            schedules.forEach { s -> dao.insertSchedule(s.copy(scheduleId = 0, timetableEntryId = newId)) }
        }
    }

    private suspend fun importHoliday(reader: JsonReader, dao: AttendanceDao) {
        var date = ""; var name = ""; var confirmed = false; var weekend = false
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "date" -> date = reader.nextString()
                "name" -> name = reader.nextString()
                "isConfirmed" -> confirmed = reader.nextBoolean()
                "isWeekend" -> weekend = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        dao.insertHoliday(Holiday(date = date, name = name, isConfirmed = confirmed, isWeekend = weekend))
    }

    private suspend fun importAttendanceRecord(reader: JsonReader, dao: AttendanceDao, idMap: Map<Long, Long>) {
        var oldTId = -1L; var schedId = -1L; var date = ""; var status = ""; var timestamp = 0L
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "timetableId" -> oldTId = reader.nextLong()
                "scheduleId" -> schedId = reader.nextLong()
                "date" -> date = reader.nextString()
                "status" -> status = reader.nextString()
                "timestamp" -> timestamp = reader.nextLong()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        
        // Use the mapped NEW ID for the subject
        val newTId = idMap[oldTId] ?: oldTId
        dao.insertAttendanceRecord(AttendanceRecord(timetableId = newTId, scheduleId = schedId, date = date, status = status, timestamp = timestamp))
    }
}
