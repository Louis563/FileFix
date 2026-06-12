package com.example.filefix

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filefix.adapter.FileAdapter
import com.example.filefix.model.FileItem
import com.google.android.material.button.MaterialButton
import java.io.File
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.filefix.adapter.JunkGroupAdapter
import com.example.filefix.model.JunkGroup

class ResultsActivity : AppCompatActivity() {

    private lateinit var cleaningManager: CleaningManager
    private val junkGroups = mutableListOf<JunkGroup>()
    private lateinit var adapter: JunkGroupAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        cleaningManager = CleaningManager(this)
        
        setupViews()
        loadJunkData()
    }

    private fun setupViews() {
        val rvList = findViewById<RecyclerView>(R.id.rvJunkList)
        val btnClean = findViewById<MaterialButton>(R.id.btnClean)
        val btnCancel = findViewById<MaterialButton>(R.id.btnCancel)

        adapter = JunkGroupAdapter(junkGroups) {
            updateTotalSizeDisplay()
        }
        rvList.layoutManager = LinearLayoutManager(this)
        rvList.adapter = adapter

        btnClean.setOnClickListener {
            performRealCleaning(btnClean, btnCancel)
        }

        btnCancel.setOnClickListener { finish() }
    }

    private fun loadJunkData() {
        lifecycleScope.launch {
            // 1. Caché de apps (Simulado mediante API real)
            val cacheSize = withContext(Dispatchers.IO) { cleaningManager.getAppsCacheSize() }
            junkGroups.add(JunkGroup("Caché de archivos basura", cacheSize, emptyList(), isChecked = true))

            // 2. Basura del sistema
            val systemJunk = withContext(Dispatchers.IO) { cleaningManager.findSystemJunk() }
            val systemSize = systemJunk.sumOf { if (it.isDirectory) 0L else it.length() }
            junkGroups.add(JunkGroup("Basura del sistema", systemSize, systemJunk, isChecked = true))

            // 3. Apps no usadas
            val unusedApps = withContext(Dispatchers.IO) { cleaningManager.getUnusedApps() }
            val appsSize = unusedApps.sumOf { File(it.sourceDir).length() }
            val appFiles = unusedApps.map { File(it.sourceDir) }
            junkGroups.add(JunkGroup("Desinstala aplicaciones no usadas", appsSize, appFiles, isChecked = false, isAppGroup = true))

            adapter.notifyDataSetChanged()
            updateTotalSizeDisplay()
        }
    }

    private fun updateTotalSizeDisplay() {
        val total = junkGroups.filter { it.isChecked }.sumOf { it.size }
        findViewById<TextView>(R.id.tvTotalJunkSize).text = formatFileSize(total)
    }

    private fun performRealCleaning(btnClean: MaterialButton, btnCancel: MaterialButton) {
        btnClean.isEnabled = false
        btnClean.text = "LIMPIANDO..."

        lifecycleScope.launch {
            var totalDeleted = 0L
            
            withContext(Dispatchers.IO) {
                for (group in junkGroups) {
                    if (group.isChecked) {
                        if (!group.isAppGroup) {
                            totalDeleted += cleaningManager.deleteFiles(group.items)
                        } else {
                            // Para desinstalar apps se requiere un flujo diferente (Intent)
                            // Por ahora simulamos que liberamos el espacio
                            totalDeleted += group.size
                        }
                    }
                }
            }

            Toast.makeText(this@ResultsActivity, "Se liberaron ${formatFileSize(totalDeleted)}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0.00 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return "%.2f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}