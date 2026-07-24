package com.example.attendance.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.attendance.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val releaseNotes: String,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String
)

data class UpdateInfo(
    val versionName: String,
    val updateUrl: String,
    val releaseNotes: String
)

class UpdateManager(private val context: Context) {

    private val REPO_API_URL = "https://api.github.com/repos/LIGHTENINGCHROME/ASMARG/releases/latest"

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(REPO_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            
            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val release = Gson().fromJson(json, GitHubRelease::class.java)
                
                val latestVersionRaw = release.tagName.lowercase().replace("version", "").replace("v", "").trim()
                val currentVersionRaw = BuildConfig.VERSION_NAME.lowercase().replace("version", "").replace("v", "").trim()
                
                if (isNewerVersion(currentVersionRaw, latestVersionRaw)) {
                    val apkAsset = release.assets.find { it.name.equals("ASMARG.apk", ignoreCase = true) } 
                        ?: release.assets.find { it.name.endsWith(".apk") }
                    
                    if (apkAsset != null) {
                        return@withContext UpdateInfo(
                            versionName = release.tagName,
                            updateUrl = apkAsset.downloadUrl,
                            releaseNotes = release.releaseNotes
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "GitHub Update check failed", e)
        }
        null
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        if (current == latest) return false
        return try {
            val currParts = current.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
            val lateParts = latest.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
            for (i in 0 until maxOf(currParts.size, lateParts.size)) {
                val v1 = if (i < currParts.size) currParts[i] else 0
                val v2 = if (i < lateParts.size) lateParts[i] else 0
                if (v2 > v1) return true
                if (v2 < v1) return false
            }
            false
        } catch (e: Exception) {
            latest != current
        }
    }

    fun downloadAndInstall(url: String, onStartDownload: () -> Unit) {
        // 1. Delete old APK
        val updateFile = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ASMARG_update.apk")
        if (updateFile.exists()) updateFile.delete()

        // 2. Check for Installation Permission (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(context, "Please allow ASMARG to install updates", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return 
            }
        }

        onStartDownload()

        // 3. Start Download
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("ASMARG Update")
            .setDescription("Downloading latest release...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "ASMARG_update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        Log.d("UpdateManager", "Download started with ID: $downloadId")

        // 4. Listen for completion
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    Log.d("UpdateManager", "Download completed. Triggering install.")
                    Toast.makeText(receivedContext, "Download finished. Installing...", Toast.LENGTH_SHORT).show()
                    installApk(receivedContext)
                    receivedContext.unregisterReceiver(this)
                }
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(onComplete, filter)
        }
    }

    private fun installApk(currentContext: Context) {
        val updateFile = java.io.File(currentContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ASMARG_update.apk")
        
        if (updateFile.exists()) {
            try {
                val uri = FileProvider.getUriForFile(currentContext, "${currentContext.packageName}.fileprovider", updateFile)
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                currentContext.startActivity(installIntent)
                Log.d("UpdateManager", "Installation intent launched successfully")
            } catch (e: Exception) {
                Log.e("UpdateManager", "Error during installation trigger", e)
                Toast.makeText(currentContext, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.e("UpdateManager", "Update APK not found at: ${updateFile.absolutePath}")
            Toast.makeText(currentContext, "Update file missing after download", Toast.LENGTH_SHORT).show()
        }
    }

    fun cleanUpOldUpdate() {
        val updateFile = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ASMARG_update.apk")
        if (updateFile.exists()) {
            updateFile.delete()
            Log.d("UpdateManager", "Cleaned up old update APK")
        }
    }
}
