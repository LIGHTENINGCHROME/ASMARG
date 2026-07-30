package com.example.attendance.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.example.attendance.BuildConfig
import com.example.attendance.data.TimetableWithSchedules
import com.example.attendance.ui.viewmodel.AttendanceViewModel
import com.example.attendance.utils.UpdateInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AttendanceViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val defaultThreshold by viewModel.defaultThreshold.collectAsState()
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsState()
    val subjects by viewModel.allTimetableEntries.collectAsState()

    var showThresholdDialog by remember { mutableStateOf(false) }
    var showBulkUpdateDialog by remember { mutableStateOf(false) }
    var pendingThreshold by remember { mutableIntStateOf(defaultThreshold) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    var headingTapCount by remember { mutableIntStateOf(0) }
    val showPreReleaseButton by remember { derivedStateOf { headingTapCount >= 5 } }

    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isOptimized by remember { mutableStateOf(!pm.isIgnoringBatteryOptimizations(context.packageName)) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val success = viewModel.exportBackup(it)
                Toast.makeText(context, if (success) "Backup Saved!" else "Backup Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val success = viewModel.importBackup(it)
                Toast.makeText(context, if (success) "Restore Successful!" else "Restore Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(text = "Settings & Update", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (isOptimized) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isOptimized) Icons.Default.Warning else Icons.Default.CheckCircle, null, tint = if (isOptimized) Color.Red else Color.Green)
                        Spacer(Modifier.width(8.dp))
                        Text(text = "Automation Status", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = if (isOptimized) "Background tracking may be blocked by your phone's battery saver." else "Background tracking is fully authorized.", style = MaterialTheme.typography.bodySmall)
                    if (isOptimized) {
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }
                            context.startActivity(intent)
                        }, modifier = Modifier.padding(top = 12.dp)) { Text("Fix Automation Issues") }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Software Update (GitHub)", 
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            headingTapCount++
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(text = "Auto-Check for Updates", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "Check GitHub on app launch", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoUpdateEnabled, onCheckedChange = { viewModel.toggleAutoUpdate(it) }, modifier = Modifier.scale(0.8f))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(text = "Current Version: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (isCheckingUpdate) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Connecting to GitHub...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (isDownloading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            Text("Downloading...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Button(onClick = {
                            scope.launch {
                                isCheckingUpdate = true
                                val updateInfo = viewModel.checkForUpdates(includePreRelease = false)
                                isCheckingUpdate = false
                                if (updateInfo != null) {
                                    viewModel.performUpdate(updateInfo.updateUrl) { isDownloading = true }
                                } else {
                                    Toast.makeText(context, "ASMARG is up to date!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Update, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Check & Update Now")
                        }

                        if (showPreReleaseButton) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                scope.launch {
                                    isCheckingUpdate = true
                                    val updateInfo = viewModel.checkForUpdates(includePreRelease = true)
                                    isCheckingUpdate = false
                                    if (updateInfo != null) {
                                        viewModel.performUpdate(updateInfo.updateUrl) { isDownloading = true }
                                    } else {
                                        Toast.makeText(context, "No pre-releases found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)) {
                                Icon(Icons.Default.BugReport, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Check for Pre-Release")
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Attendance Logic", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(text = "Default Late Threshold", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "$defaultThreshold minutes", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { pendingThreshold = defaultThreshold; showThresholdDialog = true }) { Icon(Icons.Default.Edit, null) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Data Management", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { exportLauncher.launch("attendance_backup.json") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FileUpload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Backup (Export to File)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FileDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Restore (Import from File)")
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showThresholdDialog) {
        AlertDialog(onDismissRequest = { showThresholdDialog = false }, title = { Text("Set Default Threshold") }, text = {
            Column {
                Text("This threshold will be used for all newly added subjects.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = pendingThreshold.toString(), onValueChange = { pendingThreshold = it.toIntOrNull() ?: 0 }, label = { Text("Minutes") }, modifier = Modifier.fillMaxWidth())
            }
        }, confirmButton = {
            Button(onClick = {
                viewModel.updateDefaultThreshold(pendingThreshold)
                showThresholdDialog = false
                if (subjects.isNotEmpty()) showBulkUpdateDialog = true
            }) { Text("Save") }
        }, dismissButton = { TextButton(onClick = { showThresholdDialog = false }) { Text("Cancel") } })
    }

    if (showBulkUpdateDialog) {
        val selectedSubjectIds = remember { mutableStateListOf<Long>() }
        AlertDialog(onDismissRequest = { showBulkUpdateDialog = false }, title = { Text("Apply to Existing Subjects?") }, text = {
            Column {
                Text("Select which subjects should use the new $pendingThreshold min threshold:")
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(subjects) { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = selectedSubjectIds.contains(item.entry.id), onCheckedChange = { checked -> if (checked) selectedSubjectIds.add(item.entry.id) else selectedSubjectIds.remove(item.entry.id) })
                            Text(item.entry.subjectName)
                        }
                    }
                }
            }
        }, confirmButton = {
            Button(onClick = { viewModel.applyThresholdToSubjects(selectedSubjectIds.toList(), pendingThreshold); showBulkUpdateDialog = false }) { Text("Update Selected") }
        }, dismissButton = { TextButton(onClick = { showBulkUpdateDialog = false }) { Text("Skip") } })
    }
}
