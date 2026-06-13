package com.example.filefix.model

import java.io.File

data class JunkGroup(
    val title: String,
    var size: Long,
    val items: List<File> = emptyList(),
    val details: List<FileItem> = emptyList(), // Lista detallada con iconos y tamaños reales
    var isChecked: Boolean = true,
    var isExpanded: Boolean = false,
    val isAppGroup: Boolean = false
)
