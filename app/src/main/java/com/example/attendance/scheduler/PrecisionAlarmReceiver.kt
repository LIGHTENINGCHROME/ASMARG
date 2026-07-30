package com.example.attendance.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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
        
        Log.d("PrecisionAlarm", "Alarm fired for Subject $tId. App process state preserved.")

        if (tId != -1L) {
            // Bypass background restrictions by running as EXPEDITED work
            val workRequest = OneTimeWorkRequestBuilder<AttendanceWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(
                    "timetableId" to tId,
                    "scheduleId" to sId,
                    "isBackgroundTrigger" to true
                ))
                .addTag("precision_check_immediate")
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
