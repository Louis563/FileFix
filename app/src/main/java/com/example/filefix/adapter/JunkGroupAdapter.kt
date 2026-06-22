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
    private val onCacheGlobalAction: () -> Unit,
    private val onCacheItemAction: (String) -> Unit,
    private val onTotalSizeChanged: () -> Unit
) : RecyclerView.Adapter<JunkGroupAdapter.GroupViewHolder>() {

    private val childAdapters = mutableMapOf<Int, FileAdapter>()

    class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cbGroup: CheckBox = view.findViewById(R.id.cbGroup)
        val tvTitle: TextView = view.findViewById(R.id.tvGroupTitle)
        val tvSize: TextView = view.findViewById(R.id.tvGroupSize)
        val ivExpand: ImageView = view.findViewById(R.id.ivExpand)
        val rvDetails: RecyclerView = view.findViewById(R.id.rvGroupDetails)
        val rlHeader: View = view.findViewById(R.id.rlHeader)

        init {
            rvDetails.layoutManager = LinearLayoutManager(view.context)
            rvDetails.itemAnimator = null 
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_junk_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        holder.tvTitle.text = group.title
        
        val isCacheGroup = group.title.contains("Caché", ignoreCase = true)

        if (isCacheGroup) {
            holder.cbGroup.visibility = View.GONE
            holder.tvSize.text = "Toca para limpieza global"
            holder.rlHeader.setOnClickListener { onCacheGlobalAction() }
        } else {
            holder.cbGroup.visibility = View.VISIBLE
            
            fun updateGroupSizeTextLocal() {
                val selectedSize = group.details.filter { it.isChecked }.sumOf { it.size }
                holder.tvSize.text = formatFileSize(selectedSize)
            }

            updateGroupSizeTextLocal()
            
            holder.cbGroup.setOnCheckedChangeListener(null)
            holder.cbGroup.isChecked = group.isChecked
            
            holder.cbGroup.setOnCheckedChangeListener { _, isChecked ->
                group.isChecked = isChecked
                group.details.forEach { it.isChecked = isChecked }
                updateGroupSizeTextLocal()
                onTotalSizeChanged()
                childAdapters[position]?.notifyDataSetChanged()
            }
            holder.rlHeader.setOnClickListener(null)
        }

        holder.rvDetails.visibility = if (group.isExpanded) View.VISIBLE else View.GONE
        holder.ivExpand.rotation = if (group.isExpanded) 180f else 0f
        
        if (group.isExpanded) {
            val adapter = childAdapters.getOrPut(position) {
                FileAdapter(
                    files = group.details,
                    isSelectionMode = !isCacheGroup,
                    onItemCheckedChange = { _ ->
                        val allChecked = group.details.all { it.isChecked }
                        if (group.isChecked != allChecked) {
                            group.isChecked = allChecked
                            holder.cbGroup.setOnCheckedChangeListener(null)
                            holder.cbGroup.isChecked = allChecked
                            holder.cbGroup.setOnCheckedChangeListener { _, isChecked ->
                                group.isChecked = isChecked
                                group.details.forEach { it.isChecked = isChecked }
                                val s = group.details.filter { it.isChecked }.sumOf { it.size }
                                holder.tvSize.text = formatFileSize(s)
                                onTotalSizeChanged()
                                childAdapters[position]?.notifyDataSetChanged()
                            }
                        }
                        val s = group.details.filter { it.isChecked }.sumOf { it.size }
                        holder.tvSize.text = formatFileSize(s)
                        onTotalSizeChanged()
                    }
                ) { file ->
                    if (isCacheGroup) onCacheItemAction(file.id)
                }
            }
            if (holder.rvDetails.adapter != adapter) {
                holder.rvDetails.adapter = adapter
            }
        } else {
            holder.rvDetails.adapter = null
        }

        holder.ivExpand.setOnClickListener {
            group.isExpanded = !group.isExpanded
            notifyItemChanged(position)
        }
    }

    fun updateGroupDetails(position: Int, newDetails: List<FileItem>) {
        if (position in groups.indices) {
            val group = groups[position]
            group.details.clear()
            group.details.addAll(newDetails)
            group.size = newDetails.sumOf { it.size }
            
            // Actualizar el adaptador hijo si existe
            childAdapters[position]?.updateFiles(newDetails)
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = groups.size

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0.00 B"
        val units = arrayOf("B", "kB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1000.0)).toInt()
        return "%.2f %s".format(size / Math.pow(1000.0, digitGroups.toDouble()), units[digitGroups])
    }
}
