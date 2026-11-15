package com.example.data.repository

import com.example.data.local.ActiveSessionDao
import com.example.data.local.AllowedAppDao
import com.example.data.local.AllowedAppEntity
import com.example.data.local.EntityMappers.toDomain
import com.example.data.local.EntityMappers.toEntity
import com.example.data.local.LockScheduleDao
import com.example.data.local.SessionHistoryDao
import com.example.domain.model.ActiveSession
import com.example.domain.model.LockSchedule
import com.example.domain.model.SessionHistoryRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepository(private val activeSessionDao: ActiveSessionDao) {
    fun observeActiveSession(): Flow<ActiveSession?> =
        activeSessionDao.observeActiveSession().map { it?.toDomain() }

    suspend fun getActiveSession(): ActiveSession? =
        activeSessionDao.getActiveSession()?.toDomain()

    suspend fun saveActiveSession(session: ActiveSession) {
        activeSessionDao.upsertActiveSession(session.toEntity())
    }

    suspend fun clearActiveSession() {
        activeSessionDao.clearActiveSession()
    }
}

class ScheduleRepository(private val lockScheduleDao: LockScheduleDao) {
    fun observeSchedules(): Flow<List<LockSchedule>> =
        lockScheduleDao.observeAllSchedules().map { list -> list.map { it.toDomain() } }

    suspend fun getAllSchedules(): List<LockSchedule> =
        lockScheduleDao.getAllSchedules().map { it.toDomain() }

    suspend fun getScheduleById(id: Long): LockSchedule? =
        lockScheduleDao.getScheduleById(id)?.toDomain()

    suspend fun saveSchedule(schedule: LockSchedule): Long =
        lockScheduleDao.upsertSchedule(schedule.toEntity())

    suspend fun deleteSchedule(id: Long) {
        lockScheduleDao.deleteScheduleById(id)
    }
}

class HistoryRepository(private val historyDao: SessionHistoryDao) {
    fun observeHistory(): Flow<List<SessionHistoryRecord>> =
        historyDao.observeHistory().map { list -> list.map { it.toDomain() } }

    suspend fun recordOnce(record: SessionHistoryRecord) {
        if (historyDao.countBySessionId(record.sessionId) == 0) {
            historyDao.insertHistory(record.toEntity())
        }
    }

    suspend fun clearAll() {
        historyDao.clearHistory()
    }
}

class AllowlistRepository(private val allowedAppDao: AllowedAppDao) {
    fun observeAllowedPackages(): Flow<List<String>> =
        allowedAppDao.observeAllowedApps().map { list -> list.map { it.packageName } }

    suspend fun getAllowedPackages(): List<String> =
        allowedAppDao.getAllowedApps().map { it.packageName }

    suspend fun replaceAllowedApps(apps: List<Pair<String, String>>, nowWallMs: Long) {
        val entities = apps.distinctBy { it.first }.map { (pkg, name) ->
            AllowedAppEntity(
                packageName = pkg,
                displayName = name,
                addedAtWallMs = nowWallMs
            )
        }
        allowedAppDao.replaceAll(entities)
    }
}
