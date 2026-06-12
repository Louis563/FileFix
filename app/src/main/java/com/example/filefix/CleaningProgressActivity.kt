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
    private lateinit var cleaningManager: CleaningManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cleaning_progress)

        cleaningManager = CleaningManager(this)
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
            // Animación visual suave de 0 a 100%
            for (progress in 0..100) {
                tvPercentage.text = "$progress%"
                val statusIndex = (progress / 21).coerceAtMost(statuses.size - 1)
                tvStatus.text = statuses[statusIndex]
                delay(30)
            }

            delay(500)
            val intent = Intent(this@CleaningProgressActivity, ResultsActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}