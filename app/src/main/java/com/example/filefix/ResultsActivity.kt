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

        adapter = JunkGroupAdapter(currentGroups) {
            recalculateTotalSize()
        }
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
            
            withContext(Dispatchers.IO) {
                for (group in currentGroups) {
                    val selectedFiles = group.details.filter { it.isChecked }.map { File(it.path) }
                    
                    if (selectedFiles.isNotEmpty()) {
                        if (!group.isAppGroup) {
                            totalDeleted += cleaningManager.deleteFiles(selectedFiles)
                        } else {
                            totalDeleted += group.details.filter { it.isChecked }.sumOf { it.size }
                        }
                    }
                }
            }

            Toast.makeText(this@ResultsActivity, "Se liberaron ${formatFileSize(totalDeleted)}", Toast.LENGTH_LONG).show()
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