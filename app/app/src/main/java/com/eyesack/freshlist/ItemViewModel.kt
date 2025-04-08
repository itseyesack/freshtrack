package com.eyesack.freshlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.eyesack.freshlist.Item
import com.eyesack.freshlist.ItemDao
import kotlinx.coroutines.flow.Flow

class ItemViewModel (private val itemDao: ItemDao) : ViewModel() {

    // Expose items as LiveData to observe changes in the UI
    val allItems: LiveData<List<Item>> = itemDao
        .getAllItems()
        .asLiveData()


}
