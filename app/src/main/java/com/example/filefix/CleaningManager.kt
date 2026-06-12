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

    /**
     * Identifica el caché real de las aplicaciones instaladas usando APIs de sistema.
     */
    fun getAppsCacheSize(): Long {
        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager ?: return 0L
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var totalCache = 0L

        for (app in apps) {
            try {
                // Solo apps de usuario o que no sean del sistema críticas
                val stats = storageStatsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, Process.myUserHandle())
                totalCache += stats.cacheBytes
            } catch (e: Exception) { }
        }
        return totalCache
    }

    /**
     * Busca basura del sistema (thumbnails, archivos temporales, .log, etc.)
     */
    fun findSystemJunk(): List<File> {
        val junkList = mutableListOf<File>()
        val externalStorage = Environment.getExternalStorageDirectory()
        
        // 1. Thumbnails (Candidatos 100% basura)
        val dcim = File(externalStorage, "DCIM")
        val thumbnails = File(dcim, ".thumbnails")
        if (thumbnails.exists()) {
            junkList.add(thumbnails)
            addFilesRecursive(thumbnails, junkList)
        }

        // 2. Escaneo de archivos temporales en carpetas comunes
        scanForJunkInRoot(externalStorage, junkList)

        return junkList
    }

    private fun scanForJunkInRoot(root: File, junkList: MutableList<File>) {
        val files = root.listFiles() ?: return
        for (file in files) {
            val name = file.name.lowercase()
            if (file.isDirectory) {
                if (file.name == "Android") continue // No tocar carpeta Android por seguridad
                if (junkFolderNames.contains(name)) {
                    junkList.add(file)
                    addFilesRecursive(file, junkList)
                } else if (!file.isHidden) {
                    // Escaneo limitado para no ralentizar la demo
                    scanForJunkInRoot(file, junkList)
                }
            } else {
                if (junkExtensions.contains(file.extension.lowercase()) || name.startsWith(".tmp")) {
                    junkList.add(file)
                }
            }
            if (junkList.size > 200) break // Límite para la demo
        }
    }

    private fun addFilesRecursive(directory: File, junkList: MutableList<File>) {
        val files = directory.listFiles() ?: return
        for (file in files) {
            junkList.add(file)
            if (file.isDirectory) addFilesRecursive(file, junkList)
        }
    }

    /**
     * Identifica aplicaciones no usadas en los últimos 30 días.
     */
    fun getUnusedApps(): List<ApplicationInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
        val pm = context.packageManager
        
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -30)
        val thirtyDaysAgo = cal.timeInMillis

        // Consultamos estadísticas del último mes
        val stats = usageStatsManager.queryAndAggregateUsageStats(thirtyDaysAgo, System.currentTimeMillis())
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        return allApps.filter { app ->
            val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            val lastUsed = stats[app.packageName]?.lastTimeUsed ?: 0L
            
            // Sugerir si: no es del sistema, tiene icono de inicio y no se usó en 30 días
            !isSystemApp && launchIntent != null && (lastUsed < thirtyDaysAgo)
        }.sortedBy { stats[it.packageName]?.lastTimeUsed ?: 0L }
    }

    fun deleteFiles(files: List<File>): Long {
        var totalDeleted = 0L
        // Ordenamos para borrar archivos antes que carpetas
        val sorted = files.sortedByDescending { it.absolutePath.length }
        for (file in sorted) {
            val size = if (file.isDirectory) 0L else file.length()
            if (file.delete()) totalDeleted += size
        }
        return totalDeleted
    }
}
