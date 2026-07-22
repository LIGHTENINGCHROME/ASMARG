package com.example.attendance.ocr

import com.example.attendance.data.ClassSchedule
import com.example.attendance.data.TimetableEntry
import com.example.attendance.data.TimetableWithSchedules
import java.util.regex.Pattern

class TimetableParser {

    private val dayMap = mapOf(
        "monday" to 1, "mon" to 1,
        "tuesday" to 2, "tue" to 2,
        "wednesday" to 3, "wed" to 3,
        "thursday" to 4, "thu" to 4,
        "friday" to 5, "fri" to 5,
        "saturday" to 6, "sat" to 6,
        "sunday" to 7, "sun" to 7
    )

    private val timeRegex = Pattern.compile("([0-1]?[0-9]|2[0-3])[:.][0-5][0-9]")

    fun parseGrid(texts: List<RecognizedText>): List<TimetableWithSchedules> {
        val rawSchedules = mutableListOf<RawSchedule>()
        
        val timeHeaders = texts.filter { timeRegex.matcher(it.text).find() }
            .mapNotNull { 
                val matcher = timeRegex.matcher(it.text)
                val times = mutableListOf<String>()
                while (matcher.find()) {
                    times.add(matcher.group().replace(".", ":"))
                }
                if (times.size >= 2 && it.boundingBox != null) {
                    TimeColumn(times[0], times[1], it.boundingBox.centerX())
                } else null
            }.sortedBy { it.centerX }

        val dayRows = texts.filter { text -> dayMap.keys.any { text.text.lowercase().contains(it) } }
            .mapNotNull { 
                val dayFound = dayMap.keys.find { day -> it.text.lowercase().contains(day) }
                if (dayFound != null && it.boundingBox != null) {
                    DayRow(dayMap[dayFound]!!, it.boundingBox.centerY())
                } else null
            }.sortedBy { it.centerY }

        if (timeHeaders.isEmpty() || dayRows.isEmpty()) return emptyList()

        // 1. Identify Sections
        val facultyHeader = texts.find { it.text.contains("Faculty Details", ignoreCase = true) }
        val gridMaxY = facultyHeader?.boundingBox?.top ?: Int.MAX_VALUE

        // 2. Parse Subject Mapping (Faculty Details section)
        val nameMapping = mutableMapOf<String, String>()
        if (facultyHeader != null) {
            val facultyLines = texts.filter { it.boundingBox != null && it.boundingBox.top >= gridMaxY }
                .sortedBy { it.boundingBox!!.top }
            
            // Heuristic: Looking for "Code Name Type ..." pattern
            // For each line that looks like a code (e.g., CSPC 501), find text to its right
            val possibleCodes = facultyLines.filter { it.text.length in 4..12 && it.text.any { c -> c.isDigit() } }
            for (codeText in possibleCodes) {
                val lineY = codeText.boundingBox!!.centerY()
                val codeX = codeText.boundingBox.right
                val nameInLine = facultyLines.filter { 
                    Math.abs(it.boundingBox!!.centerY() - lineY) < 20 && it.boundingBox.left > codeX 
                }.minByOrNull { it.boundingBox!!.left }
                
                if (nameInLine != null && nameInLine.text.length > 3) {
                    nameMapping[codeText.text.uppercase().trim()] = nameInLine.text.trim()
                }
            }
        }

        // 3. Parse Grid Subjects
        val subjects = texts.filter { 
            val txt = it.text.lowercase()
            !dayMap.keys.any { d -> txt.contains(d) } && 
            !timeRegex.matcher(it.text).find() &&
            it.boundingBox != null && it.boundingBox.top < gridMaxY &&
            it.text.length > 2 && it.text != "-"
        }

        for (subjectText in subjects) {
            val center = subjectText.boundingBox!!
            val row = dayRows.minByOrNull { Math.abs(it.centerY - center.centerY()) }
            val col = timeHeaders.minByOrNull { Math.abs(it.centerX - center.centerX()) }

            if (row != null && col != null) {
                var name = subjectText.text
                if (name.contains("(")) {
                    name = name.substringBefore("(").trim()
                }

                if (name.length > 2 && name != "-") {
                    rawSchedules.add(RawSchedule(name.uppercase().trim(), row.day, col.start, col.end))
                }
            }
        }

        return groupAndMerge(rawSchedules, nameMapping)
    }

    private fun groupAndMerge(raw: List<RawSchedule>, nameMapping: Map<String, String>): List<TimetableWithSchedules> {
        val subjects = raw.groupBy { it.subjectName }
        
        return subjects.map { (code, schedules) ->
            val mergedSchedules = mutableListOf<ClassSchedule>()
            val byDay = schedules.groupBy { it.day }
            byDay.forEach { (day, daySchedules) ->
                val sorted = daySchedules.sortedBy { it.start }
                if (sorted.isEmpty()) return@forEach
                
                var currentStart = sorted[0].start
                var currentEnd = sorted[0].end
                
                for (i in 1 until sorted.size) {
                    if (sorted[i].start == currentEnd) {
                        currentEnd = sorted[i].end
                    } else {
                        mergedSchedules.add(ClassSchedule(timetableEntryId = 0, dayOfWeek = day, startTime = currentStart, endTime = currentEnd))
                        currentStart = sorted[i].start
                        currentEnd = sorted[i].end
                    }
                }
                mergedSchedules.add(ClassSchedule(timetableEntryId = 0, dayOfWeek = day, startTime = currentStart, endTime = currentEnd))
            }
            
            TimetableWithSchedules(
                entry = TimetableEntry(
                    subjectName = code,
                    subjectFullName = nameMapping[code]
                ),
                schedules = mergedSchedules
            )
        }
    }

    data class TimeColumn(val start: String, val end: String, val centerX: Int)
    data class DayRow(val day: Int, val centerY: Int)
    data class RawSchedule(val subjectName: String, val day: Int, val start: String, val end: String)

    fun parse(text: String): List<TimetableWithSchedules> = emptyList()
}
