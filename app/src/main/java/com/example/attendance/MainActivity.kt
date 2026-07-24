package com.example.attendance

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.attendance.ui.screens.AttendanceScreen
import com.example.attendance.ui.screens.HolidayScreen
import com.example.attendance.ui.screens.TimetableScreen
import com.example.attendance.ui.screens.CalendarScreen
import com.example.attendance.ui.screens.settings.SettingsScreen
import com.example.attendance.ui.theme.AttendanceTheme
import com.example.attendance.ui.viewmodel.AttendanceViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttendanceTheme {
                PermissionHandler()
                MainScreen()
            }
        }
    }
}

@Composable
fun PermissionHandler() {
    val context = LocalContext.current
    
    val backgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val foregroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (locationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    LaunchedEffect(Unit) {
        val hasForeground = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasForeground || !hasCamera || !hasNotifications) {
            val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            foregroundLauncher.launch(permissions.toTypedArray())
        }

        // Battery Optimization Check (Android 6+)
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun MainScreen() {
    val viewModel: AttendanceViewModel = viewModel()
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsState()

    LaunchedEffect(Unit) {
        if (autoUpdateEnabled) {
            val updateInfo = viewModel.checkForUpdates()
            if (updateInfo != null) {
                viewModel.performUpdate(updateInfo.updateUrl) {}
            }
        }
    }
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(icon = { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("Timetable") }, selected = pagerState.currentPage == 0, onClick = { scope.launch { pagerState.animateScrollToPage(0) } })
                NavigationBarItem(icon = { Icon(Icons.Default.History, null) }, label = { Text("Attendance") }, selected = pagerState.currentPage == 1, onClick = { scope.launch { pagerState.animateScrollToPage(1) } })
                NavigationBarItem(icon = { Icon(Icons.Default.BeachAccess, null) }, label = { Text("Holidays") }, selected = pagerState.currentPage == 2, onClick = { scope.launch { pagerState.animateScrollToPage(2) } })
                NavigationBarItem(icon = { Icon(Icons.Default.EventNote, null) }, label = { Text("Calendar") }, selected = pagerState.currentPage == 3, onClick = { scope.launch { pagerState.animateScrollToPage(3) } })
                NavigationBarItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") }, selected = pagerState.currentPage == 4, onClick = { scope.launch { pagerState.animateScrollToPage(4) } })
            }
        }
    ) { innerPadding ->
        HorizontalPager(state = pagerState, modifier = Modifier.padding(innerPadding), beyondViewportPageCount = 0, userScrollEnabled = true) { page ->
            key(page) {
                when (page) {
                    0 -> TimetableScreen(viewModel)
                    1 -> AttendanceScreen(viewModel)
                    2 -> HolidayScreen(viewModel)
                    3 -> CalendarScreen(viewModel)
                    4 -> SettingsScreen(viewModel)
                }
            }
        }
    }
}
