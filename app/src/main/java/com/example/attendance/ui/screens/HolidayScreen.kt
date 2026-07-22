package com.example.attendance.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.attendance.data.Holiday
import com.example.attendance.ui.viewmodel.AttendanceViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidayScreen(viewModel: AttendanceViewModel) {
    val holidays by viewModel.allHolidays.collectAsState(initial = emptyList())
    val nonWeekendHolidays = holidays.filter { !it.isWeekend }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingHoliday by remember { mutableStateOf<Holiday?>(null) }
    var showUploadOptions by remember { mutableStateOf(false) }
    var showCameraWarning by remember { mutableStateOf(false) }
    
    var satHoliday by remember { mutableStateOf(false) }
    var sunHoliday by remember { mutableStateOf(false) }

    // Multi-select state
    val selectedIds = remember { mutableStateListOf<Long>() }
    val isSelectionMode by remember { derivedStateOf { selectedIds.isNotEmpty() } }
    
    val context = LocalContext.current
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(holidays) {
        satHoliday = holidays.any { it.isWeekend && it.name == "Saturday" }
        sunHoliday = holidays.any { it.isWeekend && it.name == "Sunday" }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.processHolidayDocument(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempPhotoUri?.let { viewModel.processHolidayDocument(it) }
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
            val file = File(context.cacheDir, "images/temp_holiday_${System.currentTimeMillis()}.jpg")
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
                    if (isSelectionMode) Text("${selectedIds.size} Selected") else Text("Holidays")
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            val toDelete = holidays.filter { it.id in selectedIds }
                            viewModel.deleteHolidays(toDelete)
                            selectedIds.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                        IconButton(onClick = { selectedIds.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            Text("Sat Off", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = satHoliday, onCheckedChange = { 
                                satHoliday = it
                                viewModel.setWeekendHolidays(it, sunHoliday)
                            }, modifier = Modifier.scale(0.6f))
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            Text("Sun Off", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = sunHoliday, onCheckedChange = { 
                                sunHoliday = it
                                viewModel.setWeekendHolidays(satHoliday, it)
                            }, modifier = Modifier.scale(0.6f))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(onClick = { showUploadOptions = true }) {
                        Icon(Icons.Default.Upload, contentDescription = "Scan Holidays")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FloatingActionButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Holiday")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            items(nonWeekendHolidays.sortedBy { it.date }, key = { it.id }) { holiday ->
                val isSelected = selectedIds.contains(holiday.id)
                HolidayItem(
                    holiday = holiday, 
                    isSelected = isSelected,
                    onConfirm = { viewModel.confirmHoliday(holiday) },
                    onDelete = { viewModel.deleteHoliday(holiday) },
                    onEdit = { 
                        if (isSelectionMode) {
                            if (isSelected) selectedIds.remove(holiday.id) else selectedIds.add(holiday.id)
                        } else {
                            editingHoliday = holiday 
                        }
                    },
                    onLongClick = {
                        if (!isSelectionMode) {
                            selectedIds.add(holiday.id)
                        }
                    }
                )
            }
        }

        if (showUploadOptions) {
            AlertDialog(
                onDismissRequest = { showUploadOptions = false },
                title = { Text("Scan Holiday List") },
                text = { Text("Choose an option to upload your holiday list.") },
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
                text = { Text("The app needs camera access to take photos of your holiday list. Please enable it in system settings.") },
                confirmButton = {
                    Button(onClick = { showCameraWarning = false }) { Text("OK") }
                }
            )
        }

        if (showAddDialog) {
            HolidayEditDialog(
                onDismiss = { showAddDialog = false },
                onSave = { name, date ->
                    viewModel.addHoliday(Holiday(name = name, date = date, isConfirmed = true))
                    showAddDialog = false
                }
            )
        }

        editingHoliday?.let { holiday ->
            HolidayEditDialog(
                holiday = holiday,
                onDismiss = { editingHoliday = null },
                onSave = { name, date ->
                    viewModel.addHoliday(holiday.copy(name = name, date = date))
                    editingHoliday = null
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HolidayItem(
    holiday: Holiday, 
    isSelected: Boolean,
    onConfirm: () -> Unit, 
    onDelete: () -> Unit, 
    onEdit: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = onEdit,
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
                Text(text = holiday.name, style = MaterialTheme.typography.titleMedium)
                Text(text = holiday.date, style = MaterialTheme.typography.bodySmall)
            }
            if (!isSelected) {
                Row {
                    if (!holiday.isConfirmed) {
                        IconButton(onClick = onConfirm) {
                            Icon(Icons.Default.Check, contentDescription = "Confirm", tint = Color.Gray)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Confirmed", tint = Color.Green, modifier = Modifier.padding(12.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            } else {
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
fun HolidayEditDialog(holiday: Holiday? = null, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(holiday?.name ?: "") }
    
    val initialDate = holiday?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    var dateStr by remember { mutableStateOf(initialDate) }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(initialDate)?.time
    )
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (holiday == null) "Add Holiday" else "Edit Holiday") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Holiday Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Date", style = MaterialTheme.typography.labelSmall)
                            Text(dateStr, style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, dateStr) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
