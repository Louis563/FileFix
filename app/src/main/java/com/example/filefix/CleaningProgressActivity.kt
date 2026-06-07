package com.example.filefix

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CleaningProgressActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvPercentage: TextView
    private var progress = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cleaning_progress)

        tvStatus = findViewById(R.id.tvProgressStatus)
        tvPercentage = findViewById(R.id.tvProgressPercentage)

        startSimulatedCleaning()
    }

    private fun startSimulatedCleaning() {
        val statuses = arrayOf(
            "Analizando caché de aplicaciones...",
            "Buscando archivos temporales...",
            "Identificando carpetas vacías...",
            "Limpiando basura del sistema...",
            "Finalizando optimización..."
        )

        val runnable = object : Runnable {
            override fun run() {
                if (progress <= 100) {
                    tvPercentage.text = "$progress%"
                    
                    // Cambiar el mensaje de estado según el progreso
                    val statusIndex = (progress / 21).coerceAtMost(statuses.size - 1)
                    tvStatus.text = statuses[statusIndex]

                    progress += 2
                    handler.postDelayed(this, 50) // Simular velocidad de escaneo
                } else {
                    // Al terminar, ir a la pantalla de resultados (Pantalla 4)
                    val intent = Intent(this@CleaningProgressActivity, MainActivity::class.java) // Temporalmente a Main
                    startActivity(intent)
                    finish()
                }
            }
        }
        handler.post(runnable)
    }
}