package com.example.filefix.adapter

import android.graphics.Bitmap
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.filefix.R
import com.example.filefix.model.FileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileAdapter(private var files: List<FileItem>) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.txtFileName)
        val fileType: TextView = view.findViewById(R.id.txtFileType)
        val fileSize: TextView = view.findViewById(R.id.txtFileSize)
        val fileIcon: ImageView = view.findViewById(R.id.imgFileIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.fileName.text = file.name
        holder.fileType.text = "Tipo: ${file.type.split("/").last().uppercase()}"
        
        // Convertir tamaño a MB si es grande
        val sizeMB = file.size / (1024.0 * 1024.0)
        holder.fileSize.text = if (sizeMB > 1) "%.2f MB".format(sizeMB) else "${file.size / 1024} KB"

        // Cargar Icono o Miniatura
        holder.fileIcon.setImageResource(R.drawable.ic_launcher_foreground) // Default
        
        if (file.uri != null && (file.type.startsWith("image") || file.type.startsWith("video"))) {
            loadThumbnail(holder.fileIcon, file)
        } else {
            // Icono según tipo
            val iconRes = when {
                file.type.contains("pdf") -> android.R.drawable.ic_menu_agenda
                file.type.contains("audio") -> android.R.drawable.ic_lock_silent_mode_off
                file.isDirectory -> android.R.drawable.ic_menu_save
                else -> android.R.drawable.ic_menu_save
            }
            holder.fileIcon.setImageResource(iconRes)
        }
    }

    private fun loadThumbnail(imageView: ImageView, file: FileItem) {
        val context = imageView.context
        val uri = file.uri ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap: Bitmap? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(100, 100), null)
                } else {
                    null // Aquí se podría implementar para versiones viejas, pero la mayoría son 10+
                }
                
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Exception) {
                // Error cargando miniatura
            }
        }
    }

    override fun getItemCount() = files.size

    fun updateFiles(newFiles: List<FileItem>) {
        files = newFiles
        notifyDataSetChanged()
    }
}