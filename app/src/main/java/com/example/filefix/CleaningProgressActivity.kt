package com.example.filefix

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.filefix.model.FileItem
import com.example.filefix.model.JunkGroup
import kotlinx.coroutines.*

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

    private fun startRealScanning() {
        val statuses = arrayOf(
            "Analizando caché de aplicaciones...",
            "Buscando archivos temporales...",
            "Identificando carpetas vacías...",
            "Calculando espacio recuperable...",
            "Finalizando escaneo..."
        )

        lifecycleScope.launch {
            val scanJob = async(Dispatchers.IO) {
                val groups = mutableListOf<JunkGroup>()
                
                // 1. Caché de apps (Marcado por defecto)
                val cacheDetails = cleaningManager.getAppsCacheDetails()
                val totalCacheSize = cacheDetails.sumOf { it.cacheSize }
                val cacheItems = cacheDetails.map { info ->
                    FileItem(id = info.packageName, name = info.appName, type = "Caché", 
                        size = info.cacheSize, status = "Installed", path = info.packageName, isChecked = true)
                }
                groups.add(JunkGroup("Caché de archivos basura", totalCacheSize, details = cacheItems, isChecked = true))
                
                // 2. Basura del sistema (Marcado por defecto)
                val systemJunk = cleaningManager.findSystemJunk()
                val systemSize = systemJunk.sumOf { if (it.isDirectory) 0L else it.length() }
                val systemItems = systemJunk.map { file ->
                    FileItem(id = file.absolutePath, name = file.name, type = "Sistema", 
                        size = file.length(), status = "Junk", path = file.absolutePath, isDirectory = file.isDirectory, isChecked = true)
                }
                groups.add(JunkGroup("Basura del sistema", systemSize, items = systemJunk, details = systemItems, isChecked = true))

                // 3. Apps no usadas (DESMARCADO por defecto - HIJOS TAMBIÉN)
                val unusedDetails = cleaningManager.getUnusedAppsDetails()
                val totalAppSize = unusedDetails.sumOf { it.appSize }
                val appItems = unusedDetails.map { info ->
                    FileItem(id = info.packageName, name = info.appName, type = "App no usada", 
                        size = info.appSize, status = "Installed", path = info.packageName, isChecked = false)
                }
                groups.add(JunkGroup("Desinstala aplicaciones no usadas", totalAppSize, details = appItems, isChecked = false, isAppGroup = true))
                
                groups
            }

            var currentProgress = 0
            while (currentProgress < 90) {
                currentProgress += 1
                tvPercentage.text = "$currentProgress%"
                val statusIndex = (currentProgress / 20).coerceAtMost(statuses.size - 1)
                tvStatus.text = statuses[statusIndex]
                if (scanJob.isCompleted) delay(5) else delay(30)
            }

            val resultGroups = scanJob.await()
            for (p in currentProgress..100) {
                tvPercentage.text = "$p%"
                delay(10)
            }
            tvStatus.text = "¡Escaneo completado!"
            delay(500)

            ResultsActivity.junkGroupsToDisplay = resultGroups
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