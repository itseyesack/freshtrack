package com.eyesack.freshlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PantryItemAdapter(
    private val items: List<PantryItem>,
    private val onItemClick: (PantryItem) -> Unit,
    private val onItemLongClick: (PantryItem) -> Unit
) : RecyclerView.Adapter<PantryItemAdapter.PantryItemViewHolder>() {

    class PantryItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemName: TextView = view.findViewById(R.id.itemName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PantryItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.pantry_item, parent, false)
        return PantryItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: PantryItemViewHolder, position: Int) {
        val item = items[position]
        holder.itemName.text = item.name

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(item)
            true // Consume the long click
        }
    }

    override fun getItemCount() = items.size
}