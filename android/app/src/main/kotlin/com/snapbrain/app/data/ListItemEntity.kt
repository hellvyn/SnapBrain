package com.snapbrain.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per list item, so reminders (phase B) and cross-screenshot views (phase C) are plain queries. */
@Entity(
    tableName = "list_item",
    foreignKeys = [ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")],
)
data class ListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val listIndex: Int,
    val listTitle: String,
    val kind: String,
    val role: String,
    val position: Int,
    val text: String,
    val due: String? = null,
    val minutes: Int = 0,
    val price: Long = 0,
    val size: String? = null,
    @ColumnInfo(defaultValue = "0") val checked: Boolean = false,
    val checkedAt: Long? = null,
    @ColumnInfo(defaultValue = "1") val remind: Boolean = true,
    @ColumnInfo(defaultValue = "0") val inBelanja: Boolean = false,
)
