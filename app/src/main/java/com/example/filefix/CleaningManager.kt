package com.example.filefix

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import com.example.filefix.model.AppJunkInfo
import java.io.File
import java.util.*

class CleaningManager(private val context: Context) {

    private val junkExtensions = setOf("tmp", "log", "cache", "temp", "apk_temp", "old", "bak", "thumbdata")
    private val junkFolderNames = setOf("cache", "temp", "logs", ".thumbnails")

    private val protectedExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp",
        "mp4", "mkv", "mov", "avi", "3gp",
        "mp3", "wav", "ogg", "flac", "m4a",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt",
        "zip", "rar", "7z"
    )

    fun getAppsCacheDetails(): List<AppJunkInfo> {
        val details = mutableListOf<AppJunkInfo>()
        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager ?: return details
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (app in apps) {
            try {
                val stats = storageStatsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, Process.myUserHandle())
                if (stats.cacheBytes > 1024 * 1024) { 
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
        val junkFiles = mutableListOf<File>()
        val root = Environment.getExternalStorageDirectory()
        scanDirectory(root, junkFiles)
        return junkFiles
    }

    private fun scanDirectory(directory: File, junkList: MutableList<File>) {
        val files = directory.listFiles() ?: return
        
        for (file in files) {
            val name = file.name.lowercase()
            
            if (file.isDirectory) {
                if (file.name == "Android") continue
                if (junkFolderNames.contains(name)) {
                    cleanAllInside(file, junkList)
                    junkList.add(file)
                } else {
                    scanDirectory(file, junkList)
                }
            } else {
                if (isJunkFile(file)) {
                    junkList.add(file)
                }
            }
            if (junkList.size > 2000) break
        }
    }

    private fun cleanAllInside(directory: File, junkList: MutableList<File>) {
        val files = directory.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                cleanAllInside(file, junkList)
                junkList.add(file)
            } else {
                junkList.add(file)
            }
        }
    }

    private fun isJunkFile(file: File): Boolean {
        val name = file.name.lowercase()
        val extension = file.extension.lowercase()
        
        if (name == ".nomedia") return false
        if (protectedExtensions.contains(extension)) return false
        if (name.startsWith(".thumbdata") || extension.contains("thumbdata")) return true
        if (junkExtensions.contains(extension)) return true
        if (name.startsWith(".") && file.length() < 2048) return true

        return false
    }

    fun getUnusedAppsDetails(): List<AppJunkInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return emptyList()
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
        var totalDeletedSpace = 0L
        val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.absolutePath.length })).reversed()
        
        for (file in sortedFiles) {
            if (!file.isDirectory) {
                val size = file.length()
                if (file.delete()) {
                    totalDeletedSpace += size
                }
            } else {
                file.delete()
            }
        }
        return totalDeletedSpace
    }

    fun boostRam(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(0)
        var count = 0

        for (packageInfo in packages) {
            val isSystemApp = (packageInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!isSystemApp && packageInfo.packageName != context.packageName) {
                try {
                    activityManager.killBackgroundProcesses(packageInfo.packageName)
                    count++
                } catch (e: Exception) {}
            }
        }
        return count
    }
}