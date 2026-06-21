package com.example.filefix

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filefix.adapter.JunkGroupAdapter
import com.example.filefix.model.JunkGroup
import com.google.android.material.button.MaterialButton
import java.io.File
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultsActivity : AppCompatActivity() {

    companion object {
        var junkGroupsToDisplay: List<JunkGroup> = emptyList()
    }

    private lateinit var cleaningManager: CleaningManager
    private lateinit var adapter: JunkGroupAdapter
    private val currentGroups = mutableListOf<JunkGroup>()
    private var totalSelectedSize: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        cleaningManager = CleaningManager(this)
        currentGroups.addAll(junkGroupsToDisplay)
        
        recalculateTotalSize()

        setupViews()
        updateTotalSizeDisplay()
    }

    private fun setupViews() {
        val rvList = findViewById<RecyclerView>(R.id.rvJunkList)
        val btnClean = findViewById<MaterialButton>(R.id.btnClean)
        val btnCancel = findViewById<MaterialButton>(R.id.btnCancel)

        adapter = JunkGroupAdapter(
            groups = currentGroups,
            onCacheGlobalAction = {
                val globalIntent = cleaningManager.getGlobalCacheClearIntent()
                if (globalIntent != null) {
                    startActivity(globalIntent)
                } else {
                    Toast.makeText(this, "Limpiando caché del sistema...", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch(Dispatchers.IO) {
                        cleaningManager.clearAppCaches()
                    }
                }
            },
            onCacheItemAction = { packageName ->
                Toast.makeText(this, "Pulse en 'Borrar Caché' en la siguiente pantalla", Toast.LENGTH_LONG).show()
                cleaningManager.openAppSettings(packageName)
            },
            onTotalSizeChanged = {
                recalculateTotalSize()
            }
        )
        rvList.layoutManager = LinearLayoutManager(this)
        rvList.adapter = adapter

        btnClean.setOnClickListener {
            performRealCleaning(btnClean, btnCancel)
        }

        btnCancel.setOnClickListener { 
            junkGroupsToDisplay = emptyList()
            finish() 
        }
    }

    private fun recalculateTotalSize() {
        totalSelectedSize = currentGroups.sumOf { group ->
            if (group.title.contains("Caché", ignoreCase = true)) {
                0L // El caché ya no es parte de la selección por lotes
            } else {
                group.details.filter { it.isChecked }.sumOf { it.size }
            }
        }
        updateTotalSizeDisplay()
    }

    private fun updateTotalSizeDisplay() {
        findViewById<TextView>(R.id.tvTotalJunkSize).text = formatFileSize(totalSelectedSize)
    }

    private fun performRealCleaning(btnClean: MaterialButton, btnCancel: MaterialButton) {
        btnClean.isEnabled = false
        btnCancel.isEnabled = false
        btnClean.text = "LIMPIANDO..."

        lifecycleScope.launch {
            var totalDeleted = 0L
            val appsToUninstall = mutableListOf<String>()
            
            withContext(Dispatchers.IO) {
                for (group in currentGroups) {
                    val anyChildChecked = group.details.any { it.isChecked }
                    if (!group.isChecked && !anyChildChecked) continue

                    if (group.isAppGroup) {
                        // Recolectar paquetes para desinstalar
                        appsToUninstall.addAll(group.details.filter { it.isChecked }.map { it.id })
                    } else if (group.title.contains("Caché", ignoreCase = true)) {
                        // El caché ahora se maneja mediante botones individuales en el Adapter
                        continue 
                    } else {
                        // Archivos de sistema (Junk)
                        val selectedFiles = group.details.filter { it.isChecked }.map { File(it.path) }
                        if (selectedFiles.isNotEmpty()) {
                            totalDeleted += cleaningManager.deleteFiles(selectedFiles)
                        }
                    }
                }
            }

            if (totalDeleted > 0) {
                Toast.makeText(this@ResultsActivity, "Se liberaron ${formatFileSize(totalDeleted)}", Toast.LENGTH_LONG).show()
            }

            // Manejar desinstalaciones
            if (appsToUninstall.isNotEmpty()) {
                for (packageName in appsToUninstall) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_DELETE)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            }

            junkGroupsToDisplay = emptyList()
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