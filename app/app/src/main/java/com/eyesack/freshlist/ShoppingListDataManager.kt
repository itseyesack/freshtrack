package com.eyesack.freshlist

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ShoppingListDataManager(private val context: Context) {

    val shoppingListItems = mutableListOf<String>()
    val crossedOffItems = mutableListOf<String>()
    private val sharedPreferencesName = "shopping_list_prefs"
    private val listKey = "shopping_list"

    init {
        loadData()
    }

    fun addItem(newItem: String) {
        if (newItem.isNotEmpty()) {
            val existingIndex = shoppingListItems.indexOf(newItem)
            if (existingIndex != -1) {
                if (newItem in crossedOffItems) {
                    crossedOffItems.remove(newItem)
                }
                shoppingListItems.removeAt(existingIndex)
            }
            shoppingListItems.add(0, newItem)
            saveData()
        }
    }

    fun removeItem(position: Int) {
        val item = shoppingListItems[position]
        shoppingListItems.removeAt(position)
        crossedOffItems.remove(item)
        saveData()
    }

    fun handleIncomingCrossedOffItems(itemsToCrossOff: List<String>?) {
        itemsToCrossOff?.let {
            it.forEach { item ->
                if (item !in crossedOffItems) {
                    crossedOffItems.add(item)
                    val index = shoppingListItems.indexOf(item)
                    if (index != -1) {
                        shoppingListItems.removeAt(index)
                        shoppingListItems.add(item)
                    }
                }
            }
            saveData()
        }
    }

    fun crossOffItems(itemsToCrossOff: List<String>){
        itemsToCrossOff.forEach{item ->
            if(item !in crossedOffItems){
                crossedOffItems.add(item)
                val index = shoppingListItems.indexOf(item)
                if (index != -1) {
                    shoppingListItems.removeAt(index)
                    shoppingListItems.add(item)
                }
            }
        }
        saveData()
    }

    fun unCrossOffItem(item: String) {
        if (item in crossedOffItems) {  // Important: Check if it's actually crossed off
            crossedOffItems.remove(item)
            // Move the item back to its original position (or the top)
            val originalIndex = shoppingListItems.indexOf(item)
            if (originalIndex != -1) {
                shoppingListItems.remove(item)  // Remove from its current (bottom) position
                shoppingListItems.add(originalIndex, item)  // Add back at the original index
            } else {
                //if item not found (removed by api), add to top
                shoppingListItems.add(0,item)
            }

            saveData() // Save the changes
        }
    }

    private fun saveData() {
        val sharedPreferences = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        editor.putString(listKey, gson.toJson(shoppingListItems))
        editor.putString("crossed_off_items", gson.toJson(crossedOffItems.distinct()))
        editor.apply()
    }

    private fun loadData() {
        val sharedPreferences = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val gson = Gson()
        sharedPreferences.getString(listKey, null)?.let {
            shoppingListItems.addAll(gson.fromJson(it, object : TypeToken<MutableList<String>>() {}.type))
        }
        sharedPreferences.getString("crossed_off_items", null)?.let {
            crossedOffItems.addAll(gson.fromJson(it, object : TypeToken<List<String>>() {}.type))
        }
    }
}