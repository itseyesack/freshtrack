package com.eyesack.freshlist

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity // Correctly using AppCompatActivity

class PantryItemDetailsActivity : AppCompatActivity() { // Correctly extending AppCompatActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantry_item_details)

        val itemName = intent.getStringExtra("name")
        val itemCategory = intent.getStringExtra("category")
        val itemDetails = intent.getStringExtra("details")

        findViewById<TextView>(R.id.itemNameTextView).text = itemName
        findViewById<TextView>(R.id.itemCategoryTextView).text = itemCategory
        findViewById<TextView>(R.id.itemDetailsTextView).text = itemDetails
    }
}