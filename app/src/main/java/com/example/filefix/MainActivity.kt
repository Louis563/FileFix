package com.example.filefix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
    private var currentFiles: List<FileItem> = emptyList()
    private var currentSortMode: Int = R.id.sort_default

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
        
        val category = intent.getStringExtra("CATEGORY") ?: "ALL"
        when (category) {
            "ALL" -> loadCurrentDirectory()
            "SEARCH" -> {
                val query = intent.getStringExtra("QUERY") ?: ""
                loadSearchFiles(query)
            }
            else -> loadCategoryFiles(category)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Si tocamos el botón contenedor "Ordenar por", no hacemos nada a la lista
        if (item.itemId == R.id.action_sort) return false

        // Marcamos la opción seleccionada y actualizamos el modo
        item.isChecked = true
        currentSortMode = item.itemId
        applySorting()
        return true
    }

    private fun applySorting() {
        if (currentFiles.isEmpty()) return

        val sortedList = when (currentSortMode) {
            R.id.sort_name_asc -> currentFiles.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            R.id.sort_name_desc -> currentFiles.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).reversed()
            R.id.sort_size_asc -> currentFiles.sortedWith(compareBy({ !it.isDirectory }, { it.size }))
            R.id.sort_size_desc -> currentFiles.sortedWith(compareBy({ !it.isDirectory }, { it.size })).reversed()
            R.id.sort_date_asc -> currentFiles.sortedWith(compareBy({ !it.isDirectory }, { it.dateModified }))
            R.id.sort_date_desc -> currentFiles.sortedWith(compareBy({ !it.isDirectory }, { it.dateModified })).reversed()
            else -> currentFiles // Default
        }
        
        adapter.updateFiles(sortedList)
    }

    private fun loadSearchFiles(query: String) {
        lifecycleScope.launch {
            val fileScanner = FileScanner(this@MainActivity)
            currentFiles = withContext(Dispatchers.IO) {
                fileScanner.searchFiles(query)
            }
            applySorting()
            supportActionBar?.title = "Resultados: $query"
        }
    }

    private fun loadCategoryFiles(category: String) {
        lifecycleScope.launch {
            val fileScanner = FileScanner(this@MainActivity)
            currentFiles = withContext(Dispatchers.IO) {
                fileScanner.getFilesByType(category)
            }
            applySorting()
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
            currentFiles = withContext(Dispatchers.IO) {
                fileScanner.listFiles(currentPath)
            }
            applySorting()
            supportActionBar?.title = File(currentPath).name.ifEmpty { "Raíz" }
        }
    }

    private fun handleBackNavigation() {
        val category = intent.getStringExtra("CATEGORY") ?: "ALL"
        if (category != "ALL") {
            finish()
            return
        }

        val parentFile = File(currentPath).parentFile
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        
        if (currentPath == rootPath) {
            finish()
        } else if (parentFile != null) {
            currentPath = parentFile.absolutePath
            loadCurrentDirectory()
        } else {
            finish()
        }
    }

    private fun observeViewModel() {
        viewModel.files.observe(this) { files ->
            if (files.isNotEmpty()) {
                currentFiles = files
                applySorting()
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