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
        AllowedAppEntity::class,
        DelayedLockPresetEntity::class,
        AppLockAppEntity::class,
        FlipTimerPresetEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class ResolveDatabase : RoomDatabase() {
    abstract fun activeSessionDao(): ActiveSessionDao
    abstract fun lockScheduleDao(): LockScheduleDao
    abstract fun sessionHistoryDao(): SessionHistoryDao
    abstract fun allowedAppDao(): AllowedAppDao
    abstract fun delayedLockPresetDao(): DelayedLockPresetDao
    abstract fun appLockAppDao(): AppLockAppDao
    abstract fun flipTimerPresetDao(): FlipTimerPresetDao

    companion object {
        const val DB_NAME = "resolve_local.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE active_session ADD COLUMN enforcementMode TEXT NOT NULL DEFAULT 'NONE'"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE lock_schedules ADD COLUMN cloudId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE lock_schedules ADD COLUMN syncAcrossDevices INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS delayed_presets (
                        presetId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        freeUseMinutes INTEGER NOT NULL,
                        lockDurationMinutes INTEGER NOT NULL,
                        builtIn INTEGER NOT NULL,
                        updatedAtWallMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_lock_apps (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        addedAtWallMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE active_session ADD COLUMN isStrictMode INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE active_session ADD COLUMN showFlipClockShortcut INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE lock_schedules ADD COLUMN isStrictMode INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE lock_schedules ADD COLUMN allowSelectedApps INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE lock_schedules ADD COLUMN showFlipClockShortcut INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS flip_timer_presets (
                        presetId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        durationSeconds INTEGER NOT NULL,
                        builtIn INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        updatedAtWallMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun create(context: Context): ResolveDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ResolveDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
