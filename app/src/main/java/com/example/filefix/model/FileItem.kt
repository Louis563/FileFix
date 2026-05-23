package com.example.filefix.model

import android.net.Uri

data class FileItem(
    val id: String,
    val name: String,
    val type: String,
    val size: Long,
    val status: String,
    val uri: Uri? = null,
    val isDirectory: Boolean = false
)