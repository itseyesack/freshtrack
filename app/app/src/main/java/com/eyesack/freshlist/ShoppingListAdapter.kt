package com.eyesack.freshlist

import android.graphics.Paint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ShoppingListAdapter(
    private val items: MutableList<String>,
    private val crossedOffItems: MutableList<String>,
    private val onItemClick: (item: String, isCrossedOff: Boolean) -> Unit // Callback
) : RecyclerView.Adapter<ShoppingListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.itemTextView)

        init {
            itemView.setOnClickListener {
                val item = items[adapterPosition]
                val isCurrentlyCrossedOff = item in crossedOffItems

                if (isCurrentlyCrossedOff) {
                    crossedOffItems.remove(item)
                    moveItemToTop(item)
                } else {
                    crossedOffItems.add(item)
                    moveItemToBottom(adapterPosition)
                }
                notifyDataSetChanged()
                // Call the callback to notify MainActivity
                onItemClick(item, !isCurrentlyCrossedOff) // Pass the item and its NEW crossed-off state
            }
        }
    }

    private fun moveItemToBottom(position: Int) {
        val item = items.removeAt(position)
        items.add(item)
    }

    private fun moveItemToTop(item: String) {
        if (items.contains(item)) {
            items.remove(item)
            items.add(0, item)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shopping_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textView.text = item
        holder.textView.setTextColor(Color.WHITE)
        if (item in crossedOffItems) {
            holder.textView.paintFlags = holder.textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.textView.paintFlags = holder.textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    override fun getItemCount(): Int = items.size

    // This method is no longer needed, as the click listener handles toggling
    // private fun toggleCrossOffStatus(position: Int) { ... }
}