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

    private val junkExtensions = setOf("tmp", "log", "cache", "temp", "apk_temp", "old", "bak", "logcat", "err")
    private val junkFolderNames = setOf("cache", "temp", "logs", "bugreports", "thumbnails")

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
        
        // 1. Carpetas de miniaturas y temporales comunes
        val commonJunkPaths = listOf(
            "DCIM/.thumbnails",
            "Pictures/.thumbnails",
            "Android/data/com.android.providers.media/cache",
            "Download/.temp",
            "Download/.tmp"
        )
        
        for (path in commonJunkPaths) {
            val folder = File(externalStorage, path)
            if (folder.exists()) {
                if (folder.isDirectory) addFilesRecursive(folder, junkList)
                else junkList.add(folder)
            }
        }

        // 2. Escaneo profundo de basura y carpetas vacías
        scanForJunkAndEmptyFolders(externalStorage, junkList)
        
        return junkList
    }

    private fun scanForJunkAndEmptyFolders(root: File, junkList: MutableList<File>) {
        val files = root.listFiles() ?: return
        if (files.isEmpty()) {
            // Es una carpeta vacía (y no es la raíz)
            if (root != Environment.getExternalStorageDirectory() && !root.name.startsWith(".")) {
                junkList.add(root)
            }
            return
        }

        for (file in files) {
            val name = file.name.lowercase()
            if (file.isDirectory) {
                // Evitar carpetas críticas del sistema y apps
                if (file.name == "Android" || file.name.startsWith(".")) continue
                
                if (junkFolderNames.contains(name)) {
                    addFilesRecursive(file, junkList)
                } else {
                    scanForJunkAndEmptyFolders(file, junkList)
                }
            } else {
                if (junkExtensions.contains(file.extension.lowercase()) || name.startsWith(".tmp")) {
                    junkList.add(file)
                }
            }
            if (junkList.size > 1000) break // Límite de seguridad
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

    /**
     * Intenta abrir el diálogo del sistema para borrar el caché de todas las apps.
     * Retorna un Intent que debe ser lanzado desde una Activity.
     */
    fun getGlobalCacheClearIntent(): android.content.Intent? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.content.Intent(StorageManager.ACTION_CLEAR_APP_CACHE)
        } else {
            null
        }
    }

    /**
     * Abre la configuración de una aplicación específica para que el usuario borre el caché manualmente.
     */
    fun openAppSettings(packageName: String) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Utiliza el truco de StorageManager.allocateBytes como fallback o para activar el recolector.
     */
    fun clearAppCaches(): Boolean {
        return try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return false
            
            // Usamos el UUID por defecto del almacenamiento interno
            val uuid = StorageManager.UUID_DEFAULT
            
            // Obtenemos cuántos bytes se pueden liberar (espacio libre + cachés borrables)
            val allocatableBytes = storageManager.getAllocatableBytes(uuid)
            
            android.util.Log.d("FileFix", "Allocatable bytes: $allocatableBytes")

            if (allocatableBytes > 0) {
                // Solicitamos liberar el 90% de lo asignable para forzar la limpieza de caché de apps
                val targetBytes = (allocatableBytes * 0.9).toLong()
                android.util.Log.d("FileFix", "Allocating bytes: $targetBytes")
                storageManager.allocateBytes(uuid, targetBytes)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("FileFix", "Error clearing cache: ${e.message}")
            false
        }
    }
}