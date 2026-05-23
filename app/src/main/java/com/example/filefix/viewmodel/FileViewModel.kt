package com.example.filefix.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.filefix.model.FileItem
import com.example.filefix.repository.FileRepository
import kotlinx.coroutines.launch

class FileViewModel(private val repository: FileRepository) : ViewModel() {

    private val _files = MutableLiveData<List<FileItem>>()
    val files: LiveData<List<FileItem>> = _files

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.getFiles()
            _files.value = result
            _isLoading.value = false
        }
    }

    // Lógica básica del Bot de optimización
    fun optimizeFiles() {
        viewModelScope.launch {
            val currentFiles = _files.value ?: emptyList()
            val optimized = currentFiles.filter { it.status != "Sugerido para borrar" }
            _files.postValue(optimized)
        }
    }
}