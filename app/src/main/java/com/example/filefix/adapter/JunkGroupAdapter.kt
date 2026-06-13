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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_junk_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        holder.tvTitle.text = group.title
        
        // Función interna para actualizar solo el texto del tamaño del grupo
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
            
            // Notificar cambio al padre para que se vea el check sincronizado
            // Pero tratamos de no re-renderizar todo si es posible
            updateGroupSizeText()
            onTotalSizeChanged()
            
            // Si está expandido, debemos avisar a la lista interna
            if (group.isExpanded) {
                holder.rvDetails.adapter?.notifyDataSetChanged()
            }
        }

        holder.rvDetails.visibility = if (group.isExpanded) View.VISIBLE else View.GONE
        holder.ivExpand.rotation = if (group.isExpanded) 180f else 0f
        
        if (group.isExpanded) {
            setupDetailsList(holder.rvDetails, group, holder)
        } else {
            holder.rvDetails.adapter = null
        }

        holder.ivExpand.setOnClickListener {
            group.isExpanded = !group.isExpanded
            notifyItemChanged(position)
        }
    }

    private fun setupDetailsList(rv: RecyclerView, group: JunkGroup, holder: GroupViewHolder) {
        if (rv.adapter == null) {
            rv.layoutManager = LinearLayoutManager(rv.context)
            rv.adapter = FileAdapter(
                files = group.details,
                isSelectionMode = true,
                onItemCheckedChange = { _ ->
                    val allChecked = group.details.all { it.isChecked }
                    
                    // Actualizar estado del padre sin re-renderizar toda la fila si es posible
                    group.isChecked = allChecked
                    holder.cbGroup.setOnCheckedChangeListener(null)
                    holder.cbGroup.isChecked = allChecked
                    holder.cbGroup.setOnCheckedChangeListener { _, isChecked ->
                        group.isChecked = isChecked
                        group.details.forEach { it.isChecked = isChecked }
                        val selectedSize = group.details.filter { it.isChecked }.sumOf { it.size }
                        holder.tvSize.text = formatFileSize(selectedSize)
                        onTotalSizeChanged()
                        rv.adapter?.notifyDataSetChanged()
                    }

                    val selectedSize = group.details.filter { it.isChecked }.sumOf { it.size }
                    holder.tvSize.text = formatFileSize(selectedSize)
                    onTotalSizeChanged()
                }
            ) { _ -> }
        } else {
            rv.adapter?.notifyDataSetChanged()
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