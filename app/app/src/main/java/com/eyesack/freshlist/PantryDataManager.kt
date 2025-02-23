package com.eyesack.freshlist

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

class PantryDataManager(private val context: Context) {

    var pantryItems = mutableListOf<PantryItem>()
    private val sharedPreferencesName = "shopping_list_prefs"
    private val pantryKey = "pantry_items"

    init {
        loadPantryData()
    }

    fun updatePantryItems(jsonResponse: JSONObject) {
        val receipts = jsonResponse.optJSONArray("receipts") ?: return
        val newPantryItems = mutableListOf<PantryItem>()

        for (i in 0 until receipts.length()) {
            val receipt = receipts.getJSONObject(i)
            val items = receipt.optJSONArray("items") ?: continue
            for (j in 0 until items.length()) {
                val item = items.getJSONObject(j)
                val name = item.optString("friendly_name", item.optString("name", "Unknown"))
                val category = item.optString("category", "Unknown")
                val upc = item.optString("upc", "Not Found")
                val details = mutableMapOf<String, String>()
                details["Store"] = receipt.optString("Store", "Unknown")
                details["Address"] = receipt.optString("Address", "Unknown")
                details["Date"] = receipt.optString("Date", "Unknown")
                details["Quantity"] = item.optString("quantity", "Unknown")
                details["Price"] = item.optString("price", "Unknown")
                details["Unit Price"] = item.optString("unit_price", "Unknown")
                details["Unit"] = item.optString("unit", "Unknown")
                details["UPC"] = upc
                details["Last Purchased"] = receipt.optString("Date", "Unknown")
                newPantryItems.add(PantryItem(name, category, details))
            }
        }

        val sharedPreferences = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val existingPantryJson = sharedPreferences.getString(pantryKey, null)
        val existingPantryItems: MutableList<PantryItem> = if (existingPantryJson != null) {
            Gson().fromJson(existingPantryJson, object : TypeToken<MutableList<PantryItem>>() {}.type)
        } else {
            mutableListOf()
        }

        for (newItem in newPantryItems) {
            val existingItemIndex = existingPantryItems.indexOfFirst { existing ->
                existing.name == newItem.name || (newItem.details["UPC"] != "Not Found" && newItem.details["UPC"]?.matches(Regex("\\d+")) == true && existing.details["UPC"] == newItem.details["UPC"])
            }
            if (existingItemIndex != -1) {
                val updatedDetails = existingPantryItems[existingItemIndex].details.toMutableMap()
                updatedDetails["Last Purchased"] = newItem.details["Last Purchased"] ?: "Unknown"
                existingPantryItems[existingItemIndex] = existingPantryItems[existingItemIndex].copy(details = updatedDetails)
            } else {
                existingPantryItems.add(newItem)
            }
        }

        pantryItems.clear()
        pantryItems.addAll(existingPantryItems)
        savePantryData()
    }

    private fun loadPantryData() {
        val sharedPreferences = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val json = sharedPreferences.getString(pantryKey, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<PantryItem>>() {}.type
            pantryItems.clear()
            pantryItems.addAll(Gson().fromJson(json, type))
        }
    }

    private fun savePantryData() {
        val sharedPreferences = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val json = Gson().toJson(pantryItems)
        editor.putString(pantryKey, json)
        editor.apply()
    }

    fun deleteItem(item: PantryItem) {
        pantryItems.remove(item)
        savePantryData()
    }
}