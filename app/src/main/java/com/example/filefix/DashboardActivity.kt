package com.example.filefix

import android.content.Context
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
import java.io.File

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
            findViewById<TextView>(R.id.tvAppsCount).text = fileScanner.countApps().toString()
            findViewById<TextView>(R.id.tvDownloadCount).text = fileScanner.countDownloads().toString()
        }
    }

    private fun setupClickListeners() {
        val etSearch = findViewById<android.widget.EditText>(R.id.etSearch)
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString()
                if (query.isNotEmpty()) {
                    navigateToSearch(query)
                }
                true
            } else false
        }

        findViewById<LinearLayout>(R.id.llAudio).setOnClickListener { navigateToMain("AUDIO") }
        findViewById<LinearLayout>(R.id.llVideos).setOnClickListener { navigateToMain("VIDEO") }
        findViewById<LinearLayout>(R.id.llImages).setOnClickListener { navigateToMain("IMAGE") }
        findViewById<LinearLayout>(R.id.llApps).setOnClickListener { navigateToMain("APPS") }
        findViewById<LinearLayout>(R.id.llDocs).setOnClickListener { navigateToMain("DOCS") }
        findViewById<LinearLayout>(R.id.llDownloads).setOnClickListener { navigateToMain("DOWNLOADS") }

        findViewById<LinearLayout>(R.id.llAllFiles).setOnClickListener { navigateToMain("ALL") }

        findViewById<LinearLayout>(R.id.llOptimizeCategory).setOnClickListener {
            if (hasManageExternalStoragePermission()) {
                startActivity(Intent(this, CleaningProgressActivity::class.java))
            } else {
                requestManageExternalStoragePermission()
            }
        }
    }

    private fun navigateToSearch(query: String) {
        if (hasManageExternalStoragePermission()) {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("CATEGORY", "SEARCH")
            intent.putExtra("QUERY", query)
            startActivity(intent)
        } else {
            requestManageExternalStoragePermission()
        }
    }

    private fun navigateToMain(category: String = "ALL") {
        if (hasManageExternalStoragePermission()) {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("CATEGORY", category)
            startActivity(intent)
        } else {
            requestManageExternalStoragePermission()
        }
    }

    private fun hasManageExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
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
            Toast.makeText(this, "Permiso concedido", Toast.LENGTH_SHORT).show()
        }
    }

    private val storageActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasManageExternalStoragePermission()) {
                Toast.makeText(this, "Permiso concedido", Toast.LENGTH_SHORT).show()
            }
        }

    private fun checkPermissions() {
        if (!hasManageExternalStoragePermission()) {
            Toast.makeText(this, "Se requiere permiso para gestionar archivos", Toast.LENGTH_LONG).show()
        }
        if (!hasUsageStatsPermission()) {
            showUsageStatsDialog()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun showUsageStatsDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permiso necesario")
            .setMessage("Para el correcto funcionamiento de la gestión de aplicaciones, se requiere el permiso de acceso a datos de uso.")
            .setPositiveButton("Configurar") { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}