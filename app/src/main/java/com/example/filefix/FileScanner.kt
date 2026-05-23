package com.example.filefix

import android.content.Context
import android.provider.MediaStore
import com.example.filefix.model.FileItem

class FileScanner(private val context: Context) {

    fun countAudios(): Int = countMedia(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    fun countVideos(): Int = countMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    fun countImages(): Int = countMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    
    fun countDocuments(): Int {
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("application/pdf", "application/msword")
        
        return queryCount(MediaStore.Files.getContentUri("external"), projection, selection, selectionArgs)
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

    fun getAllFiles(): List<FileItem> {
        val fileList = mutableListOf<FileItem>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE
        )

        val cursor = context.contentResolver.query(uri, projection, null, null, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn) ?: "Unknown"
                val mime = it.getString(mimeColumn) ?: ""
                val size = it.getLong(sizeColumn)
                
                val contentUri = android.content.ContentUris.withAppendedId(
                    if (mime.startsWith("image")) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    else if (mime.startsWith("video")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Files.getContentUri("external"),
                    id
                )

                fileList.add(FileItem(id.toString(), name, mime, size, "Local", contentUri, false))
            }
        }
        return fileList
    }
}