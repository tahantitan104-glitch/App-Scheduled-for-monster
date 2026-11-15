package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveSessionDao {
    @Query("SELECT * FROM active_session WHERE slotId = 1 LIMIT 1")
    fun observeActiveSession(): Flow<ActiveSessionEntity?>

    @Query("SELECT * FROM active_session WHERE slotId = 1 LIMIT 1")
    suspend fun getActiveSession(): ActiveSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActiveSession(session: ActiveSessionEntity)

    @Query("DELETE FROM active_session")
    suspend fun clearActiveSession()
}

@Dao
interface LockScheduleDao {
    @Query("SELECT * FROM lock_schedules ORDER BY isEnabled DESC, nextTriggerWallMs ASC, createdAtWallMs DESC")
    fun observeAllSchedules(): Flow<List<LockScheduleEntity>>

    @Query("SELECT * FROM lock_schedules")
    suspend fun getAllSchedules(): List<LockScheduleEntity>

    @Query("SELECT * FROM lock_schedules WHERE id = :id LIMIT 1")
    suspend fun getScheduleById(id: Long): LockScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: LockScheduleEntity): Long

    @Query("DELETE FROM lock_schedules WHERE id = :id")
    suspend fun deleteScheduleById(id: Long)
}

@Dao
interface SessionHistoryDao {
    @Query("SELECT * FROM session_history ORDER BY endedAtWallMs DESC")
    fun observeHistory(): Flow<List<SessionHistoryEntity>>

    @Query("SELECT COUNT(*) FROM session_history WHERE sessionId = :sessionId")
    suspend fun countBySessionId(sessionId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHistory(record: SessionHistoryEntity)

    @Query("DELETE FROM session_history")
    suspend fun clearHistory()
}

@Dao
interface AllowedAppDao {
    @Query("SELECT * FROM allowed_apps ORDER BY displayName COLLATE NOCASE ASC")
    fun observeAllowedApps(): Flow<List<AllowedAppEntity>>

    @Query("SELECT * FROM allowed_apps")
    suspend fun getAllowedApps(): List<AllowedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<AllowedAppEntity>)

    @Query("DELETE FROM allowed_apps")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(apps: List<AllowedAppEntity>) {
        clearAll()
        if (apps.isNotEmpty()) {
            insertAll(apps)
        }
    }
}
