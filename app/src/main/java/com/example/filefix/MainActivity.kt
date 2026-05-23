package com.example.filefix

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.filefix.adapter.FileAdapter
import com.example.filefix.model.FileItem
import com.example.filefix.network.ApiClient
import com.example.filefix.repository.FileRepository
import com.example.filefix.viewmodel.FileViewModel
import com.example.filefix.viewmodel.FileViewModelFactory
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: FileAdapter
    private val viewModel: FileViewModel by viewModels {
        FileViewModelFactory(FileRepository(ApiClient.apiService))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupRecyclerView()
        setupFab()
        observeViewModel()
        
        // Cargar archivos (intentará API, si no, mostrará vacío o podemos mockear)
        viewModel.loadFiles()
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewFiles)
        adapter = FileAdapter(emptyList())
        recyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.files.observe(this) { files ->
            if (files.isEmpty()) {
                // Si la API no devuelve nada, podemos inyectar datos de prueba para la demo
                val dummyFiles = listOf(
                    FileItem("1", "Documento_Universidad.pdf", "PDF", 2048000, "Normal"),
                    FileItem("2", "Foto_Vacaciones.jpg", "Imagen", 5120000, "Grande"),
                    FileItem("3", "Cache_App_Basura.tmp", "Temporal", 102400, "Sugerido para borrar"),
                    FileItem("4", "Video_Proyecto.mp4", "Video", 102400000, "Grande"),
                    FileItem("5", "Apuntes_Clase.docx", "Documento", 50000, "Normal")
                )
                adapter.updateFiles(dummyFiles)
            } else {
                adapter.updateFiles(files)
            }
        }
    }

    private fun setupFab() {
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fabOptimize)
        fab.setOnClickListener {
            Toast.makeText(this, "El Bot está analizando tus archivos...", Toast.LENGTH_SHORT).show()
            viewModel.optimizeFiles()
        }
    }
}