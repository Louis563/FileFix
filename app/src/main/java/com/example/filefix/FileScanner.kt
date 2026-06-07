package com.example.filefix

import android.content.Context
import android.provider.MediaStore
import com.example.filefix.model.FileItem
import android.webkit.MimeTypeMap
import java.io.File
import android.os.Environment
import android.content.pm.PackageManager
import android.app.usage.StorageStatsManager
import android.os.storage.StorageManager
import android.os.Process
import java.util.UUID

class FileScanner(private val context: Context) {

    fun countAudios(): Int = countMedia(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    fun countVideos(): Int = countMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    fun countImages(): Int = countMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    
    fun countDocuments(): Int {
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = ("(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/pdf' OR " +
                        "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/msword' OR " +
                        "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/vnd.openxmlformats-officedocument%' OR " +
                        "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'text/plain')")
        
        return queryCount(MediaStore.Files.getContentUri("external"), projection, selection, null)
    }

    fun countApps(): Int {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.count { pm.getLaunchIntentForPackage(it.packageName) != null }
    }

    fun countDownloads(): Int {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return downloadDir.listFiles()?.count { !it.isDirectory } ?: 0
    }

    private fun countMedia(uri: android.net.Uri): Int {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        return queryCount(uri, projection, null, null)
    }

    private fun queryCount(
        uri: android.net.Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
        val count = cursor?.count ?: 0
        cursor?.close()
        return count
    }

    fun listFiles(directoryPath: String): List<FileItem> {
        val fileList = mutableListOf<FileItem>()
        val root = File(directoryPath)
        val files = root.listFiles()

        files?.forEach { file ->
            val isDir = file.isDirectory
            val mimeType = getMimeType(file)

            fileList.add(
                FileItem(
                    id = file.absolutePath,
                    name = file.name,
                    type = if (isDir) "Directorio" else mimeType,
                    size = if (isDir) 0 else file.length(),
                    status = "Local",
                    uri = null, 
                    isDirectory = isDir,
                    path = file.absolutePath
                )
            )
        }
        return fileList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun getMimeType(file: File): String {
        if (file.isDirectory) return "Directorio"
        val extension = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        return mime ?: "application/octet-stream"
    }

    fun getMediaStoreUri(filePath: String, mimeType: String): android.net.Uri? {
        val contentUri = when {
            mimeType.startsWith("image") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith("video") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith("audio") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> null
        } ?: return null

        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DATA} = ?"
        val selectionArgs = arrayOf(filePath)

        return try {
            context.contentResolver.query(contentUri, projection, selection, selectionArgs, null)?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    android.content.ContentUris.withAppendedId(contentUri, id)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun searchFiles(query: String): List<FileItem> {
        val fileList = mutableListOf<FileItem>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA
        )

        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        context.contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn) ?: "Unknown"
                val mime = it.getString(mimeColumn) ?: ""
                val size = it.getLong(sizeColumn)
                val path = it.getString(pathColumn)
                val contentUri = android.content.ContentUris.withAppendedId(uri, id)

                fileList.add(FileItem(id.toString(), name, mime, size, "Local", contentUri, false, path))
            }
        }
        return fileList
    }

    fun getFilesByType(typeFilter: String): List<FileItem> {
        if (typeFilter == "APPS") {
            return getInstalledApps()
        }

        val fileList = mutableListOf<FileItem>()
        val uri = when (typeFilter) {
            "AUDIO" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            "VIDEO" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            "IMAGE" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA
        )

        val selection = when (typeFilter) {
            "DOCS" -> ("(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/pdf' OR " +
                      "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/msword' OR " +
                      "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/vnd.openxmlformats-officedocument%' OR " +
                      "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'text/plain')")
            "DOWNLOADS" -> "${MediaStore.Files.FileColumns.DATA} LIKE ?"
            else -> null
        }
        
        val selectionArgs = if (typeFilter == "DOWNLOADS") {
            arrayOf("%" + Environment.DIRECTORY_DOWNLOADS + "%")
        } else null

        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn) ?: "Unknown"
                val mime = it.getString(mimeColumn) ?: ""
                val size = it.getLong(sizeColumn)
                val path = it.getString(pathColumn)
                
                val contentUri = android.content.ContentUris.withAppendedId(uri, id)

                fileList.add(FileItem(id.toString(), name, mime, size, "Local", contentUri, false, path))
            }
        }
        return fileList
    }

    private fun getInstalledApps(): List<FileItem> {
        val appList = mutableListOf<FileItem>()
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        val user = Process.myUserHandle()

        for (app in apps) {
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                val name = pm.getApplicationLabel(app).toString()
                
                var totalSize = File(app.sourceDir).length()
                
                // Intentar obtener el tamaño real (incluyendo datos) si tenemos el permiso
                if (storageStatsManager != null) {
                    try {
                        val stats = storageStatsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, user)
                        totalSize = stats.appBytes + stats.dataBytes + stats.cacheBytes
                    } catch (_: Exception) {
                        // Si no hay permiso o falla, mantenemos el tamaño del APK base
                    }
                }
                
                appList.add(FileItem(
                    id = app.packageName,
                    name = name,
                    type = "application/vnd.android.package-archive",
                    size = totalSize,
                    status = "Installed",
                    uri = null,
                    isDirectory = false,
                    path = app.packageName 
                ))
            }
        }
        return appList.sortedByDescending { it.size }
    }
}