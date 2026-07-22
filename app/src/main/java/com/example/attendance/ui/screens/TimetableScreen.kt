package com.example.attendance.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.attendance.data.ClassSchedule
import com.example.attendance.data.TimetableEntry
import com.example.attendance.data.TimetableWithSchedules
import com.example.attendance.ui.viewmodel.AttendanceViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(viewModel: AttendanceViewModel) {
    val timetableEntries by viewModel.allTimetableEntries.collectAsState()
    var editingEntry by remember { mutableStateOf<TimetableWithSchedules?>(null) }
    var entryToEditDetails by remember { mutableStateOf<TimetableWithSchedules?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showBulkLocationDialog by remember { mutableStateOf(false) }
    var showFullName by remember { mutableStateOf(false) }
    var showUploadOptions by remember { mutableStateOf(false) }
    var showCameraWarning by remember { mutableStateOf(false) }
    
    val selectedIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode by remember { derivedStateOf { selectedIds.isNotEmpty() } }

    val context = LocalContext.current
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.processTimetableDocument(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempPhotoUri?.let { viewModel.processTimetableDocument(it) }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showUploadOptions = true
        } else {
            showCameraWarning = true
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val file = File(context.cacheDir, "images/temp_timetable_${System.currentTimeMillis()}.jpg")
            file.parentFile?.mkdirs()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSelectionMode) Text("${selectedIds.size} Selected") else Text("Timetable")
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            val toDelete = timetableEntries.filter { it.entry.id in selectedIds }.map { it.entry }
                            viewModel.deleteTimetableEntries(toDelete)
                            selectedIds.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                        IconButton(onClick = { selectedIds.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            Text("Names", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = showFullName, onCheckedChange = { showFullName = it }, modifier = Modifier.scale(0.7f))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(onClick = { showBulkLocationDialog = true }, containerColor = MaterialTheme.colorScheme.secondary) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Bulk Location")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FloatingActionButton(onClick = { viewModel.cleanUpDuplicates() }, containerColor = MaterialTheme.colorScheme.tertiary) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Clean Duplicates")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FloatingActionButton(onClick = { showUploadOptions = true }) {
                        Icon(Icons.Default.Upload, contentDescription = "Scan Timetable")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FloatingActionButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Manually")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(timetableEntries, key = { it.entry.id }) { item ->
                val isSelected = selectedIds.contains(item.entry.id)
                TimetableItem(
                    item = item, 
                    showFullName = showFullName,
                    isSelected = isSelected,
                    onEditLocation = { editingEntry = item },
                    onEditDetails = { 
                        if (isSelectionMode) {
                            if (isSelected) selectedIds.remove(item.entry.id) else selectedIds.add(item.entry.id)
                        } else {
                            entryToEditDetails = item 
                        }
                    },
                    onLongClick = {
                        if (!isSelectionMode) {
                            selectedIds.add(item.entry.id)
                        }
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        if (showUploadOptions) {
            AlertDialog(
                onDismissRequest = { showUploadOptions = false },
                title = { Text("Scan Timetable") },
                text = { Text("Choose an option to upload your timetable.") },
                confirmButton = {
                    TextButton(onClick = { 
                        showUploadOptions = false
                        galleryLauncher.launch("*/*")
                    }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Gallery/PDF")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showUploadOptions = false
                        launchCamera()
                    }) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Camera")
                    }
                }
            )
        }

        if (showCameraWarning) {
            AlertDialog(
                onDismissRequest = { showCameraWarning = false },
                title = { Text("Camera Permission Required") },
                text = { Text("The app needs camera access to take photos. Please enable it in system settings.") },
                confirmButton = {
                    Button(onClick = { showCameraWarning = false }) { Text("OK") }
                }
            )
        }

        editingEntry?.let { item ->
            LocationEditDialog(
                entry = item.entry,
                viewModel = viewModel,
                onDismiss = { editingEntry = null },
                onSave = { updatedEntry ->
                    viewModel.addTimetableEntry(updatedEntry, item.schedules)
                    editingEntry = null
                }
            )
        }

        entryToEditDetails?.let { item ->
            TimetableEntryDialog(
                item = item,
                viewModel = viewModel,
                onDismiss = { entryToEditDetails = null },
                onSave = { entry, schedules ->
                    viewModel.addTimetableEntry(entry, schedules)
                    entryToEditDetails = null
                }
            )
        }

        if (showAddDialog) {
            TimetableEntryDialog(
                viewModel = viewModel,
                onDismiss = { showAddDialog = false },
                onSave = { entry, schedules ->
                    viewModel.addTimetableEntry(entry, schedules)
                    showAddDialog = false
                }
            )
        }

        if (showBulkLocationDialog) {
            BulkLocationDialog(
                viewModel = viewModel,
                onDismiss = { showBulkLocationDialog = false },
                onSave = { lat, lon ->
                    viewModel.updateAllLocations(lat, lon)
                    showBulkLocationDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimetableItem(
    item: TimetableWithSchedules, 
    showFullName: Boolean,
    isSelected: Boolean,
    onEditLocation: () -> Unit, 
    onEditDetails: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = onEditDetails,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val displayName = if (showFullName && !item.entry.subjectFullName.isNullOrEmpty()) {
                    item.entry.subjectFullName
                } else {
                    item.entry.subjectName
                }
                Text(text = displayName!!, style = MaterialTheme.typography.titleMedium)
                
                // Sorting timings by day of the week
                val sortedSchedules = remember(item.schedules) {
                    item.schedules.sortedBy { it.dayOfWeek }
                }

                sortedSchedules.forEach { s ->
                    Text(text = "${getDayName(s.dayOfWeek)}: ${s.startTime} - ${s.endTime}", style = MaterialTheme.typography.bodySmall)
                }
                
                Text(text = "Threshold: ${item.entry.attendanceThresholdMinutes} mins", style = MaterialTheme.typography.bodySmall)
                
                if (!isSelected) {
                    TextButton(onClick = onEditLocation, contentPadding = PaddingValues(0.dp)) {
                        Text(
                            text = if (item.entry.latitude == 0.0) "Location not set (Tap to set)" else "Location: ${item.entry.latitude}, ${item.entry.longitude}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.entry.latitude == 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle, 
                    contentDescription = "Selected", 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableEntryDialog(
    item: TimetableWithSchedules? = null,
    viewModel: AttendanceViewModel,
    onDismiss: () -> Unit,
    onSave: (TimetableEntry, List<ClassSchedule>) -> Unit
) {
    var subjectCode by remember { mutableStateOf(item?.entry?.subjectName ?: "") }
    var subjectFullName by remember { mutableStateOf(item?.entry?.subjectFullName ?: "") }
    var threshold by remember { mutableStateOf(item?.entry?.attendanceThresholdMinutes?.toString() ?: "15") }
    
    // Sort schedules initially in the dialog
    var schedules by remember { 
        mutableStateOf(item?.schedules?.sortedBy { it.dayOfWeek } ?: emptyList()) 
    }
    
    var coords by remember { mutableStateOf(if (item != null && item.entry.latitude != 0.0) "${item.entry.latitude},${item.entry.longitude}" else "") }

    val scope = rememberCoroutineScope()
    var scheduleToEdit by remember { mutableStateOf<Pair<Int, ClassSchedule>?>(null) }
    var showAddSchedule by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add Subject" else "Edit Subject") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    OutlinedTextField(value = subjectCode, onValueChange = { subjectCode = it }, label = { Text("Subject Code") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = subjectFullName, onValueChange = { subjectFullName = it }, label = { Text("Full Name (Optional)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Timings (Sorted by Day)", style = MaterialTheme.typography.labelLarge)
                }
                
                items(schedules.size) { index ->
                    val s = schedules[index]
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scheduleToEdit = index to s }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("${getDayName(s.dayOfWeek).take(3)} ${s.startTime}-${s.endTime}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { schedules = schedules.filterIndexed { i, _ -> i != index } }) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = Color.Red)
                        }
                    }
                }
                
                item {
                    Button(onClick = { showAddSchedule = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add Timing")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = coords, 
                        onValueChange = { coords = it }, 
                        label = { Text("Coordinates (Lat,Long)") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    val loc = viewModel.getCurrentLocation()
                                    if (loc != null) coords = "${loc.latitude},${loc.longitude}"
                                }
                            }) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Use Current Location")
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = threshold, 
                        onValueChange = { threshold = it }, 
                        label = { Text("Late Threshold (min)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleaned = coords.replace(" ", "")
                val parts = cleaned.split(",")
                val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0

                onSave(
                    TimetableEntry(
                        id = item?.entry?.id ?: 0,
                        subjectName = subjectCode,
                        subjectFullName = subjectFullName.ifEmpty { null },
                        latitude = lat,
                        longitude = lon,
                        attendanceThresholdMinutes = threshold.toIntOrNull() ?: 15
                    ),
                    schedules.sortedBy { it.dayOfWeek } // Ensure sorted before saving
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showAddSchedule) {
        SchedulePickerDialog(
            onDismiss = { showAddSchedule = false },
            onConfirm = { newS ->
                schedules = (schedules + newS).sortedBy { it.dayOfWeek }
                showAddSchedule = false
            }
        )
    }

    scheduleToEdit?.let { (index, s) ->
        SchedulePickerDialog(
            initialSchedule = s,
            onDismiss = { scheduleToEdit = null },
            onConfirm = { updatedS ->
                val mutableList = schedules.toMutableList()
                mutableList[index] = updatedS
                schedules = mutableList.sortedBy { it.dayOfWeek }
                scheduleToEdit = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulePickerDialog(
    initialSchedule: ClassSchedule? = null,
    onDismiss: () -> Unit, 
    onConfirm: (ClassSchedule) -> Unit
) {
    var day by remember { mutableIntStateOf(initialSchedule?.dayOfWeek ?: 1) }
    
    val startStr = initialSchedule?.startTime ?: "09:00"
    val endStr = initialSchedule?.endTime ?: "10:00"
    
    var startH by remember { mutableIntStateOf(startStr.split(":")[0].toInt()) }
    var startM by remember { mutableIntStateOf(startStr.split(":")[1].toInt()) }
    var endH by remember { mutableIntStateOf(endStr.split(":")[0].toInt()) }
    var endM by remember { mutableIntStateOf(endStr.split(":")[1].toInt()) }

    val startState = rememberTimePickerState(initialHour = startH, initialMinute = startM)
    val endState = rememberTimePickerState(initialHour = endH, initialMinute = endM)
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSchedule == null) "Set Timing" else "Edit Timing") },
        text = {
            Column {
                Text("Day", style = MaterialTheme.typography.labelSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    (1..7).forEach { d ->
                        FilterChip(
                            selected = day == d,
                            onClick = { day = d },
                            label = { Text(getDayName(d).take(1)) },
                            modifier = Modifier.scale(0.9f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    OutlinedCard(onClick = { showStart = true }, modifier = Modifier.weight(1f).padding(4.dp)) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Start", style = MaterialTheme.typography.labelSmall)
                            Text(String.format(Locale.getDefault(), "%02d:%02d", startH, startM), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    OutlinedCard(onClick = { showEnd = true }, modifier = Modifier.weight(1f).padding(4.dp)) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("End", style = MaterialTheme.typography.labelSmall)
                            Text(String.format(Locale.getDefault(), "%02d:%02d", endH, endM), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(ClassSchedule(
                    timetableEntryId = initialSchedule?.timetableEntryId ?: 0,
                    dayOfWeek = day,
                    startTime = String.format(Locale.getDefault(), "%02d:%02d", startH, startM),
                    endTime = String.format(Locale.getDefault(), "%02d:%02d", endH, endM)
                ))
            }) { Text(if (initialSchedule == null) "Add" else "Update") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showStart) {
        TimePickerDialog(onDismissRequest = { showStart = false }, confirmButton = {
            TextButton(onClick = {
                startH = startState.hour
                startM = startState.minute
                showStart = false
            }) { Text("OK") }
        }, dismissButton = {}) {
            TimePicker(state = startState)
        }
    }
    if (showEnd) {
        TimePickerDialog(onDismissRequest = { showEnd = false }, confirmButton = {
            TextButton(onClick = {
                endH = endState.hour
                endM = endState.minute
                showEnd = false
            }) { Text("OK") }
        }, dismissButton = {}) {
            TimePicker(state = endState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.width(IntrinsicSize.Min).height(IntrinsicSize.Min).background(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), text = "Select Time", style = MaterialTheme.typography.labelMedium)
                content()
                Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.End) {
                    dismissButton()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
fun BulkLocationDialog(viewModel: AttendanceViewModel, onDismiss: () -> Unit, onSave: (Double, Double) -> Unit) {
    var coords by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Bulk Location") },
        text = {
            Column {
                Text("This updates all subjects.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = coords, 
                    onValueChange = { coords = it }, 
                    label = { Text("Coordinates") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                val loc = viewModel.getCurrentLocation()
                                if (loc != null) coords = "${loc.latitude},${loc.longitude}"
                            }
                        }) {
                            Icon(Icons.Default.MyLocation, contentDescription = null)
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleaned = coords.replace(" ", "")
                val parts = cleaned.split(",")
                onSave(parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0, parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0)
            }) { Text("Update All") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LocationEditDialog(entry: TimetableEntry, viewModel: AttendanceViewModel, onDismiss: () -> Unit, onSave: (TimetableEntry) -> Unit) {
    var coords by remember { mutableStateOf("${entry.latitude},${entry.longitude}") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Location") },
        text = {
            Column {
                Text(entry.subjectName, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = coords, 
                    onValueChange = { coords = it }, 
                    label = { Text("Coordinates") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                val loc = viewModel.getCurrentLocation()
                                if (loc != null) coords = "${loc.latitude},${loc.longitude}"
                            }
                        }) {
                            Icon(Icons.Default.MyLocation, contentDescription = null)
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleaned = coords.replace(" ", "")
                val parts = cleaned.split(",")
                onSave(entry.copy(
                    latitude = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0, 
                    longitude = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun getDayName(day: Int): String = when (day) {
    1 -> "Monday"
    2 -> "Tuesday"
    3 -> "Wednesday"
    4 -> "Thursday"
    5 -> "Friday"
    6 -> "Saturday"
    7 -> "Sunday"
    else -> "Unknown"
}
