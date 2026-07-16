package com.example.aetherandroid

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(private val context: Context) {

    companion object {
        private const val TAG = "UpdateChecker"
        private const val GITHUB_API_BASE = "https://api.github.com/repos"
        private const val UPDATE_NOTIFICATION_CHANNEL = "aether_update_channel"
        private const val UPDATE_NOTIFICATION_ID = 100

        // ⚙️ YOU MUST UPDATE THESE:
        // Replace with your actual GitHub username and repository name
        private const val GITHUB_USER = "Elesarh"
        private const val GITHUB_REPO = "aether-android"
    }

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val downloadUrl: String?,
        val releaseNotes: String?
    )

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * Check for updates from GitHub Releases
     * Call from a coroutine scope
     */
    suspend fun checkForUpdate(): UpdateInfo {
        return withContext(Dispatchers.IO) {
            try {
                val currentVersionCode = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                } catch (e: Exception) {
                    1
                }

                val apiUrl = "$GITHUB_API_BASE/$GITHUB_USER/$GITHUB_REPO/releases/latest"
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "GitHub API returned ${connection.responseCode}")
                    return@withContext UpdateInfo(false, "", null, null)
                }

                val response = connection.inputStream.bufferedReader().readText()

                // Parse the JSON response
                val json = org.json.JSONObject(response)
                val tagName = json.optString("tag_name", "") ?: ""
                val body = json.optString("body", "") ?: ""
                val assets = json.optJSONArray("assets")

                // Parse version from tag (e.g., "v1.0.0" -> 10000, "v1.2.3" -> 10203)
                val remoteVersionCode = parseVersionCode(tagName)

                // Find APK asset
                var apkDownloadUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkDownloadUrl = asset.optString("browser_download_url", null)
                            break
                        }
                    }
                }

                val hasUpdate = remoteVersionCode > currentVersionCode

                Log.i(TAG, "Check result: current=$currentVersionCode, remote=$remoteVersionCode " +
                        "(tag=$tagName), hasUpdate=$hasUpdate")

                UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = tagName,
                    downloadUrl = apkDownloadUrl,
                    releaseNotes = body
                )

            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for update", e)
                UpdateInfo(false, "", null, null)
            }
        }
    }

    /**
     * Download the update APK and trigger installation
     */
    fun downloadAndInstallUpdate(downloadUrl: String, versionName: String) {
        try {
            // Request POST_NOTIFICATIONS permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
                }
            }

            val fileName = "aether-android-$versionName.apk"

            // Use DownloadManager for reliable download
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("Aether Android Update")
                setDescription("Downloading $versionName...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadId = downloadManager.enqueue(request)

            // Register receiver to know when download is complete
            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        installApk(context ?: return, fileName)
                        context.unregisterReceiver(this)
                    }
                }
            }

            context.registerReceiver(
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to download update", e)
            Toast.makeText(context, "Update download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun installApk(context: Context, fileName: String) {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )

        if (!file.exists()) {
            Log.e(TAG, "APK file not found: ${file.absolutePath}")
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(intent)
    }

    private fun parseVersionCode(tag: String): Int {
        // Parse "v1.2.3" -> 10203, "v1.0" -> 10000, "v1" -> 10000
        val cleaned = tag.trimStart('v', 'V')
        val parts = cleaned.split(".")
        var version = 0
        for ((index, part) in parts.withIndex()) {
            val num = part.filter { it.isDigit() }.toIntOrNull() ?: 0
            when (index) {
                0 -> version += num * 10000
                1 -> version += num * 100
                2 -> version += num
            }
        }
        return version
    }
}