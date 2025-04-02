package com.eyesack.freshlist

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert
    suspend fun insertItem(item: Item)

    @Update
    suspend fun updateItem(item: Item)

    @Delete
    suspend fun deleteItem(item: Item)

    @Query("SELECT * FROM item WHERE itemId = :itemId")
    suspend fun getItemById(itemId: Int): Item?

    @Query("SELECT * FROM item WHERE itemStatus = :status")
    fun getItemsByStatus(status: ItemStatus): Flow<List<Item>>

    @Query("SELECT * FROM item WHERE itemStatus = :status")
    fun getAvailableItems(status: ItemStatus = ItemStatus
        .AVAILABLE): Flow<List<Item>>

    @Query("SELECT * FROM item WHERE itemStatus = :status")
    fun getWastedtems(status: ItemStatus = ItemStatus
        .CONSUMED): Flow<List<Item>>

    @Query("SELECT * FROM item WHERE itemStatus = :status")
    fun getConsumedItems(status: ItemStatus = ItemStatus
        .WASTED): Flow<List<Item>>

    @Query("Select * FROM item")
    fun getAllItems(): Flow<List<Item>>

    @Query("UPDATE item SET itemStatus = :newStatus WHERE itemId = :itemId")
    suspend fun changeItemStatus(itemId: Int, newStatus: ItemStatus)

}