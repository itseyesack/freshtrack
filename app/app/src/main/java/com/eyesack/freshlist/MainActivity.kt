package com.eyesack.freshlist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerViewAdapter: ShoppingListAdapter
    private val shoppingListItems = mutableListOf<String>()
    private val crossedOffItems = mutableListOf<String>()
    private val sharedPreferencesName = "shopping_list_prefs"
    private val listKey = "shopping_list"
    private lateinit var setEndpointButton: Button
    private lateinit var addButton: Button // Add Button reference
    private lateinit var listButton: Button // Add Button reference
    private lateinit var pantryButton: Button
    private var endpoint: String = "http://10.0.0.116:8000/process-images/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadEndpoint()

        val cameraButton = findViewById<Button>(R.id.cameraButton)
        addButton = findViewById(R.id.addButton) // Initialize addButton
        listButton = findViewById(R.id.listButton)
        pantryButton = findViewById(R.id.pantryButton)
        setEndpointButton = findViewById(R.id.setEndpointButton) // Initialize setEndpointButton

        cameraButton.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java).apply {
                putExtra("use_image_picker", false)
                putExtra("endpoint", endpoint)
            })
        }
        cameraButton.setOnLongClickListener {
            startActivity(Intent(this, CameraActivity::class.java).apply {
                putExtra("use_image_picker", true)
                putExtra("endpoint", endpoint)
            })
            true
        }

        setEndpointButton.setOnClickListener { showSetEndpointDialog() }
        pantryButton.setOnClickListener {
            val intent = Intent(this, PantryActivity::class.java)
            startActivity(intent)
        }
        listButton.setOnClickListener{
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        loadData()

        recyclerViewAdapter = ShoppingListAdapter(shoppingListItems, crossedOffItems)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = recyclerViewAdapter


        addButton.setOnClickListener {
            val newItem = findViewById<EditText>(R.id.itemInput).text.toString().trim()
            if (newItem.isNotEmpty()) {
                val existingIndex = shoppingListItems.indexOf(newItem)
                if (existingIndex != -1) {
                    if (newItem in crossedOffItems) {
                        crossedOffItems.remove(newItem)
                    }
                    shoppingListItems.removeAt(existingIndex)
                }
                shoppingListItems.add(0, newItem)
                recyclerViewAdapter.notifyDataSetChanged()
                recyclerView.scrollToPosition(0)
                findViewById<EditText>(R.id.itemInput).text.clear()
                saveData()
            }
        }

        // Swipe to remove
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = shoppingListItems[position]
                shoppingListItems.removeAt(position)
                crossedOffItems.remove(item)
                recyclerViewAdapter.notifyItemRemoved(position)
                saveData()
            }
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
        handleIncomingCrossedOffItems()

        // Initially, show the "Add" button and hide the "List" button
        showAddButton()
    }
    private fun showSetEndpointDialog() {
        val editText = EditText(this)
        editText.setText(endpoint)
        AlertDialog.Builder(this)
            .setTitle("Set Endpoint")
            .setMessage("Enter the server endpoint URL:")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                endpoint = editText.text.toString().trim()
                saveEndpoint()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveEndpoint() {
        val sharedPreferences = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("endpoint", endpoint).apply()
    }
    private fun loadEndpoint() {
        val sharedPreferences = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        endpoint = sharedPreferences.getString("endpoint", endpoint) ?: endpoint
    }

    fun saveData() {
        val sharedPreferences = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        editor.putString(listKey, gson.toJson(shoppingListItems))
        editor.putString("crossed_off_items", gson.toJson(crossedOffItems.distinct()))
        editor.apply()
    }
    private fun showAddButton() {
        addButton.visibility = View.VISIBLE
        listButton.visibility = View.GONE
    }

    private fun showListButton() {
        addButton.visibility = View.GONE
        listButton.visibility = View.VISIBLE
    }
    private fun loadData() {
        val sharedPreferences = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val gson = Gson()
        sharedPreferences.getString(listKey, null)?.let {
            shoppingListItems.addAll(gson.fromJson(it, object : TypeToken<MutableList<String>>() {}.type))
        }
        sharedPreferences.getString("crossed_off_items", null)?.let {
            crossedOffItems.addAll(gson.fromJson(it, object : TypeToken<List<String>>() {}.type))
        }
    }
    private fun handleIncomingCrossedOffItems() {
        intent.getStringArrayListExtra("items_to_cross_off")?.let { itemsToCrossOff ->
            itemsToCrossOff.forEach { item ->
                if (item !in crossedOffItems) {
                    crossedOffItems.add(item)
                    val index = shoppingListItems.indexOf(item)
                    if (index != -1) {
                        shoppingListItems.removeAt(index)
                        shoppingListItems.add(item)
                    }
                }
            }
            recyclerViewAdapter.notifyDataSetChanged()
            saveData()
        }
    }

    override fun onResume() {
        super.onResume()
        handleIncomingCrossedOffItems()
        // Show the "List" button when returning from PantryActivity
        if (intent.getBooleanExtra("from_pantry", false)) {
            showListButton()
        } else {
            showAddButton() // Ensure Add button is shown otherwise
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)  //very important
        handleIncomingCrossedOffItems() // Handle potential new crossed-off items
    }
}