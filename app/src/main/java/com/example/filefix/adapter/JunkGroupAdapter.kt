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
        
        // Actualizar tamaño basado en lo seleccionado
        val selectedSize = group.details.filter { it.isChecked }.sumOf { it.size }
        holder.tvSize.text = formatFileSize(selectedSize)
        
        holder.cbGroup.setOnCheckedChangeListener(null)
        holder.cbGroup.isChecked = group.isChecked
        
        holder.cbGroup.setOnCheckedChangeListener { _, isChecked ->
            group.isChecked = isChecked
            group.details.forEach { it.isChecked = isChecked }
            onTotalSizeChanged()
            notifyItemChanged(position)
        }

        // CONTROL DE VISIBILIDAD CRÍTICO: Evita mezclas visuales al reciclar
        holder.rvDetails.visibility = if (group.isExpanded) View.VISIBLE else View.GONE
        holder.ivExpand.rotation = if (group.isExpanded) 180f else 0f
        
        if (group.isExpanded) {
            setupDetailsList(holder.rvDetails, group, position)
        } else {
            holder.rvDetails.adapter = null // Limpiar adaptador al cerrar para evitar fugas visuales
        }

        holder.ivExpand.setOnClickListener {
            group.isExpanded = !group.isExpanded
            notifyItemChanged(position)
        }
    }

    private fun setupDetailsList(rv: RecyclerView, group: JunkGroup, parentPosition: Int) {
        // Siempre usamos un adaptador nuevo o actualizamos los datos para evitar que se mezclen con otras filas
        rv.layoutManager = LinearLayoutManager(rv.context)
        rv.adapter = FileAdapter(
            files = group.details,
            isSelectionMode = true,
            onItemCheckedChange = { _ ->
                // Actualizar estado del padre
                val allChecked = group.details.all { it.isChecked }
                if (group.isChecked != allChecked) {
                    group.isChecked = allChecked
                }
                onTotalSizeChanged()
                notifyItemChanged(parentPosition)
            }
        ) { _ -> }
    }

    override fun getItemCount() = groups.size

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0.00 B"
        val units = arrayOf("B", "kB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1000.0)).toInt()
        return "%.2f %s".format(size / Math.pow(1000.0, digitGroups.toDouble()), units[digitGroups])
    }
}