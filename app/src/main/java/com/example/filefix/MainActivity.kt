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

        setupToolbar()
        setupRecyclerView()
        setupFab()
        observeViewModel()
        
        // Cargar archivos (intentará API, si no, mostrará vacío o podemos mockear)
        viewModel.loadFiles()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewFiles)
        adapter = FileAdapter(emptyList())
        recyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        val fileScanner = FileScanner(this)
        val realFiles = fileScanner.getAllFiles()
        
        if (realFiles.isEmpty()) {
            // Si no hay archivos reales, mostramos los dummy solo para que la lista no esté vacía en la demo
            val dummyFiles = listOf(
                FileItem("1", "Sin archivos reales.pdf", "PDF", 0, "Demo"),
                FileItem("2", "Asegúrate de tener archivos.jpg", "Imagen", 0, "Demo")
            )
            adapter.updateFiles(dummyFiles)
        } else {
            // Mostramos tus archivos reales
            adapter.updateFiles(realFiles)
        }
        
        // Mantenemos la observación por si usas la API después
        viewModel.files.observe(this) { files ->
            if (files.isNotEmpty()) {
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