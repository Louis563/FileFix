package com.example.filefix

import java.io.File

class CleaningManager {
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
        return file.delete()
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        file.listFiles()?.forEach {
            size += if (it.isDirectory) getFolderSize(it) else it.length()
        }
        return size
    }
}