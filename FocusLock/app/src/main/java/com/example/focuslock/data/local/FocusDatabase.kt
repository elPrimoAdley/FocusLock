package com.example.focuslock.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.focuslock.data.local.dao.AppMetadataDao
import com.example.focuslock.data.local.dao.BlockAuditLogDao
import com.example.focuslock.data.local.dao.FocusSessionDao
import com.example.focuslock.data.local.dao.QuestTaskDao
import com.example.focuslock.data.local.dao.RuleDao
import com.example.focuslock.data.local.dao.SavedContentDao
import com.example.focuslock.data.local.entity.AppMetadataEntity
import com.example.focuslock.data.local.entity.BlockAuditLogEntity
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
        AppMetadataEntity::class
    ],
    version = 1,
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

    companion object {
        private const val DATABASE_NAME = "focuslock.db"

        @Volatile
        private var INSTANCE: FocusDatabase? = null

        fun getInstance(context: Context): FocusDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusDatabase::class.java,
                    DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}