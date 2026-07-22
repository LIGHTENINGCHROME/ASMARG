package com.example.attendance.ui.viewmodel

import android.app.Application
import android.content.Context
import android.location.Location
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.attendance.data.*
import com.example.attendance.data.backup.BackupManager
import com.example.attendance.ocr.HolidayParser
import com.example.attendance.ocr.OcrManager
import com.example.attendance.ocr.TimetableParser
import com.example.attendance.scheduler.AttendanceScheduler
import com.example.attendance.worker.AttendanceWorker
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class SubjectStats(
    val presentCount: Int,
    val totalCount: Int,
    val suspendedCount: Int,
    val percentage: Float,
    val effectivePercentage: Float
)

data class AttendanceConflict(
    val date: String,
    val subjectName: String,
    val startTime: String,
    val endTime: String,
    val scheduleId: Long,
    val conflictingSubjectName: String,
    val currentStatus: String,
    val newStatus: String
)

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AttendanceRepository
    private val ocrManager: OcrManager = OcrManager(application)
    private val timetableParser: TimetableParser = TimetableParser()
    private val holidayParser: HolidayParser = HolidayParser()
    private val scheduler: AttendanceScheduler = AttendanceScheduler(application)
    private val backupManager: BackupManager = BackupManager(application)
    private val prefs = application.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)

    // Using StateFlow with 5s timeout to keep data alive during quick tab switches
    val allTimetableEntries: StateFlow<List<TimetableWithSchedules>>
    val allHolidays: StateFlow<List<Holiday>>
    val allAttendanceRecords: StateFlow<List<AttendanceRecord>>
    val subjectStats: StateFlow<Map<Long, SubjectStats>>
    
    private val _defaultThreshold = MutableStateFlow(prefs.getInt("default_threshold", 15))
    val defaultThreshold: StateFlow<Int> = _defaultThreshold.asStateFlow()

    init {
        val attendanceDao = AttendanceDatabase.getDatabase(application).attendanceDao()
        repository = AttendanceRepository(attendanceDao)
        
        allTimetableEntries = repository.allTimetableEntries
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
        allHolidays = repository.allHolidays
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            
        allAttendanceRecords = repository.allAttendanceRecords
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Offload stats computation to Default dispatcher to keep Main thread free
        subjectStats = combine(allTimetableEntries, allAttendanceRecords) { entries, records ->
            entries.associate { item ->
                val sRecords = records.filter { it.timetableId == item.entry.id }
                val activeRecords = sRecords.filter { it.status != "SUSPENDED" }
                val present = activeRecords.count { it.status == "PRESENT" }
                val heldTotal = activeRecords.size
                val suspended = sRecords.count { it.status == "SUSPENDED" }
                val effPresent = present + suspended
                val effTotal = heldTotal + suspended

                item.entry.id to SubjectStats(
                    presentCount = present,
                    totalCount = heldTotal,
                    suspendedCount = suspended,
                    percentage = if (heldTotal > 0) present.toFloat() / heldTotal.toFloat() else 0f,
                    effectivePercentage = if (effTotal > 0) effPresent.toFloat() / effTotal.toFloat() else 0f
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    }

    fun updateDefaultThreshold(newThreshold: Int) {
        prefs.edit().putInt("default_threshold", newThreshold).apply()
        _defaultThreshold.value = newThreshold
    }

    fun applyThresholdToSubjects(subjectIds: List<Long>, newThreshold: Int) = viewModelScope.launch(Dispatchers.IO) {
        val attendanceDao = AttendanceDatabase.getDatabase(getApplication()).attendanceDao()
        val allEntries = attendanceDao.getAllTimetableEntriesOnce()
        
        allEntries.filter { it.entry.id in subjectIds }.forEach { item ->
            repository.insertTimetableWithSchedules(
                item.entry.copy(attendanceThresholdMinutes = newThreshold),
                item.schedules
            )
        }
        updateSchedule()
    }

    private fun updateSchedule() = viewModelScope.launch(Dispatchers.Default) {
        val attendanceDao = AttendanceDatabase.getDatabase(getApplication()).attendanceDao()
        scheduler.scheduleAllChecks(attendanceDao)
    }

    fun addTimetableEntry(entry: TimetableEntry, schedules: List<ClassSchedule>) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertTimetableWithSchedules(entry, schedules)
        updateSchedule()
    }

    fun deleteTimetableEntry(entry: TimetableEntry) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteTimetableEntry(entry)
        updateSchedule()
    }

    fun deleteTimetableEntries(entries: List<TimetableEntry>) = viewModelScope.launch(Dispatchers.IO) {
        entries.forEach { repository.deleteTimetableEntry(it) }
        updateSchedule()
    }

    fun processTimetableDocument(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val extension = context().contentResolver.getType(uri)
        val curDefault = _defaultThreshold.value
        
        if (extension == "application/pdf") {
            val bitmaps = ocrManager.pdfToBitmaps(uri)
            bitmaps.forEach { bitmap ->
                val texts = ocrManager.extractStructuredTextFromBitmap(bitmap)
                val results = timetableParser.parseGrid(texts)
                results.forEach { 
                    repository.insertTimetableWithSchedules(it.entry.copy(attendanceThresholdMinutes = curDefault), it.schedules) 
                }
                bitmap.recycle()
            }
        } else {
            val texts = ocrManager.extractStructuredTextFromUri(uri)
            val results = timetableParser.parseGrid(texts)
            results.forEach { 
                repository.insertTimetableWithSchedules(it.entry.copy(attendanceThresholdMinutes = curDefault), it.schedules) 
            }
        }
        updateSchedule()
    }

    private fun context() = getApplication<Application>()

    fun processHolidayDocument(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val extension = context().contentResolver.getType(uri)
        if (extension == "application/pdf") {
            val bitmaps = ocrManager.pdfToBitmaps(uri)
            bitmaps.forEach { bitmap ->
                val texts = ocrManager.extractStructuredTextFromBitmap(bitmap)
                val holidays = holidayParser.parseGrid(texts)
                holidays.forEach { repository.insertHoliday(it) }
                bitmap.recycle()
            }
        } else {
            val texts = ocrManager.extractStructuredTextFromUri(uri)
            val holidays = holidayParser.parseGrid(texts)
            holidays.forEach { repository.insertHoliday(it) }
        }
    }

    fun confirmHoliday(holiday: Holiday) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateHoliday(holiday.copy(isConfirmed = true))
    }

    fun addHoliday(holiday: Holiday) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertHoliday(holiday)
    }

    fun deleteHoliday(holiday: Holiday) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteHoliday(holiday)
    }

    fun deleteHolidays(holidays: List<Holiday>) = viewModelScope.launch(Dispatchers.IO) {
        holidays.forEach { repository.deleteHoliday(it) }
    }

    fun setWeekendHolidays(saturday: Boolean, sunday: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        repository.syncWeekendHolidays(saturday, sunday)
    }

    fun triggerManualAttendanceCheck() {
        val workRequest = OneTimeWorkRequestBuilder<AttendanceWorker>().build()
        WorkManager.getInstance(getApplication()).enqueue(workRequest)
    }

    suspend fun getCurrentLocation(): Location? {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplication<Application>())
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: SecurityException) {
            null
        }
    }

    fun cleanUpDuplicates() = viewModelScope.launch(Dispatchers.IO) {
        val attendanceDao = AttendanceDatabase.getDatabase(getApplication()).attendanceDao()
        val allEntries = attendanceDao.getAllTimetableEntriesOnce()
        val grouped = allEntries.groupBy { it.entry.subjectName.uppercase().trim() }
        grouped.forEach { (name, items) ->
            if (items.size > 1) {
                val primary = items[0]
                val allSchedules = items.flatMap { it.schedules }
                repository.insertTimetableWithSchedules(primary.entry.copy(subjectName = name), allSchedules)
                for (i in 1 until items.size) {
                    repository.deleteTimetableEntry(items[i].entry)
                }
            }
        }
        updateSchedule()
    }

    fun updateAllLocations(latitude: Double, longitude: Double) = viewModelScope.launch(Dispatchers.IO) {
        val attendanceDao = AttendanceDatabase.getDatabase(getApplication()).attendanceDao()
        val allEntries = attendanceDao.getAllTimetableEntriesOnce()
        allEntries.forEach { item ->
            repository.insertTimetableWithSchedules(
                item.entry.copy(latitude = latitude, longitude = longitude),
                item.schedules
            )
        }
        updateSchedule()
    }

    fun updateAttendanceStatus(record: AttendanceRecord, newStatus: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertAttendanceRecord(record.copy(status = newStatus))
    }

    fun deleteAttendanceRecord(record: AttendanceRecord) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAttendanceRecord(record)
    }

    fun toggleAttendanceSuspended(record: AttendanceRecord) = viewModelScope.launch(Dispatchers.IO) {
        val newStatus = if (record.status == "SUSPENDED") "ABSENT" else "SUSPENDED"
        repository.insertAttendanceRecord(record.copy(status = newStatus))
    }

    suspend fun exportBackup(uri: Uri): Boolean {
        return backupManager.exportData(uri)
    }

    suspend fun importBackup(uri: Uri): Boolean {
        val success = backupManager.importData(uri)
        if (success) {
            updateSchedule()
        }
        return success
    }

    suspend fun checkBulkAddConflicts(subjectId: Long, startDate: String, endDate: String, newStatus: String): List<AttendanceConflict> = withContext(Dispatchers.IO) {
        val database = AttendanceDatabase.getDatabase(getApplication())
        val dao = database.attendanceDao()
        val allSubjects = dao.getAllTimetableEntriesOnce()
        val targetSubject = allSubjects.find { it.entry.id == subjectId } ?: return@withContext emptyList()
        val allRecords = dao.getAllAttendanceRecordsOnce()
        val allSchedules = allSubjects.flatMap { it.schedules }
        
        val conflicts = mutableListOf<AttendanceConflict>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        val start = sdf.parse(startDate) ?: return@withContext emptyList()
        val end = sdf.parse(endDate) ?: return@withContext emptyList()
        val cal = Calendar.getInstance()
        cal.time = start
        
        while (!cal.time.after(end)) {
            val dateStr = sdf.format(cal.time)
            val currentDay = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3; 
                Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6; Calendar.SUNDAY -> 7; else -> 1
            }
            
            val dailySchedules = targetSubject.schedules.filter { it.dayOfWeek == currentDay }
            
            dailySchedules.forEach { ts ->
                val tsStart = timeSdf.parse(ts.startTime)!!
                val tsEnd = timeSdf.parse(ts.endTime)!!
                
                val overlappingRecord = allRecords.find { r ->
                    if (r.date != dateStr) return@find false
                    val rs = allSchedules.find { it.scheduleId == r.scheduleId } ?: return@find false
                    val rsStart = timeSdf.parse(rs.startTime)!!
                    val rsEnd = timeSdf.parse(rs.endTime)!!
                    tsStart.before(rsEnd) && rsStart.before(tsEnd)
                }
                
                if (overlappingRecord != null) {
                    val conflictingSub = allSubjects.find { it.entry.id == overlappingRecord.timetableId }?.entry?.subjectName ?: "Unknown"
                    conflicts.add(AttendanceConflict(
                        date = dateStr,
                        subjectName = targetSubject.entry.subjectName,
                        startTime = ts.startTime,
                        endTime = ts.endTime,
                        scheduleId = ts.scheduleId,
                        conflictingSubjectName = conflictingSub,
                        currentStatus = overlappingRecord.status,
                        newStatus = newStatus
                    ))
                }
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return@withContext conflicts
    }

    fun bulkAddAttendanceConfirmed(subjectId: Long, startDate: String, endDate: String, status: String, updateIds: Set<String>, hasConflicts: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val database = AttendanceDatabase.getDatabase(getApplication())
        val dao = database.attendanceDao()
        val allSubjects = dao.getAllTimetableEntriesOnce()
        val subject = allSubjects.find { it.entry.id == subjectId } ?: return@launch
        val allSchedules = allSubjects.flatMap { it.schedules }
        val allRecords = dao.getAllAttendanceRecordsOnce()
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val start = sdf.parse(startDate)!!
        val end = sdf.parse(endDate)!!
        val cal = Calendar.getInstance()
        cal.time = start
        
        while (!cal.time.after(end)) {
            val dateStr = sdf.format(cal.time)
            val holiday = dao.getHolidayForDate(dateStr)
            if (holiday == null || !holiday.isConfirmed) {
                val currentDay = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3; 
                    Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6; Calendar.SUNDAY -> 7; else -> 1
                }
                
                subject.schedules.filter { it.dayOfWeek == currentDay }.forEach { s ->
                    val dateScheduleKey = "${dateStr}_${s.scheduleId}"
                    
                    val tsStart = timeSdf.parse(s.startTime)!!
                    val tsEnd = timeSdf.parse(s.endTime)!!
                    val isConflict = allRecords.any { r ->
                        if (r.date != dateStr) return@any false
                        val rs = allSchedules.find { it.scheduleId == r.scheduleId } ?: return@any false
                        val rsStart = timeSdf.parse(rs.startTime)!!
                        val rsEnd = timeSdf.parse(rs.endTime)!!
                        tsStart.before(rsEnd) && rsStart.before(tsEnd)
                    }

                    if (isConflict) {
                        if (updateIds.contains(dateScheduleKey)) {
                            val existing = dao.getAttendanceForScheduleAndDate(subjectId, s.scheduleId, dateStr)
                            if (existing != null) dao.deleteAttendanceRecord(existing)
                            dao.insertAttendanceRecord(AttendanceRecord(timetableId = subjectId, scheduleId = s.scheduleId, date = dateStr, status = status))
                        }
                    } else {
                        val existing = dao.getAttendanceForScheduleAndDate(subjectId, s.scheduleId, dateStr)
                        if (existing == null) {
                            dao.insertAttendanceRecord(AttendanceRecord(timetableId = subjectId, scheduleId = s.scheduleId, date = dateStr, status = status))
                        }
                    }
                }
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}
