package com.example.filefix.model

data class FileItem(
    val id: String,
    val name: String,
    val type: String,
    val size: Long,
    val status: String
)