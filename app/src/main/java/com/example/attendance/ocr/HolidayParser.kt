package com.example.attendance.ocr

import com.example.attendance.data.Holiday
import java.util.regex.Pattern

class HolidayParser {

    // Regex for YYYY-MM-DD or DD-MM-YYYY (supports - or / or .)
    private val dateRegex = Pattern.compile("(\\d{4}[-/. ]\\d{1,2}[-/. ]\\d{1,2})|(\\d{1,2}[-/. ]\\d{1,2}[-/. ]\\d{4})")
    private val dayOfWeekRegex = Pattern.compile("(?i)^(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)$")

    fun parseGrid(texts: List<RecognizedText>): List<Holiday> {
        val holidays = mutableListOf<Holiday>()
        if (texts.isEmpty()) return holidays

        // 1. Group into rows by Y-coordinate
        val sortedTexts = texts.filter { it.boundingBox != null }.sortedBy { it.boundingBox!!.top }
        val rows = mutableListOf<MutableList<RecognizedText>>()
        
        if (sortedTexts.isNotEmpty()) {
            var currentRow = mutableListOf(sortedTexts[0])
            rows.add(currentRow)
            
            for (i in 1 until sortedTexts.size) {
                val rowY = currentRow.first().boundingBox!!.centerY()
                val currentY = sortedTexts[i].boundingBox!!.centerY()
                
                if (Math.abs(currentY - rowY) < 50) {
                    currentRow.add(sortedTexts[i])
                } else {
                    currentRow = mutableListOf(sortedTexts[i])
                    rows.add(currentRow)
                }
            }
        }

        // 2. Process each row
        for (row in rows) {
            val sortedRow = row.sortedBy { it.boundingBox!!.left }
            val fullLine = sortedRow.joinToString(" ") { it.text }
            val dateMatcher = dateRegex.matcher(fullLine)
            
            if (dateMatcher.find()) {
                val dateStr = dateMatcher.group()
                
                val candidates = row.map { it.text.trim() }
                    .filter { it != dateStr }
                    .filter { !dayOfWeekRegex.matcher(it).matches() }
                    .filter { it.any { c -> c.isLetter() } }
                    .filter { !it.equals("Date", ignoreCase = true) && !it.equals("Day", ignoreCase = true) && !it.equals("Holiday", ignoreCase = true) }
                
                val holidayName = candidates.maxByOrNull { it.length } ?: "Unknown Holiday"
                
                holidays.add(
                    Holiday(
                        date = normalizeDate(dateStr),
                        name = holidayName.trim(),
                        isConfirmed = false
                    )
                )
            }
        }

        return holidays
    }

    private fun normalizeDate(dateStr: String): String {
        // Clean symbols to -
        val cleanDate = dateStr.replace("/", "-").replace(".", "-").replace(" ", "-")
        val parts = cleanDate.split("-")
        
        return if (parts[0].length == 4) {
            // Already YYYY-MM-DD
            val y = parts[0]
            val m = parts[1].padStart(2, '0')
            val d = parts[2].padStart(2, '0')
            "$y-$m-$d"
        } else {
            // Assume DD-MM-YYYY
            val d = parts[0].padStart(2, '0')
            val m = parts[1].padStart(2, '0')
            val y = parts[2]
            "$y-$m-$d"
        }
    }

    fun parse(text: String): List<Holiday> = emptyList()
}
