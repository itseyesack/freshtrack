package com.eyesack.freshlist

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "item")
data class Item(
    @PrimaryKey(autoGenerate = true)
    val itemId            : Int,

    // Attributes
    val itemName          : String,
    val itemInfo          : String ?,
    val itemWeight        : Float  ?,
    val itemPriceToWeight : Float  ?,
    val itemPrice         : Float  ?,
    val itemQuantity      : Int    ?,
    val itemCost          : Float  ?,
    val timeSincePurchase : Date   ?,
    val expiryDate        : Date   ?,
    val purchasedDate     : Date   ?,
    val dateStatusChanged : Date   ?,

    // Location Information
    val storeName :    String?,
    val storeAddress : String?,
    val storeZipCode : String?,
    val storeState   : String?,
    val storeCountry : String?,

    // Enums
    val itemCategory        : ItemCategory?,
    val itemStorageType     : StorageType ?,
    val itemStatus          : ItemStatus  ?

)

// Enum classes
enum class ItemCategory {
    PRODUCE,
    MEAT,
    DAIRY,
    GRAIN
}

enum class StorageType {
    FRIDGE,
    FREEZER,
    PANTRY
}

enum class ItemStatus {
    AVAILABLE,
    WASTED,
    CONSUMED
}