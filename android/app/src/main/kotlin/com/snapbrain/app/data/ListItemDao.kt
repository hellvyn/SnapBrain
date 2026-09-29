package com.snapbrain.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ItemProgress(val itemId: String, val total: Int, val done: Int, val nextDue: String?)

@Dao
interface ListItemDao {
    @Query("SELECT * FROM list_item WHERE itemId = :itemId ORDER BY listIndex, position")
    fun observe(itemId: String): Flow<List<ListItemEntity>>

    // Dues are ISO strings, so MIN() picks the earliest; "2026-10-01" sorts before "2026-10-01T09:00".
    @Query(
        """SELECT itemId, COUNT(*) AS total, SUM(checked) AS done,
                  MIN(CASE WHEN checked = 0 AND due IS NOT NULL AND due != '' THEN due END) AS nextDue
           FROM list_item GROUP BY itemId""",
    )
    fun observeProgress(): Flow<List<ItemProgress>>

    @Insert
    suspend fun insertAll(rows: List<ListItemEntity>)

    @Query("DELETE FROM list_item WHERE itemId = :itemId")
    suspend fun deleteFor(itemId: String)

    // SQLite evaluates every SET expression against the old row, so checkedAt sees the old `checked`.
    @Query("UPDATE list_item SET checked = NOT checked, checkedAt = CASE WHEN checked = 0 THEN :now ELSE NULL END WHERE id = :id")
    suspend fun toggle(id: Long, now: Long)
}
