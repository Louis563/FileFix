package com.example.filefix

import android.content.Intent
import android.net.Uri
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

import android.os.Environment
import java.io.File

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: FileAdapter
    private var currentPath: String = Environment.getExternalStorageDirectory().absolutePath
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
        setupBackCallback()
        
        val category = intent.getStringExtra("CATEGORY_FILTER") ?: "ALL"
        if (category == "ALL") {
            loadCurrentDirectory()
        } else if (category == "SEARCH") {
            val query = intent.getStringExtra("QUERY") ?: ""
            loadSearchFiles(query)
        } else {
            loadCategoryFiles(category)
        }
    }

    private fun loadSearchFiles(query: String) {
        lifecycleScope.launch {
            val fileScanner = FileScanner(this@MainActivity)
            val files = withContext(Dispatchers.IO) {
                fileScanner.searchFiles(query)
            }
            adapter.updateFiles(files)
            supportActionBar?.title = "Resultados: $query"
        }
    }

    private fun loadCategoryFiles(category: String) {
        lifecycleScope.launch {
            val fileScanner = FileScanner(this@MainActivity)
            val files = withContext(Dispatchers.IO) {
                fileScanner.getFilesByType(category)
            }
            adapter.updateFiles(files)
            supportActionBar?.title = when(category) {
                "AUDIO" -> "Audios"
                "VIDEO" -> "Videos"
                "IMAGE" -> "Imágenes"
                "APPS" -> "Aplicaciones"
                "DOCS" -> "Documentos"
                "DOWNLOADS" -> "Descargas"
                else -> "Archivos"
            }
        }
    }

    private fun setupBackCallback() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            handleBackNavigation()
        }
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewFiles)
        adapter = FileAdapter(emptyList()) { fileItem ->
            if (fileItem.isDirectory) {
                currentPath = fileItem.path
                loadCurrentDirectory()
            } else if (fileItem.status == "Installed") {
                openAppDetails(fileItem.path)
            } else {
                openFile(fileItem)
            }
        }
        recyclerView.adapter = adapter
    }

    private fun openFile(fileItem: FileItem) {
        try {
            val file = File(fileItem.path)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, fileItem.type)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            startActivity(Intent.createChooser(intent, "Abrir con..."))
        } catch (_: Exception) {
            Toast.makeText(this, "No se puede abrir el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppDetails(packageName: String) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo abrir la configuración de la app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCurrentDirectory() {
        lifecycleScope.launch {
            val fileScanner = FileScanner(this@MainActivity)
            val files = withContext(Dispatchers.IO) {
                fileScanner.listFiles(currentPath)
            }
            adapter.updateFiles(files)
            supportActionBar?.title = File(currentPath).name.ifEmpty { "Raíz" }
        }
    }

    private fun handleBackNavigation() {
        val parentFile = File(currentPath).parentFile
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        
        if (currentPath == rootPath) {
            finish() // Salir si estamos en la raíz
        } else if (parentFile != null) {
            currentPath = parentFile.absolutePath
            loadCurrentDirectory()
        } else {
            finish()
        }
    }

    private fun observeViewModel() {
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