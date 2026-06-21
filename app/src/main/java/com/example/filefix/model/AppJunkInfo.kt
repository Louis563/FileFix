package com.example.filefix.model

data class AppJunkInfo(
    val appName: String,
    val packageName: String,
    val cacheSize: Long,
    val appSize: Long = 0L
)
