package com.example.attendance.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.attendance.data.AttendanceRecord
import com.example.attendance.data.TimetableWithSchedules
import com.example.attendance.ui.viewmodel.AttendanceConflict
import com.example.attendance.ui.viewmodel.AttendanceViewModel
import com.example.attendance.ui.viewmodel.SubjectStats
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(viewModel: AttendanceViewModel) {
    // Optimization: Use recentAttendanceRecords (limit 100) instead of all records
    val records by viewModel.recentAttendanceRecords.collectAsState()
    val timetableEntries by viewModel.allTimetableEntries.collectAsState()
    val stats by viewModel.subjectStats.collectAsState()
    
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var headerTapCount by remember { mutableIntStateOf(0) }
    val isDebugEnabled by remember { derivedStateOf { headerTapCount >= 5 } }

    var showFullName by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var recordToDelete by remember { mutableStateOf<AttendanceRecord?>(null) }
    
    var bulkAddSubjectId by remember { mutableLongStateOf(-1L) }
    var activeConflicts by remember { mutableStateOf<List<AttendanceConflict>>(emptyList()) }
    var bulkAddParams by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    Scaffold(
        topBar = {
            if (selectedTabIndex == 1) {
                TopAppBar(
                    title = { Text("History") },
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            Text("Names", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = showFullName, onCheckedChange = { showFullName = it }, modifier = Modifier.scale(0.7f))
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (selectedTabIndex == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Attendance", 
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            headerTapCount++
                        }
                    )
                    if (isDebugEnabled) {
                        Button(onClick = { viewModel.triggerManualAttendanceCheck() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                            Icon(Icons.Default.BugReport, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Check Now")
                        }
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Subject-wise") })
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("All History") })
            }

            when (selectedTabIndex) {
                0 -> SubjectWiseAttendance(
                    timetableItems = timetableEntries, 
                    stats = stats, 
                    viewModel = viewModel, 
                    onDeleteRequest = { recordToDelete = it },
                    onBulkAddRequest = { bulkAddSubjectId = it }
                )
                1 -> AttendanceHistory(
                    records = records, 
                    timetableItems = timetableEntries, 
                    viewModel = viewModel, 
                    showFullName = showFullName, 
                    onDeleteRequest = { recordToDelete = it }
                )
            }
        }
    }

    // Bulk Add Conflict Review Flow
    if (activeConflicts.isNotEmpty() && bulkAddParams != null) {
        val (start, end, status) = bulkAddParams!!
        ConflictReviewDialog(
            conflicts = activeConflicts,
            onDismiss = { activeConflicts = emptyList(); bulkAddParams = null },
            onConfirm = { updateDateSchedules ->
                viewModel.bulkAddAttendanceConfirmed(bulkAddSubjectId, start, end, status, updateDateSchedules)
                activeConflicts = emptyList()
                bulkAddParams = null
                bulkAddSubjectId = -1L
            }
        )
    }

    if (bulkAddSubjectId != -1L && activeConflicts.isEmpty()) {
        val subject = timetableEntries.find { it.entry.id == bulkAddSubjectId }
        BulkAddAttendanceDialog(
            subjectName = subject?.entry?.subjectName ?: "",
            onDismiss = { bulkAddSubjectId = -1L },
            onConfirm = { start, end, status ->
                scope.launch {
                    val conflicts = viewModel.checkBulkAddConflicts(bulkAddSubjectId, start, end, status)
                    if (conflicts.isEmpty()) {
                        viewModel.bulkAddAttendanceConfirmed(bulkAddSubjectId, start, end, status, emptySet())
                        bulkAddSubjectId = -1L
                    } else {
                        bulkAddParams = Triple(start, end, status)
                        activeConflicts = conflicts
                    }
                }
            }
        )
    }

    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Delete Record?") },
            text = { Text("Are you sure you want to delete this attendance record for ${record.date}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAttendanceRecord(record)
                        recordToDelete = null
                        scope.launch {
                            val result = snackbarHostState.showSnackbar("Record deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.updateAttendanceStatus(record, record.status)
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { recordToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ConflictReviewDialog(
    conflicts: List<AttendanceConflict>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val updateDateSchedules = remember { mutableStateOf(emptySet<String>()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Current History?") },
        text = {
            Column {
                Text("Existing records were found. Check items that you want to UPDATE to the new status. Unchecked items will be kept as they are.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(conflicts) { conflict ->
                        val key = "${conflict.date}_${conflict.scheduleId}"
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Checkbox(
                                checked = updateDateSchedules.value.contains(key),
                                onCheckedChange = { checked ->
                                    updateDateSchedules.value = if (checked) updateDateSchedules.value + key else updateDateSchedules.value - key
                                }
                            )
                            Column {
                                Text("${conflict.date} (${conflict.startTime})", style = MaterialTheme.typography.labelLarge)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Keep: ${conflict.currentStatus}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp).padding(horizontal = 4.dp))
                                    Text("New: ${conflict.newStatus}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(updateDateSchedules.value) }) { Text("Update Selected") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep All Original") } }
    )
}

@Composable
fun SubjectWiseAttendance(
    timetableItems: List<TimetableWithSchedules>,
    stats: Map<Long, SubjectStats>,
    viewModel: AttendanceViewModel,
    onDeleteRequest: (AttendanceRecord) -> Unit,
    onBulkAddRequest: (Long) -> Unit
) {
    val subjects = remember(timetableItems) { timetableItems.map { it.entry.subjectName }.distinct() }
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Spacer(modifier = Modifier.height(16.dp)) }
        items(subjects, key = { it }) { subject ->
            val item = remember(timetableItems, subject) { timetableItems.find { it.entry.subjectName == subject } } ?: return@items
            val entry = item.entry
            val sStats = stats[entry.id] ?: SubjectStats(0, 0, 0, 0f, 0f)

            var expanded by remember { mutableStateOf(false) }

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { expanded = !expanded }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = subject, style = MaterialTheme.typography.titleLarge)
                            if (!entry.subjectFullName.isNullOrEmpty()) {
                                Text(text = entry.subjectFullName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("Effective Attendance (Suspended = Present)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(
                        progress = { sStats.effectivePercentage },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = if (sStats.effectivePercentage >= 0.75f) Color.Green else Color.Red
                    )
                    Text(text = "${(sStats.effectivePercentage * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Actual Attendance (Excluding Suspended)", style = MaterialTheme.typography.labelSmall)
                    LinearProgressIndicator(
                        progress = { sStats.percentage },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = if (sStats.percentage >= 0.75f) Color.Green.copy(alpha = 0.6f) else Color.Red.copy(alpha = 0.6f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Stats: ${sStats.presentCount} P / ${sStats.totalCount} Held", style = MaterialTheme.typography.bodySmall)
                        if (sStats.suspendedCount > 0) {
                            Text(text = "Suspended: ${sStats.suspendedCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }

                    if (expanded) {
                        // Lazy load subject records only when expanded
                        val subjectRecords by viewModel.getRecordsForSubject(entry.id).collectAsState(initial = emptyList())
                        
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            
                            Button(
                                onClick = { onBulkAddRequest(entry.id) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Icon(Icons.Default.LibraryAdd, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Bulk Add Attendance")
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            if (subjectRecords.isEmpty()) {
                                Text("No records yet.", style = MaterialTheme.typography.bodySmall)
                            }
                            subjectRecords.sortedByDescending { it.date }.forEach { record ->
                                AttendanceRecordRow(record = record, onUpdateStatus = { status -> viewModel.updateAttendanceStatus(record, status) }, onDelete = { onDeleteRequest(record) })
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkAddAttendanceDialog(
    subjectName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var startDate by remember { mutableStateOf(sdf.format(Date())) }
    var endDate by remember { mutableStateOf(sdf.format(Date())) }
    var status by remember { mutableStateOf("PRESENT") }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // Date Constraints: 2025 to Current+2yrs
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val minDate = remember { Calendar.getInstance().apply { set(2025, 0, 1) }.timeInMillis }
    val maxDate = remember { Calendar.getInstance().apply { set(currentYear + 2, 11, 31) }.timeInMillis }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk Attendance: $subjectName") },
        text = {
            Column {
                Text("Select range. Only days scheduled for this subject will be marked.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                
                OutlinedCard(onClick = { showStartPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("From Date", style = MaterialTheme.typography.labelSmall)
                        Text(startDate, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedCard(onClick = { showEndPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("To Date", style = MaterialTheme.typography.labelSmall)
                        Text(endDate, style = MaterialTheme.typography.titleMedium)
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Text("Mark as:", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(selected = status == "PRESENT", onClick = { status = "PRESENT" }, label = { Text("Present") })
                    FilterChip(selected = status == "ABSENT", onClick = { status = "ABSENT" }, label = { Text("Absent") })
                    FilterChip(selected = status == "SUSPENDED", onClick = { status = "SUSPENDED" }, label = { Text("Suspended") })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(startDate, endDate, status) }) { Text("Mark Range") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showStartPicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in minDate..maxDate
            }
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { startDate = sdf.format(Date(it)) }
                    showStartPicker = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = dateState) }
    }
    if (showEndPicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in minDate..maxDate
            }
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { endDate = sdf.format(Date(it)) }
                    showEndPicker = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = dateState) }
    }
}

@Composable
fun AttendanceRecordRow(record: AttendanceRecord, onUpdateStatus: (String) -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = record.date, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = when (record.status) {
                    "PRESENT" -> Color.Green
                    "ABSENT" -> Color.Red
                    "SUSPENDED" -> Color.Gray
                    else -> Color.Gray
                }
                Text(text = record.status, color = color, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val mod = Modifier.weight(1f).height(32.dp)
            FilledTonalButton(onClick = { onUpdateStatus("PRESENT") }, modifier = mod, colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (record.status == "PRESENT") Color.Green.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant), contentPadding = PaddingValues(0.dp)) { Text("P", style = MaterialTheme.typography.labelSmall) }
            FilledTonalButton(onClick = { onUpdateStatus("ABSENT") }, modifier = mod, colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (record.status == "ABSENT") Color.Red.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant), contentPadding = PaddingValues(0.dp)) { Text("A", style = MaterialTheme.typography.labelSmall) }
            FilledTonalButton(onClick = { onUpdateStatus("SUSPENDED") }, modifier = mod, colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (record.status == "SUSPENDED") Color.Gray.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant), contentPadding = PaddingValues(0.dp)) { Text("S", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
fun AttendanceHistory(
    records: List<AttendanceRecord>, 
    timetableItems: List<TimetableWithSchedules>, 
    viewModel: AttendanceViewModel,
    showFullName: Boolean,
    onDeleteRequest: (AttendanceRecord) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Spacer(modifier = Modifier.height(16.dp)) }
        items(records, key = { it.id }) { record ->
            val item = remember(timetableItems, record.timetableId) { timetableItems.find { it.entry.id == record.timetableId } }
            val name = if (showFullName && !item?.entry?.subjectFullName.isNullOrEmpty()) {
                item?.entry?.subjectFullName!!
            } else {
                item?.entry?.subjectName ?: "Unknown"
            }
            AttendanceItem(record = record, subject = name, onUpdateStatus = { status -> viewModel.updateAttendanceStatus(record, status) }, onDelete = { onDeleteRequest(record) })
        }
    }
}

@Composable
fun AttendanceItem(record: AttendanceRecord, subject: String, onUpdateStatus: (String) -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = subject, style = MaterialTheme.typography.titleMedium)
                    Text(text = record.date, style = MaterialTheme.typography.bodySmall)
                }
                val color = when (record.status) {
                    "PRESENT" -> Color.Green
                    "ABSENT" -> Color.Red
                    "SUSPENDED" -> Color.Gray
                    else -> Color.Gray
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = record.status, color = color, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val mod = Modifier.weight(1f)
                OutlinedButton(onClick = { onUpdateStatus("PRESENT") }, modifier = mod, border = ButtonDefaults.outlinedButtonBorder(enabled = true).let { if (record.status == "PRESENT") it.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.Green)) else it }, contentPadding = PaddingValues(0.dp)) { Text("P", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(onClick = { onUpdateStatus("ABSENT") }, modifier = mod, border = ButtonDefaults.outlinedButtonBorder(enabled = true).let { if (record.status == "ABSENT") it.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.Red)) else it }, contentPadding = PaddingValues(0.dp)) { Text("A", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(onClick = { onUpdateStatus("SUSPENDED") }, modifier = mod, border = ButtonDefaults.outlinedButtonBorder(enabled = true).let { if (record.status == "SUSPENDED") it.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.Gray)) else it }, contentPadding = PaddingValues(0.dp)) { Text("S", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
