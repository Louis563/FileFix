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

class DashboardActivity : AppCompatActivity() {

    private lateinit var fileScanner: FileScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        fileScanner = FileScanner(this)
        setupClickListeners()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateFileCounts()
    }

    private fun updateFileCounts() {
        if (hasManageExternalStoragePermission()) {
            findViewById<TextView>(R.id.tvAudioCount).text = fileScanner.countAudios().toString()
            findViewById<TextView>(R.id.tvVideoCount).text = fileScanner.countVideos().toString()
            findViewById<TextView>(R.id.tvImageCount).text = fileScanner.countImages().toString()
            findViewById<TextView>(R.id.tvDocCount).text = fileScanner.countDocuments().toString()
        }
    }

    private fun setupClickListeners() {
        val btnAllFiles = findViewById<LinearLayout>(R.id.llAllFiles)
        btnAllFiles.setOnClickListener {
            navigateToMain()
        }

        val btnStorage = findViewById<LinearLayout>(R.id.llStorage)
        btnStorage.setOnClickListener {
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        if (hasManageExternalStoragePermission()) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        } else {
            requestManageExternalStoragePermission()
        }
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