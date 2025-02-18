package com.eyesack.freshlist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


data class PantryItem(val name: String, val category: String, val details: Map<String, String>)

class PantryActivity : AppCompatActivity() { // Correctly extending AppCompatActivity

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PantryItemAdapter
    private var pantryItems = mutableListOf<PantryItem>()
    private val sharedPreferencesName = "pantry_prefs"
    private val pantryKey = "pantry_items"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantry)

        recyclerView = findViewById(R.id.pantryRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3) // 3 columns

        loadPantryData()

        adapter = PantryItemAdapter(pantryItems,
            { item -> showItemDetails(item) }, // Click listener
            { item -> showDeleteConfirmation(item) } // Long-click listener
        )
        recyclerView.adapter = adapter
    }

    private fun loadPantryData() {
        val sharedPreferences = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val json = sharedPreferences.getString(pantryKey, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<PantryItem>>() {}.type
            pantryItems.clear() // Clear existing data before loading
            pantryItems.addAll(Gson().fromJson(json, type))
        }
    }
    private fun savePantryData() {
        val sharedPreferences = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val json = Gson().toJson(pantryItems)
        editor.putString(pantryKey, json)
        editor.apply()
    }

    private fun showItemDetails(item: PantryItem) {
        val intent = Intent(this, PantryItemDetailsActivity::class.java)
        intent.putExtra("name", item.name)
        intent.putExtra("category", item.category)
        // Convert details map to a string format for easy passing via Intent
        val detailsString = item.details.map { "${it.key}: ${it.value}" }.joinToString("\n")
        intent.putExtra("details", detailsString)
        startActivity(intent)
    }


    private fun showDeleteConfirmation(item: PantryItem) {
        AlertDialog.Builder(this) // Corrected AlertDialog.Builder context
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete ${item.name}?")
            .setPositiveButton("Delete") { _, _ ->
                pantryItems.remove(item)
                adapter.notifyDataSetChanged()
                savePantryData()
                Toast.makeText(this, "${item.name} deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadPantryData() // Reload data to reflect any changes from CameraActivity
        adapter.notifyDataSetChanged()
    }
}