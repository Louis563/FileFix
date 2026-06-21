package com.example.filefix.adapter

import android.graphics.Bitmap
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.filefix.R
import com.example.filefix.model.FileItem
import com.example.filefix.FileScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileAdapter(
    private var files: List<FileItem>,
    private val isSelectionMode: Boolean = false,
    private val onItemCheckedChange: ((FileItem) -> Unit)? = null,
    private val onItemClick: (FileItem) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    private lateinit var fileScanner: FileScanner

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.txtFileName)
        val fileType: TextView = view.findViewById(R.id.txtFileType)
        val fileSize: TextView = view.findViewById(R.id.txtFileSize)
        val fileIcon: ImageView = view.findViewById(R.id.imgFileIcon)
        val cbSelect: CheckBox = view.findViewById(R.id.cbFileSelect)
        val root: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        if (!::fileScanner.isInitialized) {
            fileScanner = FileScanner(parent.context)
        }
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.fileName.text = file.name
        holder.fileType.text = getFriendlyTypeName(file)
        
        if (file.isDirectory) {
            holder.fileSize.text = ""
        } else {
            holder.fileSize.text = formatFileSize(file.size)
        }

        if (isSelectionMode) {
            holder.cbSelect.visibility = View.VISIBLE
            holder.cbSelect.setOnCheckedChangeListener(null)
            holder.cbSelect.isChecked = file.isChecked
            holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                file.isChecked = isChecked
                onItemCheckedChange?.invoke(file)
            }
        } else {
            holder.cbSelect.visibility = View.GONE
            if (file.status == "Installed") {
                holder.fileSize.text = "${formatFileSize(file.size)} - Toca para limpiar"
            }
        }

        holder.fileIcon.setImageResource(R.drawable.ic_launcher_foreground) 
        holder.fileIcon.clearColorFilter()
        
        if (file.isDirectory) {
            holder.fileIcon.setImageResource(R.drawable.ic_folder)
        } else {
            val isMedia = file.type.startsWith("image") || file.type.startsWith("video")
            val isInstalledApp = file.status == "Installed"

            if (isInstalledApp) {
                loadAppIcon(holder.fileIcon, file.path)
            } else if (isMedia) {
                loadThumbnailOnDemand(holder.fileIcon, file)
            } else {
                val iconRes = when {
                    file.type.contains("pdf") -> R.drawable.ic_pdf
                    file.type.contains("word") || file.type.contains("officedocument.word") -> R.drawable.ic_word
                    file.type.contains("excel") || file.type.contains("officedocument.sheet") -> R.drawable.ic_excel
                    file.type.contains("powerpoint") || file.type.contains("officedocument.presentation") -> R.drawable.ic_ppt
                    file.type.contains("text") -> R.drawable.ic_txt
                    file.type.contains("audio") -> android.R.drawable.ic_lock_silent_mode_off
                    else -> android.R.drawable.ic_menu_save
                }
                holder.fileIcon.setImageResource(iconRes)
            }
        }

        holder.root.setOnClickListener { onItemClick(file) }
    }

    private fun loadAppIcon(imageView: ImageView, packageName: String) {
        try {
            val pm = imageView.context.packageManager
            val icon = pm.getApplicationIcon(packageName)
            imageView.setImageDrawable(icon)
        } catch (_: Exception) {
            imageView.setImageResource(android.R.drawable.sym_def_app_icon)
        }
    }

    private fun getFriendlyTypeName(file: FileItem): String {
        if (file.isDirectory) return "Carpeta"
        if (file.status == "Installed") return "Aplicación"
        val mime = file.type.lowercase()
        val extension = file.name.substringAfterLast(".", "").uppercase()

        return when {
            mime.contains("pdf") -> "PDF"
            mime.contains("word") || mime.contains("officedocument.word") -> "WORD"
            mime.contains("excel") || mime.contains("officedocument.sheet") -> "EXCEL"
            mime.contains("powerpoint") || mime.contains("officedocument.presentation") -> "PPT"
            mime.contains("image") -> mime.split("/").last().uppercase()
            mime.contains("video") -> mime.split("/").last().uppercase()
            mime.contains("audio") -> mime.split("/").last().uppercase()
            mime.contains("zip") || mime.contains("rar") || mime.contains("compressed") -> "COMPRIMIDO"
            mime.contains("text") -> "TEXTO"
            extension.isNotEmpty() && extension.length <= 4 -> extension
            else -> {
                val lastPart = mime.split("/").last().uppercase()
                if (lastPart.length > 8 || lastPart.contains("OCTET")) {
                    if (extension.isNotEmpty() && extension.length <= 5) extension else "ARCHIVO"
                } else lastPart
            }
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0.00 B"
        val units = arrayOf("B", "kB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1000.0)).toInt()
        return "%.2f %s".format(size / Math.pow(1000.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun loadThumbnailOnDemand(imageView: ImageView, file: FileItem) {
        CoroutineScope(Dispatchers.IO).launch {
            val uri = file.uri ?: fileScanner.getMediaStoreUri(file.path, file.type)
            if (uri != null) {
                try {
                    val bitmap: Bitmap? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        imageView.context.contentResolver.loadThumbnail(uri, Size(100, 100), null)
                    } else null
                    
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun getItemCount() = files.size

    fun updateFiles(newFiles: List<FileItem>) {
        files = newFiles
        notifyDataSetChanged()
    }
}