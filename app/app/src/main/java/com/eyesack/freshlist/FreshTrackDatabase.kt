package com.eyesack.freshlist

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Item::class],
    version = 1,
    exportSchema = false
)

@TypeConverters(Converters::class)
abstract class FreshTrackDatabase: RoomDatabase() {
    abstract val dao: ItemDao
}