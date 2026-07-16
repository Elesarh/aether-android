package com.example.aetherandroid

import android.content.Context
import java.io.File

/**
 * Manages the running Aether binary version.
 * Handles version comparison and automatic binary update.
 */
class VersionManager(private val context: Context) {

    companion object {
        private const val VERSION_FILE = "aether_version.txt"
        const val TAG = "VersionManager"
    }

    data class AppVersion(
        val versionCode: Int = 0,
        val versionName: String = "0.0.0"
    )

    /**
     * Get the currently installed Aether binary version
     */
    fun getInstalledVersion(): AppVersion? {
        val file = File(context.filesDir, VERSION_FILE)
        if (!file.exists()) return null

        return try {
            val content = file.readText().trim()
            val parts = content.split("|")
            if (parts.size >= 2) {
                AppVersion(
                    versionCode = parts[0].toIntOrNull() ?: 0,
                    versionName = parts[1]
                )
            } else {
                AppVersion(versionName = content)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save the currently installed version
     */
    fun saveInstalledVersion(version: AppVersion) {
        val file = File(context.filesDir, VERSION_FILE)
        file.writeText("${version.versionCode}|${version.versionName}")
    }

    /**
     * Get the app's own version
     */
    fun getAppVersion(): AppVersion {
        return try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            AppVersion(
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkg.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    pkg.versionCode
                },
                versionName = pkg.versionName ?: "1.0.0"
            )
        } catch (e: Exception) {
            AppVersion()
        }
    }
}