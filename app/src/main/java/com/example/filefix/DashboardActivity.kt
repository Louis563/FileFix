package com.example.filefix

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.os.BatteryManager
import android.app.ActivityManager
import android.graphics.Color
import java.io.File

class DashboardActivity : AppCompatActivity() {

    private lateinit var fileScanner: FileScanner

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val temperature = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
                val health = it.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                updateBatteryUI(temperature, health)
            }
        }
    }

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
        updateRamInfo()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(batteryReceiver)
    }

    private fun updateBatteryUI(temp: Double, health: Int) {
        val healthText = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Buena"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentada"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Mala"
            BatteryManager.BATTERY_HEALTH_COLD -> "Fría"
            else -> "Normal"
        }
        
        findViewById<TextView>(R.id.tvBatteryStatus).text = "Salud: $healthText"
        findViewById<TextView>(R.id.tvBatteryTemp).text = "Temp: $temp °C"
        
        val tempColor = if (temp > 38) "#FF5252" else "#00E676"
        findViewById<TextView>(R.id.tvBatteryTemp).setTextColor(Color.parseColor(tempColor))
    }

    private fun updateRamInfo() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRam = memoryInfo.totalMem / (1000.0 * 1000.0 * 1000.0)
        val availableRam = memoryInfo.availMem / (1000.0 * 1000.0 * 1000.0)
        val usedRam = totalRam - availableRam

        findViewById<TextView>(R.id.tvRamStatus).text = String.format("%.1f GB / %.1f GB", usedRam, totalRam)
        val progress = ((usedRam / totalRam) * 100).toInt()
        findViewById<ProgressBar>(R.id.pbRamUsage).progress = progress
    }

    private fun updateStorageInfo() {
        try {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes

            var usedGB = usedBytes / (1000.0 * 1000.0 * 1000.0)
            var totalGB = totalBytes / (1000.0 * 1000.0 * 1000.0)

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

        findViewById<android.view.View>(R.id.llOptimizeCategoryMain).setOnClickListener {
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
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}