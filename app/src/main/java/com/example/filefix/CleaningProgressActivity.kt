package com.example.filefix

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.filefix.model.FileItem
import com.example.filefix.model.JunkGroup
import kotlinx.coroutines.*
import android.app.ActivityManager
import android.content.Context
import java.io.File

class CleaningProgressActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvPercentage: TextView
    private lateinit var cleaningManager: CleaningManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cleaning_progress)

        cleaningManager = CleaningManager(this)
        tvStatus = findViewById(R.id.tvProgressStatus)
        tvPercentage = findViewById(R.id.tvProgressPercentage)

        startRealScanning()
    }

    private fun getAvailableRam(): Long {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem
    }

    private fun startRealScanning() {
        val statuses = arrayOf(
            "Analizando caché de aplicaciones...",
            "Buscando archivos temporales...",
            "Identificando carpetas vacías...",
            "Liberando memoria RAM...",
            "Calculando espacio recuperable...",
            "Finalizando escaneo..."
        )

        lifecycleScope.launch {
            val ramBefore = getAvailableRam()

            val scanJob = async(Dispatchers.IO) {
                cleaningManager.boostRam()
                
                val groups = mutableListOf<JunkGroup>()
                
                // 1. Caché de apps
                val cacheDetails = cleaningManager.getAppsCacheDetails()
                val totalCacheSize = cacheDetails.sumOf { it.cacheSize }
                val cacheItems = cacheDetails.map { info ->
                    FileItem(id = info.packageName, name = info.appName, type = "Caché", 
                        size = info.cacheSize, status = "Installed", path = info.packageName, isChecked = true)
                }
                groups.add(JunkGroup("Caché de archivos basura", totalCacheSize, details = cacheItems.toMutableList(), isChecked = true))
                
                // 2. Basura del sistema (CON AGRUPAMIENTO POR CARPETA)
                val systemJunk = cleaningManager.findSystemJunk()
                val systemSize = systemJunk.sumOf { if (it.isDirectory) 0L else it.length() }
                
                val systemItems = mutableListOf<FileItem>()
                
                // Agrupar archivos por su carpeta contenedora
                val groupedByFolder = systemJunk.filter { !it.isDirectory }.groupBy { it.parentFile?.name ?: "Otros" }
                
                groupedByFolder.forEach { (folderName, filesInFolder) ->
                    if (filesInFolder.size > 5) {
                        // Si hay muchos archivos en una carpeta (como thumbnails), mostramos solo la carpeta
                        val folderPath = filesInFolder.first().parent ?: ""
                        systemItems.add(FileItem(
                            id = "folder_$folderPath",
                            name = "Carpeta: $folderName (${filesInFolder.size} archivos)",
                            type = "Basura",
                            size = filesInFolder.sumOf { it.length() },
                            status = "Junk",
                            path = folderPath,
                            isDirectory = true,
                            isChecked = true
                        ))
                    } else {
                        // Si son pocos archivos, los mostramos individualmente
                        filesInFolder.forEach { file ->
                            systemItems.add(FileItem(
                                id = file.absolutePath,
                                name = file.name,
                                type = file.extension.uppercase().ifEmpty { "SISTEMA" },
                                size = file.length(),
                                status = "Junk",
                                path = file.absolutePath,
                                isChecked = true
                            ))
                        }
                    }
                }
                
                // Añadir carpetas vacías reales
                systemJunk.filter { it.isDirectory }.forEach { dir ->
                    systemItems.add(FileItem(
                        id = dir.absolutePath,
                        name = dir.name,
                        type = "Carpeta vacía",
                        size = 0,
                        status = "Junk",
                        path = dir.absolutePath,
                        isDirectory = true,
                        isChecked = true
                    ))
                }

                groups.add(JunkGroup("Basura del sistema", systemSize, items = systemJunk, details = systemItems.sortedByDescending { it.size }.toMutableList(), isChecked = true))

                // 3. Apps no usadas
                val unusedDetails = cleaningManager.getUnusedAppsDetails()
                val totalAppSize = unusedDetails.sumOf { it.appSize }
                val appItems = unusedDetails.map { info ->
                    FileItem(id = info.packageName, name = info.appName, type = "App no usada", 
                        size = info.appSize, status = "Installed", path = info.packageName, isChecked = false)
                }
                groups.add(JunkGroup("Desinstala aplicaciones no usadas", totalAppSize, details = appItems.toMutableList(), isChecked = false, isAppGroup = true))
                
                groups
            }

            var currentProgress = 0
            while (currentProgress < 90) {
                currentProgress += 1
                tvPercentage.text = "$currentProgress%"
                val statusIndex = (currentProgress / 16).coerceAtMost(statuses.size - 1)
                tvStatus.text = statuses[statusIndex]
                if (scanJob.isCompleted) delay(5) else delay(40)
            }

            val resultGroups = scanJob.await()
            val ramAfter = getAvailableRam()
            val ramFreed = (ramAfter - ramBefore).coerceAtLeast(0L)

            for (p in currentProgress..100) {
                tvPercentage.text = "$p%"
                delay(10)
            }

            tvStatus.text = "¡Escaneo completado!"
            delay(500)

            ResultsActivity.junkGroupsToDisplay = resultGroups
            ResultsActivity.ramSizeLiberated = ramFreed

            startActivity(Intent(this@CleaningProgressActivity, ResultsActivity::class.java))
            finish()
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0.00 B"
        val units = arrayOf("B", "kB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1000.0)).toInt()
        return "%.2f %s".format(size / Math.pow(1000.0, digitGroups.toDouble()), units[digitGroups])
    }
}