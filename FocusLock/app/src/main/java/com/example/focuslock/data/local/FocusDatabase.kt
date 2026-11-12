package com.example.focuslock.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.focuslock.data.local.dao.AppMetadataDao
import com.example.focuslock.data.local.dao.BlockAuditLogDao
import com.example.focuslock.data.local.dao.DailyUsageDao
import com.example.focuslock.data.local.dao.FocusSessionDao
import com.example.focuslock.data.local.dao.QuestTaskDao
import com.example.focuslock.data.local.dao.RuleDao
import com.example.focuslock.data.local.dao.SavedContentDao
import com.example.focuslock.data.local.entity.AppMetadataEntity
import com.example.focuslock.data.local.entity.BlockAuditLogEntity
import com.example.focuslock.data.local.entity.DailyUsageEntity
import com.example.focuslock.data.local.entity.FocusSessionEntity
import com.example.focuslock.data.local.entity.QuestTaskEntity
import com.example.focuslock.data.local.entity.RuleEntity
import com.example.focuslock.data.local.entity.SavedContentEntity

@Database(
    entities = [
        RuleEntity::class,
        QuestTaskEntity::class,
        FocusSessionEntity::class,
        SavedContentEntity::class,
        BlockAuditLogEntity::class,
        AppMetadataEntity::class,
        DailyUsageEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(FocusTypeConverters::class)
abstract class FocusDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao
    abstract fun questTaskDao(): QuestTaskDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun savedContentDao(): SavedContentDao
    abstract fun blockAuditLogDao(): BlockAuditLogDao
    abstract fun appMetadataDao(): AppMetadataDao
    abstract fun dailyUsageDao(): DailyUsageDao

    companion object {
        private const val DATABASE_NAME = "focuslock.db"

        /**
         * 4.3 : Migration manuelle v1 -> v2 (aucun fallbackToDestructiveMigration).
         * - Ajoute openCount (défaut 0) sur saved_contents
         * - Déduplique préventivement sourceUrl puis crée l'index unique index_saved_contents_sourceUrl
         * - Crée la table daily_usage(epochDay, packageName, totalMs)
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `saved_contents` ADD COLUMN `openCount` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    DELETE FROM `saved_contents` 
                    WHERE rowid NOT IN (
                        SELECT MIN(rowid) FROM `saved_contents` GROUP BY `sourceUrl`
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_contents_sourceUrl` ON `saved_contents` (`sourceUrl`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_usage` (
                        `epochDay` INTEGER NOT NULL, 
                        `packageName` TEXT NOT NULL, 
                        `totalMs` INTEGER NOT NULL, 
                        PRIMARY KEY(`epochDay`, `packageName`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: FocusDatabase? = null

        fun getInstance(context: Context): FocusDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
