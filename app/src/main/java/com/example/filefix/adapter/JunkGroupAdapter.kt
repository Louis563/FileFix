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
        
        fun updateGroupSizeText() {
            val selectedSize = group.details.filter { it.isChecked }.sumOf { it.size }
            holder.tvSize.text = formatFileSize(selectedSize)
        }

        updateGroupSizeText()
        
        holder.cbGroup.setOnCheckedChangeListener(null)
        holder.cbGroup.isChecked = group.isChecked
        
        holder.cbGroup.setOnCheckedChangeListener { _, isChecked ->
            group.isChecked = isChecked
            group.details.forEach { it.isChecked = isChecked }
            updateGroupSizeText()
            onTotalSizeChanged()
            // Refrescar lista interna si está visible
            if (group.isExpanded) {
                holder.rvDetails.adapter?.notifyDataSetChanged()
            }
        }

        holder.rvDetails.visibility = if (group.isExpanded) View.VISIBLE else View.GONE
        holder.ivExpand.rotation = if (group.isExpanded) 180f else 0f
        
        if (group.isExpanded) {
            setupDetailsList(holder.rvDetails, group, holder)
        } else {
            // Importante: Limpiar el adaptador para evitar mezclas al reciclar
            holder.rvDetails.adapter = null
        }

        holder.ivExpand.setOnClickListener {
            group.isExpanded = !group.isExpanded
            notifyItemChanged(position)
        }
    }

    private fun setupDetailsList(rv: RecyclerView, group: JunkGroup, holder: GroupViewHolder) {
        // SIEMPRE asignamos un nuevo adaptador o actualizamos los datos para evitar el bug de reciclaje
        rv.adapter = FileAdapter(
            files = group.details,
            isSelectionMode = true,
            onItemCheckedChange = { _ ->
                val allChecked = group.details.all { it.isChecked }
                
                if (group.isChecked != allChecked) {
                    group.isChecked = allChecked
                    holder.cbGroup.setOnCheckedChangeListener(null)
                    holder.cbGroup.isChecked = allChecked
                    // Re-asignar el listener principal después de cambiar el estado visual
                    holder.cbGroup.setOnCheckedChangeListener { _, isChecked ->
                        group.isChecked = isChecked
                        group.details.forEach { it.isChecked = isChecked }
                        updateGroupSizeText(holder, group)
                        onTotalSizeChanged()
                        rv.adapter?.notifyDataSetChanged()
                    }
                }
                updateGroupSizeText(holder, group)
                onTotalSizeChanged()
            }
        ) { _ -> }
    }

    private fun updateGroupSizeText(holder: GroupViewHolder, group: JunkGroup) {
        val selectedSize = group.details.filter { it.isChecked }.sumOf { it.size }
        holder.tvSize.text = formatFileSize(selectedSize)
    }

    override fun getItemCount() = groups.size

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0.00 B"
        val units = arrayOf("B", "kB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1000.0)).toInt()
        return "%.2f %s".format(size / Math.pow(1000.0, digitGroups.toDouble()), units[digitGroups])
    }
}