package com.eyesack.freshlist

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import java.util.Date

class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let {Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromItemCategory(value: ItemCategory?): String? {
        return value?.name
    }

    @TypeConverter
    fun fromStorageType(value: StorageType): String? {
        return value?.name
    }

    @TypeConverter
    fun toStorageType(value: String?): StorageType? {
        return value?.let {StorageType.valueOf(it)}
    }

    @TypeConverter
    fun fromItemStatus(value: ItemStatus?): String? {
        return value?.name
    }

    @TypeConverter
    fun toItemStatus(value: String?): ItemStatus? {
        return value?.let {ItemStatus.valueOf(it)
        }
    }
}