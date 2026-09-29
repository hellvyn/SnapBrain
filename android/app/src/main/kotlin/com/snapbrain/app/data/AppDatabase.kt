package com.snapbrain.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ItemEntity::class, ListItemEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun listItemDao(): ListItemDao

    companion object {
        /** v2: list items move to their own table; items keep up to three v2 actions. Old rows stay as they are. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `item` ADD COLUMN `actions` TEXT")
                db.execSQL("ALTER TABLE `item` ADD COLUMN `activation` TEXT")
                db.execSQL("ALTER TABLE `item` ADD COLUMN `active` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `list_item` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemId` TEXT NOT NULL,
                        `listIndex` INTEGER NOT NULL,
                        `listTitle` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `due` TEXT,
                        `minutes` INTEGER NOT NULL,
                        `price` INTEGER NOT NULL,
                        `size` TEXT,
                        `checked` INTEGER NOT NULL DEFAULT 0,
                        `checkedAt` INTEGER,
                        `remind` INTEGER NOT NULL DEFAULT 1,
                        `inBelanja` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`itemId`) REFERENCES `item`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_list_item_itemId` ON `list_item` (`itemId`)")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "snapbrain.db").addMigrations(MIGRATION_1_2).build()
    }
}
