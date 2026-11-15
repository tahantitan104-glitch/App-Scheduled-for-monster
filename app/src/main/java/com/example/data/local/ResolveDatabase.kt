package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ActiveSessionEntity::class,
        LockScheduleEntity::class,
        SessionHistoryEntity::class,
        AllowedAppEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ResolveDatabase : RoomDatabase() {
    abstract fun activeSessionDao(): ActiveSessionDao
    abstract fun lockScheduleDao(): LockScheduleDao
    abstract fun sessionHistoryDao(): SessionHistoryDao
    abstract fun allowedAppDao(): AllowedAppDao

    companion object {
        const val DB_NAME = "resolve_local.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE active_session ADD COLUMN enforcementMode TEXT NOT NULL DEFAULT 'NONE'"
                )
            }
        }

        fun create(context: Context): ResolveDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ResolveDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
