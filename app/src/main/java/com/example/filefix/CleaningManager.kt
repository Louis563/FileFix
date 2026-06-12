package com.example.filefix

import android.os.Environment
import java.io.File

class CleaningManager {

    private val junkExtensions = setOf("tmp", "log", "cache", "temp", "apk_temp", "old", "bak", "thumbdata")
    private val junkFolderNames = setOf("cache", "temp", "logs", ".thumbnails")

    private val protectedExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp",
        "mp4", "mkv", "mov", "avi", "3gp",
        "mp3", "wav", "ogg", "flac", "m4a",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt",
        "zip", "rar", "7z"
    )

    fun findJunkFiles(): List<File> {
        val junkFiles = mutableListOf<File>()
        val root = Environment.getExternalStorageDirectory()
        scanDirectory(root, junkFiles)
        return junkFiles
    }

    private fun scanDirectory(directory: File, junkList: MutableList<File>) {
        val files = directory.listFiles() ?: return
        
        for (file in files) {
            val name = file.name.lowercase()
            
            if (file.isDirectory) {
                if (file.name == "Android") continue

                // Si es una carpeta de basura como .thumbnails o cache, limpiamos TODO su contenido
                if (junkFolderNames.contains(name)) {
                    cleanAllInside(file, junkList)
                } else {
                    scanDirectory(file, junkList)
                }
            } else {
                if (isJunkFile(file)) {
                    junkList.add(file)
                }
            }
        }
    }

    /**
     * Esta función añade TODO el contenido de una carpeta a la lista de limpieza,
     * ignorando la lista de extensiones protegidas porque sabemos que esta carpeta es 100% basura.
     */
    private fun cleanAllInside(directory: File, junkList: MutableList<File>) {
        val files = directory.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                cleanAllInside(file, junkList)
                junkList.add(file)
            } else {
                // Aquí no preguntamos si es protegido, si está en .thumbnails o cache, se va.
                junkList.add(file)
            }
        }
    }

    private fun isJunkFile(file: File): Boolean {
        val name = file.name.lowercase()
        val extension = file.extension.lowercase()
        
        // REGLA DE ORO 1: Si se llama .nomedia, ES SAGRADO (evita que la basura salga en la galería)
        if (name == ".nomedia") {
            return false
        }

        // REGLA DE ORO 2: Si la extensión es de un archivo personal, NO TOCAR
        if (protectedExtensions.contains(extension)) {
            return false
        }

        if (name.startsWith(".thumbdata") || extension.contains("thumbdata")) {
            return true
        }

        if (junkExtensions.contains(extension)) {
            return true
        }

        if (name.startsWith(".") && file.length() < 2048) {
            return true
        }

        return false
    }

    fun deleteFiles(files: List<File>): Long {
        var totalDeletedSpace = 0L
        // Ordenamos para borrar archivos primero y carpetas después
        val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.absolutePath.length }))
        
        for (file in sortedFiles) {
            if (!file.isDirectory) {
                val size = file.length()
                if (file.delete()) {
                    totalDeletedSpace += size
                }
            } else {
                file.delete() // Borrar carpeta si está vacía
            }
        }
        return totalDeletedSpace
    }
}