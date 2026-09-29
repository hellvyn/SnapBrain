package com.snapbrain.app.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ItemProgress(val itemId: String, val total: Int, val done: Int, val nextDue: String?)

/** A list row plus the title of the screenshot it came from, for the Belanja and To-do tabs. */
data class SourcedRow(@Embedded val row: ListItemEntity, val itemTitle: String?)

@Dao
interface ListItemDao {
    @Query("SELECT * FROM list_item WHERE itemId = :itemId ORDER BY listIndex, position")
    fun observe(itemId: String): Flow<List<ListItemEntity>>

    /** Per-item progress and the earliest open due, for the Inbox cards. */
    @Query(
        """SELECT itemId, COUNT(*) AS total, SUM(checked) AS done,
                  MIN(CASE WHEN checked = 0 AND due IS NOT NULL AND due != '' THEN due END) AS nextDue
           FROM list_item GROUP BY itemId""",
    )
    fun observeProgress(): Flow<List<ItemProgress>>

    /** Spec §8 Belanja: rows put there by an activation, newest screenshot first. */
    @Query(
        """SELECT list_item.*, item.title AS itemTitle FROM list_item JOIN item ON item.id = list_item.itemId
           WHERE list_item.inBelanja = 1
           ORDER BY item.createdAt DESC, list_item.listIndex, list_item.position""",
    )
    fun observeBelanja(): Flow<List<SourcedRow>>

    /** Spec §8 To-do: to-do/bring/step rows of activated screenshots, plus any such row with a due. */
    @Query(
        """SELECT list_item.*, item.title AS itemTitle FROM list_item JOIN item ON item.id = list_item.itemId
           WHERE (list_item.role IN ('todo', 'bawa') OR list_item.kind = 'steps')
             AND (item.active = 1 OR (list_item.due IS NOT NULL AND list_item.due != ''))
           ORDER BY item.createdAt DESC, list_item.listIndex, list_item.position""",
    )
    fun observeTodo(): Flow<List<SourcedRow>>

    /** Spec §8 budget "Terbeli": shopping rows checked since [since]. */
    @Query("SELECT COALESCE(SUM(price), 0) FROM list_item WHERE role = 'belanja' AND checked = 1 AND checkedAt >= :since")
    fun observeSpent(since: Long): Flow<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM list_item WHERE checked = 0 AND remind = 1 AND due IS NOT NULL AND due != '')")
    fun observeHasDue(): Flow<Boolean>

    @Query("SELECT * FROM list_item WHERE checked = 0 AND remind = 1 AND due IS NOT NULL AND due != ''")
    suspend fun remindable(): List<ListItemEntity>

    @Query("SELECT * FROM list_item WHERE id = :id")
    suspend fun get(id: Long): ListItemEntity?

    @Query("SELECT * FROM list_item WHERE itemId = :itemId")
    suspend fun rowsFor(itemId: String): List<ListItemEntity>

    @Query("SELECT list_item.*, item.title AS itemTitle FROM list_item JOIN item ON item.id = list_item.itemId WHERE list_item.id = :id")
    suspend fun sourced(id: Long): SourcedRow?

    @Insert
    suspend fun insertAll(rows: List<ListItemEntity>): List<Long>

    @Query("DELETE FROM list_item WHERE itemId = :itemId")
    suspend fun deleteFor(itemId: String)

    @Query("UPDATE list_item SET checked = NOT checked, checkedAt = CASE WHEN checked = 0 THEN :now ELSE NULL END WHERE id = :id")
    suspend fun toggle(id: Long, now: Long)

    /** Rows already in the wanted state keep their original checkedAt. */
    @Query(
        """UPDATE list_item SET checked = :checked, checkedAt = CASE WHEN :checked THEN :now ELSE NULL END
           WHERE id IN (:ids) AND checked != :checked""",
    )
    suspend fun setChecked(ids: List<Long>, checked: Boolean, now: Long)

    @Query("UPDATE list_item SET remind = NOT remind WHERE id = :id")
    suspend fun toggleRemind(id: Long)

    @Query("UPDATE list_item SET inBelanja = 1 WHERE itemId = :itemId AND role = 'belanja'")
    suspend fun addToBelanja(itemId: String)

    /** Deactivating keeps checked rows in Belanja until "Selesai belanja" (spec §8). */
    @Query("UPDATE list_item SET inBelanja = 0 WHERE itemId = :itemId AND checked = 0")
    suspend fun removeFromBelanja(itemId: String)

    @Query("UPDATE list_item SET inBelanja = 0 WHERE inBelanja = 1 AND checked = 1")
    suspend fun finishShopping()
}
