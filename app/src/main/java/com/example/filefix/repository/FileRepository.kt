package com.example.filefix.repository

import com.example.filefix.model.FileItem
import com.example.filefix.network.ApiService

class FileRepository(private val apiService: ApiService) {

    suspend fun getFiles(): List<FileItem> {
        return try {
            val response = apiService.getFiles()
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fixFile(fileId: String): Boolean {
        return try {
            val response = apiService.fixFile(fileId)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteFile(fileId: String): Boolean {
        return try {
            val response = apiService.deleteFile(fileId)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}