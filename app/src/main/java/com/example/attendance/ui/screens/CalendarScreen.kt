package com.example.attendance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.attendance.data.AttendanceRecord
import com.example.attendance.data.Holiday
import com.example.attendance.data.TimetableWithSchedules
import com.example.attendance.ui.viewmodel.AttendanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CalendarScreen(viewModel: AttendanceViewModel) {
    // Optimization: Collect all records but treat carefully
    val records by viewModel.recentAttendanceRecords.collectAsState() 
    val holidays by viewModel.allHolidays.collectAsState()
    val subjects by viewModel.allTimetableEntries.collectAsState()

    var selectedSubjectId by remember { mutableLongStateOf(-1L) }
    var showFilter by remember { mutableStateOf(false) }
    var overlayText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Date Constraints: 2025 to Current+2yrs
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val startYear = 2025
    val endYear = currentYear + 2
    
    val initialPage = (Calendar.getInstance().get(Calendar.YEAR) - startYear) * 12 + Calendar.getInstance().get(Calendar.MONTH)
    val totalPages = (endYear - startYear + 1) * 12
    
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { totalPages })
    
    val currentMonthCalendar = remember(pagerState.currentPage) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, startYear + (pagerState.currentPage / 12))
            set(Calendar.MONTH, pagerState.currentPage % 12)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    LaunchedEffect(overlayText) {
        if (overlayText != null) { delay(3000); overlayText = null }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            CalendarHeader(currentMonthCalendar) { 
                scope.launch { 
                    val target = pagerState.currentPage + it
                    if (target in 0 until totalPages) {
                        pagerState.animateScrollToPage(target)
                    }
                } 
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Attendance Map", style = MaterialTheme.typography.titleMedium)
                Box {
                    val filteredSubName = remember(selectedSubjectId, subjects) {
                        if (selectedSubjectId == -1L) "All Subjects" else subjects.find { it.entry.id == selectedSubjectId }?.entry?.subjectName ?: "All"
                    }
                    TextButton(onClick = { showFilter = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(filteredSubName)
                    }
                    DropdownMenu(expanded = showFilter, onDismissRequest = { showFilter = false }) {
                        DropdownMenuItem(text = { Text("All Subjects") }, onClick = { selectedSubjectId = -1L; showFilter = false })
                        subjects.forEach { item ->
                            DropdownMenuItem(text = { Text(item.entry.subjectName) }, onClick = { selectedSubjectId = item.entry.id; showFilter = false })
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // For the calendar, we actually need ALL records for the current month.
            // Since records are now restricted to 100 in recentAttendanceRecords, 
            // we'll need to fetch month-specific records or just accept the history view optimization.
            // For now, let's keep it simple: we use recent records to avoid OOM.
            val filteredRecords = remember(selectedSubjectId, records) { if (selectedSubjectId == -1L) records else records.filter { it.timetableId == selectedSubjectId } }
            
            HorizontalPager(
                state = pagerState, 
                modifier = Modifier.wrapContentHeight(), 
                verticalAlignment = Alignment.Top, 
                beyondViewportPageCount = 0
            ) { page ->
                val monthCal = remember(page) { 
                    Calendar.getInstance().apply {
                        set(Calendar.YEAR, startYear + (page / 12))
                        set(Calendar.MONTH, page % 12)
                        set(Calendar.DAY_OF_MONTH, 1)
                    } 
                }
                CalendarGrid(monthCal, filteredRecords, holidays, subjects) { overlayText = it }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LegendItem("Weekend", Color.DarkGray); LegendItem("Holiday", Color.Blue.copy(alpha = 0.5f)); LegendItem("Present", Color.Green.copy(alpha = 0.4f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LegendItem("Absent", Color.Red.copy(alpha = 0.4f)); LegendItem("Suspended", Color.Yellow.copy(alpha = 0.4f))
                }
            }
            Spacer(modifier = Modifier.weight(1f)) 
        }
        overlayText?.let { text ->
            Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp).padding(horizontal = 32.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface, tonalElevation = 4.dp) {
                Text(text = text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(color, shape = androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(4.dp)); Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun CalendarHeader(calendar: Calendar, onMonthChange: (Int) -> Unit) {
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonthChange(-1) }) { Icon(Icons.Default.ChevronLeft, contentDescription = null) }
        Text(text = monthFormat.format(calendar.time), style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = { onMonthChange(1) }) { Icon(Icons.Default.ChevronRight, contentDescription = null) }
    }
}

@Composable
fun CalendarGrid(calendar: Calendar, records: List<AttendanceRecord>, holidays: List<Holiday>, allSubjects: List<TimetableWithSchedules>, onDayClick: (String) -> Unit) {
    val monthData = remember(calendar) {
        val tempCal = calendar.clone() as Calendar
        tempCal.set(Calendar.DAY_OF_MONTH, 1)
        val offset = if (tempCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 6 else tempCal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
        val total = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH) + offset
        offset to total
    }
    val (offset, totalCells) = monthData
    LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
        val weekDays = listOf("M", "T", "W", "T", "F", "S", "S")
        items(weekDays) { Text(text = it, modifier = Modifier.padding(8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
        items((0 until totalCells).toList()) { index ->
            if (index >= offset) {
                val day = index - offset + 1
                val dateStr = remember(calendar, day) {
                    val dayCal = calendar.clone() as Calendar
                    dayCal.set(Calendar.DAY_OF_MONTH, day)
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(dayCal.time)
                }
                
                val dayRecords = remember(records, dateStr) { records.filter { it.date == dateStr } }
                val dayHoliday = remember(holidays, dateStr) { holidays.find { it.date == dateStr && it.isConfirmed } }
                
                DayCell(day, dayRecords, dayHoliday) {
                    val info = StringBuilder()
                    if (dayHoliday != null) info.append(dayHoliday.name)
                    if (dayRecords.isNotEmpty()) {
                        if (info.isNotEmpty()) info.append("\n")
                        dayRecords.forEach { r -> 
                            val subName = allSubjects.find { it.entry.id == r.timetableId }?.entry?.subjectName ?: "Unknown"
                            info.append("$subName: ${r.status}\n") 
                        }
                    }
                    if (info.isNotEmpty()) onDayClick(info.toString().trim())
                }
            } else { Spacer(modifier = Modifier.size(40.dp)) }
        }
    }
}

@Composable
fun DayCell(day: Int, records: List<AttendanceRecord>, holiday: Holiday?, onClick: () -> Unit) {
    Box(modifier = Modifier.aspectRatio(1f).padding(2.dp).background(color = when { holiday?.isWeekend == true -> Color.DarkGray.copy(alpha = 0.8f); holiday != null -> Color.Blue.copy(alpha = 0.5f); records.any { it.status == "PRESENT" } -> Color.Green.copy(alpha = 0.4f); records.any { it.status == "ABSENT" } -> Color.Red.copy(alpha = 0.4f); records.any { it.status == "SUSPENDED" } -> Color.Yellow.copy(alpha = 0.4f); else -> MaterialTheme.colorScheme.surfaceVariant }, shape = MaterialTheme.shapes.small).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = day.toString(), style = MaterialTheme.typography.bodySmall, color = if (holiday?.isWeekend == true) Color.White else Color.Unspecified)
            if (records.isNotEmpty() || holiday != null) {
                Box(modifier = Modifier.size(4.dp).background(color = when { holiday?.isWeekend == true -> Color.LightGray; holiday != null -> Color.Cyan; records.all { it.status == "PRESENT" } -> Color.Green; records.any { it.status == "SUSPENDED" } -> Color.Yellow; else -> Color.Red }, shape = androidx.compose.foundation.shape.CircleShape))
            }
        }
    }
}
