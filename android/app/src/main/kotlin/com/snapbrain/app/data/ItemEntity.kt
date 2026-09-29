package com.snapbrain.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "item")
data class ItemEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val imagePath: String,
    val ocrText: String,
    val status: String,
    val category: String? = null,
    val title: String? = null,
    val extractedInfo: String? = null, // JSON object, see ExtractJson
    val actionType: String? = null,
    val actionPayload: String? = null,
    val tasks: String? = null, // JSON array, see ExtractJson
    val tasksTotal: Int = 0,
    val attempts: Int = 0,
    val actions: String? = null, // JSON array of ActionData, see ExtractJson; null on items stored before v2
    val activation: String? = null,
    @ColumnInfo(defaultValue = "0") val active: Boolean = false,
)
