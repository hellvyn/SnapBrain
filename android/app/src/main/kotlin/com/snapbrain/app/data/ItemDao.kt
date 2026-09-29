package com.snapbrain.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    // ponytail: LIKE scan over every row; move to FTS4 if inboxes reach tens of thousands of items.
    @Query(
        """SELECT * FROM item
           WHERE (:category IS NULL OR category = :category OR (:category = 'unclassified' AND category IS NULL))
             AND (:query = '' OR ocrText LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%')
           ORDER BY createdAt DESC""",
    )
    fun observe(query: String, category: String?): Flow<List<ItemEntity>>

    @Query("SELECT * FROM item WHERE id = :id")
    fun observeById(id: String): Flow<ItemEntity?>

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun get(id: String): ItemEntity?

    @Query("SELECT * FROM item WHERE status = :status ORDER BY createdAt")
    suspend fun withStatus(status: String): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM item WHERE status = :status")
    suspend fun countWithStatus(status: String): Int

    @Insert
    suspend fun insert(item: ItemEntity)

    @Update
    suspend fun update(item: ItemEntity)

    @Query("UPDATE item SET active = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    @Query("DELETE FROM item WHERE id = :id")
    suspend fun delete(id: String)
}
