package com.example.attendance.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.attendance.worker.AttendanceWorker

class PrecisionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tId = intent.getLongExtra("timetableId", -1L)
        val sId = intent.getLongExtra("scheduleId", -1L)
        
        Log.d("PrecisionAlarm", "Alarm triggered for Subject $tId, Schedule $sId. Launching EXPEDITED worker.")

        if (tId != -1L) {
            val workRequest = OneTimeWorkRequestBuilder<AttendanceWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(
                    "timetableId" to tId,
                    "scheduleId" to sId
                ))
                .addTag("precision_check_immediate")
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
