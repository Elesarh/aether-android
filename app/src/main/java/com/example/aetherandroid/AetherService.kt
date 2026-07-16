package com.example.aetherandroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class AetherService : Service() {

    companion object {
        private const val CHANNEL_ID = "aether_service_channel"
        private const val NOTIFICATION_ID = 1
        const val TAG = "AetherService"
        private const val BASE_URL = "https://github.com/CluvexStudio/Aether/releases/latest/download"
    }

    private var process: Process? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Starting Aether...")
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            ensureAndRunAetherBinary()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aether Tunnel Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running Aether censorship circumvention tunnel"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aether Tunnel")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun ensureAndRunAetherBinary() {
        try {
            updateNotification("Checking binary...")

            val binaryFile = File(filesDir, "aether")

            if (!binaryFile.exists() || !binaryFile.canExecute()) {
                updateNotification("Downloading Aether binary...")
                downloadAndExtractBinary(binaryFile)
            }

            binaryFile.setExecutable(true)
            updateNotification("Starting Aether tunnel...")

            // Arguments matching aether.sh defaults
            val command = arrayOf(
                binaryFile.absolutePath,
                "--masque",
                "-4",
                "--scan", "turbo",
                "--noize", "firewall"
            )

            val processBuilder = ProcessBuilder(*command)
            processBuilder.redirectErrorStream(true)
            processBuilder.directory(filesDir)

            withContext(Dispatchers.IO) {
                process = processBuilder.start()

                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Log.d(TAG, "Aether: $line")
                }

                val exitCode = process!!.waitFor()
                Log.i(TAG, "Aether exited with code: $exitCode")
                updateNotification("Aether stopped. Code: $exitCode")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in Aether service", e)
            updateNotification("Error: ${e.message}")
        } finally {
            process = null
        }
    }

    private suspend fun downloadAndExtractBinary(targetFile: File) {
        val abi = detectAndroidAbi()
        val assetName = "aether-android-$abi.tar.gz"
        val downloadUrl = "$BASE_URL/$assetName"

        withContext(Dispatchers.IO) {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP error code: ${connection.responseCode}")
            }

            val tempFile = File(cacheDir, "aether_download.tar.gz")
            tempFile.outputStream().use { out ->
                connection.inputStream.use { input ->
                    input.copyTo(out)
                }
            }

            extractTarGz(tempFile, targetFile)
            tempFile.delete()
        }
    }

    private fun extractTarGz(sourceFile: File, targetFile: File) {
        FileInputStream(sourceFile).use { fis ->
            GZIPInputStream(fis).use { gzis ->
                val headerSize = 512
                val header = ByteArray(headerSize)
                val read = gzis.read(header)
                if (read != headerSize) {
                    throw IOException("Failed to read tar header")
                }

                // Parse file name (bytes 0-99)
                val fileName = String(header.copyOfRange(0, 100)).trim().split("\u0000")[0]
                // Parse size (bytes 124-135)
                val sizeOctal = String(header.copyOfRange(124, 136)).trim()
                val fileSize = java.lang.Long.parseLong(sizeOctal, 8).toInt()

                FileOutputStream(targetFile).use { fos ->
                    val buffer = ByteArray(8192)
                    var remaining = fileSize
                    while (remaining > 0) {
                        val chunk = minOf(buffer.size, remaining)
                        val bytesRead = gzis.read(buffer, 0, chunk)
                        if (bytesRead == -1) break
                        fos.write(buffer, 0, bytesRead)
                        remaining -= bytesRead
                    }
                }

                // Also extract to the fileName if it's not the target name
                if (fileName.isNotEmpty() && fileName != "aether") {
                    val altFile = File(filesDir, fileName)
                    if (targetFile.exists()) {
                        targetFile.renameTo(altFile)
                    }
                }
            }
        }
    }

    private fun detectAndroidAbi(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull()
        return when (abi) {
            "armeabi-v7a" -> "armv7"
            "arm64-v8a" -> "arm64"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> {
                Log.w(TAG, "Unknown ABI: $abi, defaulting to armv7")
                "armv7"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        process?.destroyForcibly()
        process = null
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}