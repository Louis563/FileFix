package com.example.filefix

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        val btnAllFiles = findViewById<LinearLayout>(R.id.llAllFiles)
        btnAllFiles.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        val btnStorage = findViewById<LinearLayout>(R.id.llStorage)
        btnStorage.setOnClickListener {
            // Podríamos abrir una vista de optimización específica
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        
        // Aquí se pueden añadir clics para cada categoría (Audio, Video, etc.)
    }
}