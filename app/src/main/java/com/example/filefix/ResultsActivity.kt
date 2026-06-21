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
        var ramSizeLiberated: Long = 0L
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
        
        if (ramSizeLiberated > 1024 * 1024) { 
            Toast.makeText(this, "¡Memoria acelerada! Se liberaron ${formatFileSize(ramSizeLiberated)} de RAM", Toast.LENGTH_SHORT).show()
            ramSizeLiberated = 0L
        }
    }

    private fun setupViews() {
        val rvList = findViewById<RecyclerView>(R.id.rvJunkList)
        val btnClean = findViewById<MaterialButton>(R.id.btnClean)
        val btnCancel = findViewById<MaterialButton>(R.id.btnCancel)

        adapter = JunkGroupAdapter(currentGroups) {
            recalculateTotalSize()
        }
        rvList.layoutManager = LinearLayoutManager(this)
        rvList.adapter = adapter

        btnClean.setOnClickListener {
            performRealCleaning(btnClean, btnCancel)
        }

        btnCancel.setOnClickListener { 
            cleanupData()
            finish() 
        }
    }

    private fun recalculateTotalSize() {
        totalSelectedSize = currentGroups.sumOf { group ->
            group.details.filter { it.isChecked }.sumOf { it.size }
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
                    if (group.isAppGroup) {
                        appsToUninstall.addAll(group.details.filter { it.isChecked }.map { it.id })
                    } else {
                        val filesToDelete = mutableListOf<File>()
                        group.details.filter { it.isChecked }.forEach { detail ->
                            if (detail.id.startsWith("folder_")) {
                                val folderPath = detail.path
                                filesToDelete.addAll(group.items.filter { it.absolutePath.startsWith(folderPath) })
                            } else {
                                filesToDelete.add(File(detail.path))
                            }
                        }
                        if (filesToDelete.isNotEmpty()) {
                            totalDeleted += cleaningManager.deleteFiles(filesToDelete)
                        }
                    }
                }
            }

            Toast.makeText(this@ResultsActivity, "Se liberaron ${formatFileSize(totalDeleted)}", Toast.LENGTH_LONG).show()
            cleanupData()
            finish()
        }
    }

    private fun cleanupData() {
        junkGroupsToDisplay = emptyList()
        ramSizeLiberated = 0L
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0.00 B"
        val units = arrayOf("B", "kB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1000.0)).toInt()
        return "%.2f %s".format(size / Math.pow(1000.0, digitGroups.toDouble()), units[digitGroups])
    }
}