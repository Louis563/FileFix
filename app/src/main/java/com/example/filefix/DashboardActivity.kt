package com.example.filefix

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

import android.widget.TextView

import android.os.StatFs
import android.widget.ProgressBar

class DashboardActivity : AppCompatActivity() {

    private lateinit var fileScanner: FileScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        fileScanner = FileScanner(this)
        setupClickListeners()
        checkPermissions()
        updateStorageInfo()
    }

    override fun onResume() {
        super.onResume()
        updateFileCounts()
        updateStorageInfo()
    }

    private fun updateStorageInfo() {
        try {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes

            var usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0)
            var totalGB = totalBytes / (1024.0 * 1024.0 * 1024.0)

            val realTotal = when {
                totalGB > 100 && totalGB < 128 -> 128.0
                totalGB > 50 && totalGB < 64 -> 64.0
                totalGB > 25 && totalGB < 32 -> 32.0
                else -> totalGB
            }

            if (realTotal > totalGB) {
                val systemReserved = realTotal - totalGB
                usedGB += systemReserved
                totalGB = realTotal
            }

            findViewById<TextView>(R.id.tvStorageUsed).text = String.format("%.2f GB usada", usedGB)
            findViewById<TextView>(R.id.tvStorageTotal).text = String.format("%.2f GB total", totalGB)

            val progress = ((usedGB / totalGB) * 100).toInt()
            findViewById<ProgressBar>(R.id.storageProgressBar).progress = progress

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateFileCounts() {
        if (hasManageExternalStoragePermission()) {
            findViewById<TextView>(R.id.tvAudioCount).text = fileScanner.countAudios().toString()
            findViewById<TextView>(R.id.tvVideoCount).text = fileScanner.countVideos().toString()
            findViewById<TextView>(R.id.tvImageCount).text = fileScanner.countImages().toString()
            findViewById<TextView>(R.id.tvDocCount).text = fileScanner.countDocuments().toString()
            findViewById<TextView>(R.id.tvAppsCount).text = fileScanner.countApks().toString()
        }
    }

    private fun setupClickListeners() {
        val btnAllFiles = findViewById<LinearLayout>(R.id.llAllFiles)
        btnAllFiles.setOnClickListener {
            navigateToCategory("ALL")
        }

        val btnAudio = findViewById<LinearLayout>(R.id.llAudio)
        btnAudio.setOnClickListener { navigateToCategory("AUDIO") }

        val btnVideos = findViewById<LinearLayout>(R.id.llVideos)
        btnVideos.setOnClickListener { navigateToCategory("VIDEO") }

        val btnImages = findViewById<LinearLayout>(R.id.llImages)
        btnImages.setOnClickListener { navigateToCategory("IMAGE") }

        val btnApps = findViewById<LinearLayout>(R.id.llApps)
        btnApps.setOnClickListener { navigateToCategory("APPS") }

        val btnDocs = findViewById<LinearLayout>(R.id.llDocs)
        btnDocs.setOnClickListener { navigateToCategory("DOCS") }

        val btnDownloads = findViewById<LinearLayout>(R.id.llDownloads)
        btnDownloads.setOnClickListener { navigateToCategory("DOWNLOADS") }

        val btnStorageInfo = findViewById<LinearLayout>(R.id.llStorageInfo)
        btnStorageInfo.setOnClickListener {
            val intent = Intent(this, CleaningProgressActivity::class.java)
            startActivity(intent)
        }

        val btnOptimize = findViewById<android.view.View>(R.id.llOptimizeCategory)
        btnOptimize.setOnClickListener {
            val intent = Intent(this, CleaningProgressActivity::class.java)
            startActivity(intent)
        }
    }

    private fun navigateToCategory(category: String) {
        if (hasManageExternalStoragePermission()) {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("CATEGORY_FILTER", category)
            startActivity(intent)
        } else {
            requestManageExternalStoragePermission()
        }
    }

    private fun navigateToMain() {
        navigateToCategory("ALL")
    }

    private fun hasManageExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Para versiones anteriores, podrías chequear READ/WRITE_EXTERNAL_STORAGE
            true
        }
    }

    private fun requestManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                storageActivityResultLauncher.launch(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                storageActivityResultLauncher.launch(intent)
            }
        } else {
            // Lógica para Android 10 o inferior si fuera necesario
            Toast.makeText(this, "Permiso concedido (Versión anterior)", Toast.LENGTH_SHORT).show()
        }
    }

    private val storageActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasManageExternalStoragePermission()) {
                Toast.makeText(this, "Permiso de almacenamiento concedido", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permiso de almacenamiento denegado", Toast.LENGTH_SHORT).show()
            }
        }

    private fun checkPermissions() {
        if (!hasManageExternalStoragePermission()) {
            Toast.makeText(this, "Se requiere permiso para gestionar archivos", Toast.LENGTH_LONG).show()
        }
    }
}