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

class ResultsActivity : AppCompatActivity() {

    companion object {
        var filesToDelete: List<File> = emptyList()
        var totalSizeToClean: Long = 0L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        val tvSize = findViewById<TextView>(R.id.tvTotalJunkSize)
        val rvList = findViewById<RecyclerView>(R.id.rvJunkList)
        val btnClean = findViewById<MaterialButton>(R.id.btnClean)
        val btnCancel = findViewById<MaterialButton>(R.id.btnCancel)

        tvSize.text = formatFileSize(totalSizeToClean)

        val fileScanner = FileScanner(this)
        val fileItems = filesToDelete.map { file ->
            FileItem(
                id = file.absolutePath,
                name = file.name,
                type = fileScanner.getMimeType(file),
                size = file.length(),
                status = "Junk",
                uri = null,
                isDirectory = file.isDirectory,
                path = file.absolutePath
            )
        }

        rvList.layoutManager = LinearLayoutManager(this)
        rvList.adapter = FileAdapter(fileItems) { item ->
            Toast.makeText(this, item.path, Toast.LENGTH_LONG).show()
        }

        btnClean.setOnClickListener {
            performSafeCleaning(btnClean, btnCancel)
        }

        btnCancel.setOnClickListener {
            filesToDelete = emptyList()
            totalSizeToClean = 0L
            finish()
        }
    }

    private fun performSafeCleaning(btnClean: MaterialButton, btnCancel: MaterialButton) {
        btnClean.isEnabled = false
        btnCancel.isEnabled = false
        btnClean.text = "LIMPIANDO..."

        lifecycleScope.launch {
            val cleaningManager = CleaningManager()
            val deletedSpace = withContext(Dispatchers.IO) {
                cleaningManager.deleteFiles(filesToDelete)
            }

            Toast.makeText(this@ResultsActivity, 
                "Limpieza terminada. Se liberaron ${formatFileSize(deletedSpace)}", 
                Toast.LENGTH_LONG).show()

            filesToDelete = emptyList()
            totalSizeToClean = 0L
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