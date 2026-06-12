package com.example.filefix.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filefix.R
import com.example.filefix.model.FileItem
import com.example.filefix.model.JunkGroup

class JunkGroupAdapter(
    private val groups: List<JunkGroup>,
    private val onTotalSizeChanged: () -> Unit
) : RecyclerView.Adapter<JunkGroupAdapter.GroupViewHolder>() {

    class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbGroup: CheckBox = view.findViewById(R.id.cbGroup)
        val tvTitle: TextView = view.findViewById(R.id.tvGroupTitle)
        val tvSize: TextView = view.findViewById(R.id.tvGroupSize)
        val ivExpand: ImageView = view.findViewById(R.id.ivExpand)
        val rvDetails: RecyclerView = view.findViewById(R.id.rvGroupDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_junk_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        holder.tvTitle.text = group.title
        holder.tvSize.text = formatFileSize(group.size)
        holder.cbGroup.isChecked = group.isChecked

        holder.cbGroup.setOnCheckedChangeListener { _, isChecked ->
            group.isChecked = isChecked
            onTotalSizeChanged()
        }

        holder.ivExpand.setOnClickListener {
            group.isExpanded = !group.isExpanded
            holder.rvDetails.visibility = if (group.isExpanded) View.VISIBLE else View.GONE
            holder.ivExpand.rotation = if (group.isExpanded) 180f else 0f
            
            if (group.isExpanded && holder.rvDetails.adapter == null) {
                setupDetailsList(holder.rvDetails, group)
            }
        }
    }

    private fun setupDetailsList(rv: RecyclerView, group: JunkGroup) {
        val items = group.items.map { file ->
            FileItem(
                id = file.absolutePath,
                name = file.name,
                type = if (file.isDirectory) "Carpeta" else file.extension,
                size = file.length(),
                status = "Junk",
                uri = null,
                isDirectory = file.isDirectory,
                path = file.absolutePath
            )
        }
        rv.layoutManager = LinearLayoutManager(rv.context)
        rv.adapter = FileAdapter(items) { }
    }

    override fun getItemCount() = groups.size

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0.00 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return "%.2f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
