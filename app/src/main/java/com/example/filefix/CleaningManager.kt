package com.example.filefix

import android.os.Environment
import java.io.File

class CleaningManager {

    /**
     * Busca archivos considerados "basura":
     * - Archivos en carpetas de caché.
     * - Archivos temporales (.tmp).
     * - Carpetas vacías (opcional).
     */
    fun findJunkFiles(): List<File> {
        val junkList = mutableListOf<File>()
        
        // Escanear almacenamiento externo para simular búsqueda de basura
        val externalStorage = Environment.getExternalStorageDirectory()
        scanDirectoryForJunk(externalStorage, junkList)
        
        return junkList
    }

    private fun scanDirectoryForJunk(directory: File, junkList: MutableList<File>) {
        val files = directory.listFiles() ?: return
        
        for (file in files) {
            if (file.isDirectory) {
                // Si es una carpeta de caché conocida
                if (file.name.equals("cache", ignoreCase = true) || 
                    file.name.equals(".cache", ignoreCase = true)) {
                    junkList.add(file)
                } else {
                    // Limitar profundidad para evitar lentitud extrema en la demo
                    if (!file.isHidden) {
                        scanDirectoryForJunk(file, junkList)
                    }
                }
            } else {
                // Si es un archivo temporal
                if (file.extension.equals("tmp", ignoreCase = true) || 
                    file.name.startsWith(".tmp")) {
                    junkList.add(file)
                }
            }
            // Limitar a 100 archivos para la demo
            if (junkList.size > 100) break
        }
    }

    /**
     * Elimina físicamente los archivos y devuelve el espacio total liberado.
     */
    fun deleteFiles(files: List<File>): Long {
        var totalDeleted: Long = 0
        for (file in files) {
            val size = if (file.isDirectory) getFolderSize(file) else file.length()
            if (deleteRecursive(file)) {
                totalDeleted += size
            }
        }
        return totalDeleted
    }

    private fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        return try {
            file.delete()
        } catch (e: Exception) {
            false
        }
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        file.listFiles()?.forEach {
            size += if (it.isDirectory) getFolderSize(it) else it.length()
        }
        return size
    }
}
