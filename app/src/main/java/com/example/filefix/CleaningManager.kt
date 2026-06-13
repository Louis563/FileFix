package com.example.filefix

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import java.io.File
import java.util.*

class CleaningManager(private val context: Context) {

    private val junkExtensions = setOf("tmp", "log", "cache", "temp", "apk_temp", "old", "bak")
    private val junkFolderNames = setOf("cache", "temp", "logs")

    data class AppJunkInfo(
        val appName: String,
        val packageName: String,
        val cacheSize: Long,
        val appSize: Long = 0L
    )

    /**
     * Obtiene una lista de apps y cuánto espacio de caché ocupa cada una.
     */
    fun getAppsCacheDetails(): List<AppJunkInfo> {
        val details = mutableListOf<AppJunkInfo>()
        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager ?: return details
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (app in apps) {
            try {
                val stats = storageStatsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, Process.myUserHandle())
                if (stats.cacheBytes > 1024 * 1024) { // Solo mostrar apps con más de 1MB de caché
                    details.add(AppJunkInfo(
                        appName = pm.getApplicationLabel(app).toString(),
                        packageName = app.packageName,
                        cacheSize = stats.cacheBytes
                    ))
                }
            } catch (e: Exception) { }
        }
        return details.sortedByDescending { it.cacheSize }
    }

    fun findSystemJunk(): List<File> {
        val junkList = mutableListOf<File>()
        val externalStorage = Environment.getExternalStorageDirectory()
        val dcim = File(externalStorage, "DCIM")
        val thumbnails = File(dcim, ".thumbnails")
        if (thumbnails.exists()) {
            addFilesRecursive(thumbnails, junkList)
        }
        scanForJunkInRoot(externalStorage, junkList)
        return junkList
    }

    private fun scanForJunkInRoot(root: File, junkList: MutableList<File>) {
        val files = root.listFiles() ?: return
        for (file in files) {
            val name = file.name.lowercase()
            if (file.isDirectory) {
                if (file.name == "Android") continue
                if (junkFolderNames.contains(name)) {
                    addFilesRecursive(file, junkList)
                } else if (!file.isHidden) {
                    scanForJunkInRoot(file, junkList)
                }
            } else {
                if (junkExtensions.contains(file.extension.lowercase()) || name.startsWith(".tmp")) {
                    junkList.add(file)
                }
            }
            if (junkList.size > 500) break
        }
    }

    private fun addFilesRecursive(directory: File, junkList: MutableList<File>) {
        val files = directory.listFiles() ?: return
        for (file in files) {
            junkList.add(file)
            if (file.isDirectory) addFilesRecursive(file, junkList)
        }
    }

    fun getUnusedAppsDetails(): List<AppJunkInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
        val pm = context.packageManager
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -30)
        val thirtyDaysAgo = cal.timeInMillis

        val stats = usageStatsManager.queryAndAggregateUsageStats(thirtyDaysAgo, System.currentTimeMillis())
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        return allApps.filter { app ->
            val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            val lastUsed = stats[app.packageName]?.lastTimeUsed ?: 0L
            !isSystemApp && launchIntent != null && (lastUsed < thirtyDaysAgo)
        }.map { app ->
            AppJunkInfo(
                appName = pm.getApplicationLabel(app).toString(),
                packageName = app.packageName,
                cacheSize = 0L,
                appSize = File(app.sourceDir).length()
            )
        }.sortedByDescending { it.appSize }
    }

    fun deleteFiles(files: List<File>): Long {
        var totalDeleted = 0L
        val sorted = files.sortedByDescending { it.absolutePath.length }
        for (file in sorted) {
            val size = if (file.isDirectory) 0L else file.length()
            if (file.delete()) totalDeleted += size
        }
        return totalDeleted
    }
}