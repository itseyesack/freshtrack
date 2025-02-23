package com.eyesack.freshlist

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

class MainActivity : AppCompatActivity(), ImageHandlerListener, ApiCallback {

    private lateinit var recyclerViewAdapter: ShoppingListAdapter
    private lateinit var addButton: Button
    private lateinit var listButton: Button
    private lateinit var pantryButton: Button
    private lateinit var authentication: FirebaseAuth
    private lateinit var pantryRecyclerView: RecyclerView
    private lateinit var pantryAdapter: PantryItemAdapter
    private lateinit var contentFrameLayout: FrameLayout
    private lateinit var listRecyclerView: RecyclerView
    private lateinit var imageView: ImageView
    private lateinit var imageCardView: CardView
    private lateinit var imageHandler: ImageHandler
    private lateinit var apiClient: ApiClient
    private lateinit var pantryDataManager: PantryDataManager
    private lateinit var shoppingListDataManager: ShoppingListDataManager
    private val sharedPreferencesName = "shopping_list_prefs" // Add this for SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageHandler = ImageHandler(this, this)
        apiClient = ApiClient(this)
        pantryDataManager = PantryDataManager(this)
        shoppingListDataManager = ShoppingListDataManager(this)
        authentication = FirebaseAuth.getInstance()

        val profileButton: Button = findViewById(R.id.btnProfile)
        profileButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        val cameraButton = findViewById<Button>(R.id.cameraButton)
        addButton = findViewById(R.id.addButton)
        listButton = findViewById(R.id.listButton)
        pantryButton = findViewById(R.id.pantryButton)
        contentFrameLayout = findViewById(R.id.contentFrameLayout)
        listRecyclerView = findViewById(R.id.listRecyclerView)
        pantryRecyclerView = findViewById(R.id.pantryRecyclerView)
        imageCardView = CardView(this).apply {
            radius = 16f
            cardElevation = 8f
            visibility = View.GONE
        }
        imageView = ImageView(this)
        imageCardView.addView(imageView)

        cameraButton.setOnClickListener { imageHandler.takePicture() }
        cameraButton.setOnLongClickListener {
            imageHandler.pickImageFromGallery()
            true
        }
        pantryButton.setOnClickListener { showPantryView() }
        listButton.setOnClickListener { showShoppingListView() }

        initShoppingListView()
        initPantryView()
        showShoppingListView() // Default to shopping list view

        shoppingListDataManager.handleIncomingCrossedOffItems(intent.getStringArrayListExtra("items_to_cross_off"))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        imageHandler.handleActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        imageHandler.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun initShoppingListView() {
        recyclerViewAdapter = ShoppingListAdapter(
            shoppingListDataManager.shoppingListItems,
            shoppingListDataManager.crossedOffItems
        ) { item, isCrossedOff ->
            // This is the callback implementation
            if (isCrossedOff) {
                shoppingListDataManager.crossOffItems(listOf(item))
            } else {
                // UNcross the item.  We now have a dedicated method for this.
                shoppingListDataManager.unCrossOffItem(item)
            }
        }
        listRecyclerView.layoutManager = LinearLayoutManager(this)
        listRecyclerView.adapter = recyclerViewAdapter

        addButton.setOnClickListener {
            val newItem = findViewById<EditText>(R.id.itemInput).text.toString().trim()
            shoppingListDataManager.addItem(newItem)
            recyclerViewAdapter.notifyDataSetChanged()  // Update the adapter
            listRecyclerView.scrollToPosition(0) // Scroll to top
            findViewById<EditText>(R.id.itemInput).text.clear() // Clear the input
        }

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                shoppingListDataManager.removeItem(position)
                recyclerViewAdapter.notifyItemRemoved(position) // Update adapter
            }
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(listRecyclerView)
    }

    private fun initPantryView() {
        pantryRecyclerView.layoutManager = GridLayoutManager(this@MainActivity, 3)
        pantryAdapter = PantryItemAdapter(pantryDataManager.pantryItems,
            { item -> showItemDetails(item) },
            { item -> showDeleteConfirmation(item) })
        pantryRecyclerView.adapter = pantryAdapter
    }

    private fun showShoppingListView() {
        contentFrameLayout.removeAllViews()
        contentFrameLayout.addView(listRecyclerView)
        findViewById<View>(R.id.addConstraint).visibility = View.VISIBLE
        addButton.visibility = View.VISIBLE
        listRecyclerView.visibility = View.VISIBLE
        hideImageView()
    }
    private fun showPantryView() {
        contentFrameLayout.removeAllViews()
        contentFrameLayout.addView(pantryRecyclerView)
        findViewById<View>(R.id.addConstraint).visibility = View.GONE
        addButton.visibility = View.GONE
        pantryRecyclerView.visibility = View.VISIBLE
        hideImageView()
    }
    private fun showImageView(bitmap: Bitmap) {
        contentFrameLayout.removeAllViews()
        imageView.setImageBitmap(bitmap)
        imageView.adjustViewBounds = true
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        val cardLayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(32, 32, 32, 32) // Add some margin
        }
        imageCardView.layoutParams = cardLayoutParams
        contentFrameLayout.addView(imageCardView)
        imageCardView.visibility = View.VISIBLE
        findViewById<View>(R.id.addConstraint).visibility = View.GONE
        addButton.visibility = View.GONE
    }
    private fun hideImageView() {
        imageCardView.visibility = View.GONE
        if (imageCardView.parent != null) {
            contentFrameLayout.removeView(imageCardView)
        }
    }
    private fun showJsonPopup(jsonResponse: String, onDismiss: () -> Unit) {
        val textView = TextView(this).apply {
            text = jsonResponse
            setPadding(16, 16, 16, 16) // Add some padding
        }
        AlertDialog.Builder(this)
            .setTitle("JSON Response")
            .setView(ScrollView(this).apply { addView(textView) }) // Make it scrollable
            .setPositiveButton("OK") { dialog, _->
                dialog.dismiss()
                onDismiss()
            }
            .show()
    }
    private fun showItemDetails(item: PantryItem) {
        val intent = Intent(this, PantryItemDetailsActivity::class.java)
        intent.putExtra("name", item.name)
        intent.putExtra("category", item.category)
        val detailsString = item.details.map { "${it.key}: ${it.value}" }.joinToString("\n")
        intent.putExtra("details", detailsString)
        startActivity(intent)
    }

    private fun showDeleteConfirmation(item: PantryItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete ${item.name}?")
            .setPositiveButton("Delete") { _, _ ->
                pantryDataManager.deleteItem(item)
                pantryAdapter.notifyDataSetChanged()
                Toast.makeText(this, "${item.name} deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        shoppingListDataManager.handleIncomingCrossedOffItems(intent.getStringArrayListExtra("items_to_cross_off"))
        if (::pantryAdapter.isInitialized) {
            pantryAdapter.notifyDataSetChanged()
        }
    }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Very important: update the intent
        intent?.getStringArrayListExtra("items_to_cross_off")?.let { itemsToCrossOff ->
            shoppingListDataManager.handleIncomingCrossedOffItems(itemsToCrossOff)
        }
    }
    // ImageHandlerListener callbacks
    override fun onImageCaptured(bitmap: Bitmap) {
        showImageView(bitmap)
        apiClient.sendImage(bitmap, shoppingListDataManager.shoppingListItems, this)
    }

    override fun onImagePicked(bitmap: Bitmap) {
        showImageView(bitmap)
        apiClient.sendImage(bitmap, shoppingListDataManager.shoppingListItems, this)
    }

    override fun onImageError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    // ApiCallback callbacks
    override fun onSuccess(itemsToRemove: List<String>, jsonResponse: String) {
        showPantryView() // Switch to pantry view after getting the response
        val sharedPreferences = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val isDebugMode = sharedPreferences.getBoolean("debug_mode", false)

        if (isDebugMode) { // Only show popup if debug mode is enabled
            if (itemsToRemove.isNotEmpty()) {
                showJsonPopup(jsonResponse) { //show popup, cross off on dismiss
                    shoppingListDataManager.crossOffItems(itemsToRemove)
                    recyclerViewAdapter.notifyDataSetChanged()
                }
            } else {
                Toast.makeText(this@MainActivity, "No matching items found.", Toast.LENGTH_SHORT).show()
            }
        } else {
            //if not debug, just cross items
            shoppingListDataManager.crossOffItems(itemsToRemove)
            recyclerViewAdapter.notifyDataSetChanged()

            if (itemsToRemove.isEmpty()) {
                Toast.makeText(this@MainActivity, "No matching items found.", Toast.LENGTH_SHORT).show()
            }
        }
        pantryDataManager.updatePantryItems(JSONObject(jsonResponse)) //parse and add to pantry
        pantryAdapter.notifyDataSetChanged() // Notify adapter of the changed data
    }

    override fun onFailure(message: String) {
        showPantryView()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}