package com.example.filefix

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class CleaningProgressActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvPercentage: TextView
    private val cleaningManager = CleaningManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cleaning_progress)

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
            // Ejecutar escaneo real en segundo plano
            val scanTask = async(Dispatchers.IO) {
                cleaningManager.findJunkFiles()
            }

            // Animación visual suave de 0 a 99%
            for (progress in 0..99) {
                tvPercentage.text = "$progress%"
                val statusIndex = (progress / 20).coerceAtMost(statuses.size - 1)
                tvStatus.text = statuses[statusIndex]
                delay(30)
            }

            // Esperar al resultado real
            val junkFiles = scanTask.await()
            val totalSize = junkFiles.sumOf { if (it.isDirectory) 0L else it.length() }

            // Finalizar
            tvPercentage.text = "100%"
            tvStatus.text = "¡Escaneo completado!"
            delay(500)

            // Pasar datos a la pantalla de resultados
            ResultsActivity.filesToDelete = junkFiles
            ResultsActivity.totalSizeToClean = totalSize

            val intent = Intent(this@CleaningProgressActivity, ResultsActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}