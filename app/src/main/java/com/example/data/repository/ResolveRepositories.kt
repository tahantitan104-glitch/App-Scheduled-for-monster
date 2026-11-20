package com.example.data.repository

import com.example.data.local.ActiveSessionDao
import com.example.data.local.AllowedAppDao
import com.example.data.local.AllowedAppEntity
import com.example.data.local.AppLockAppDao
import com.example.data.local.AppLockAppEntity
import com.example.data.local.DelayedLockPresetDao
import com.example.data.local.EntityMappers.toDomain
import com.example.data.local.EntityMappers.toEntity
import com.example.data.local.FlipTimerPresetDao
import com.example.data.local.LockScheduleDao
import com.example.data.local.SessionHistoryDao
import com.example.domain.model.ActiveSession
import com.example.domain.model.DelayedLockPreset
import com.example.domain.model.FlipTimerPreset
import com.example.domain.model.LockSchedule
import com.example.domain.model.SessionHistoryRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    suspend fun getScheduleByCloudId(cloudId: String): LockSchedule? =
        if (cloudId.isBlank()) null else lockScheduleDao.getScheduleByCloudId(cloudId)?.toDomain()

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

class DelayedPresetRepository(private val presetDao: DelayedLockPresetDao) {
    fun observePresets(): Flow<List<DelayedLockPreset>> =
        presetDao.observePresets().map { entities ->
            val domain = entities.map { it.toDomain() }
            if (domain.any { it.presetId == QUICK_RECOVERY_ID }) {
                domain
            } else {
                listOf(BUILT_IN_QUICK_RECOVERY) + domain
            }
        }

    suspend fun ensureBuiltInPresets() {
        if (presetDao.getPresetById(QUICK_RECOVERY_ID) == null) {
            presetDao.upsertPreset(BUILT_IN_QUICK_RECOVERY.toEntity())
        }
    }

    suspend fun getAllPresets(): List<DelayedLockPreset> {
        val existing = presetDao.getAllPresets().map { it.toDomain() }
        return if (existing.any { it.presetId == QUICK_RECOVERY_ID }) {
            existing
        } else {
            listOf(BUILT_IN_QUICK_RECOVERY) + existing
        }
    }

    suspend fun savePreset(preset: DelayedLockPreset) {
        presetDao.upsertPreset(preset.toEntity())
    }

    suspend fun deleteCustomPreset(presetId: String) {
        if (presetId != QUICK_RECOVERY_ID) {
            presetDao.deleteCustomPreset(presetId)
        }
    }

    companion object {
        const val QUICK_RECOVERY_ID = "builtin_quick_recovery"
        val BUILT_IN_QUICK_RECOVERY = DelayedLockPreset(
            presetId = QUICK_RECOVERY_ID,
            name = "Quick Recovery",
            freeUseMinutes = 20L,
            lockDurationMinutes = 30L,
            builtIn = true,
            updatedAtWallMs = 0L
        )
    }
}

class AppLockRepository(private val appLockAppDao: AppLockAppDao) {
    fun observeProtectedPackages(): Flow<List<String>> =
        appLockAppDao.observeProtectedApps().map { list -> list.map { it.packageName } }

    suspend fun getProtectedPackages(): List<String> =
        appLockAppDao.getProtectedApps().map { it.packageName }

    suspend fun replaceProtectedApps(apps: List<Pair<String, String>>, nowWallMs: Long) {
        val entities = apps.distinctBy { it.first }.map { (pkg, name) ->
            AppLockAppEntity(
                packageName = pkg,
                displayName = name,
                addedAtWallMs = nowWallMs
            )
        }
        appLockAppDao.replaceAll(entities)
    }
}

sealed interface FlipPresetMutationResult {
    data class Saved(val preset: FlipTimerPreset) : FlipPresetMutationResult
    data class LimitReached(val maxCount: Int = FlipTimerPreset.MAX_TOTAL_PRESETS) : FlipPresetMutationResult
    data class Invalid(val reason: String) : FlipPresetMutationResult
}

class FlipTimerPresetRepository(
    private val presetDao: FlipTimerPresetDao,
    private val preferencesRepository: com.example.data.preferences.ResolvePreferencesRepository? = null
) {
    fun observePresets(): Flow<List<FlipTimerPreset>> =
        presetDao.observePresets().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun ensureDefaultPresetsSeeded() {
        val seededAlready = preferencesRepository?.hasSeededDefaultFlipPresets() ?: false
        val count = presetDao.countPresets()
        if (!seededAlready && count == 0) {
            presetDao.upsertAll(DEFAULT_BUILT_IN_PRESETS.map { it.toEntity() })
            preferencesRepository?.markDefaultFlipPresetsSeeded()
        }
    }

    suspend fun getAllPresets(): List<FlipTimerPreset> {
        ensureDefaultPresetsSeeded()
        return presetDao.getAllPresets().map { it.toDomain() }
    }

    suspend fun getPresetById(presetId: String): FlipTimerPreset? =
        presetDao.getPresetById(presetId)?.toDomain()

    suspend fun savePreset(
        presetId: String?,
        name: String,
        durationSeconds: Long,
        builtIn: Boolean = false,
        nowWallMs: Long
    ): FlipPresetMutationResult {
        ensureDefaultPresetsSeeded()
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return FlipPresetMutationResult.Invalid("Enter a preset name.")
        }
        if (durationSeconds <= 0L) {
            return FlipPresetMutationResult.Invalid("Duration must be at least 1 second.")
        }
        val existing = presetId?.let { presetDao.getPresetById(it)?.toDomain() }
        val currentCount = presetDao.countPresets()
        if (existing == null && currentCount >= FlipTimerPreset.MAX_TOTAL_PRESETS) {
            return FlipPresetMutationResult.LimitReached(FlipTimerPreset.MAX_TOTAL_PRESETS)
        }
        val nextSortOrder = existing?.sortOrder
            ?: ((presetDao.getAllPresets().maxOfOrNull { it.sortOrder } ?: (currentCount - 1)) + 1)
        val targetId = existing?.presetId ?: presetId?.takeIf { it.isNotBlank() }
            ?: "flip_custom_${java.util.UUID.randomUUID().toString().replace("-", "").take(12)}"
        val saved = FlipTimerPreset(
            presetId = targetId,
            name = trimmedName,
            durationSeconds = durationSeconds.coerceIn(1L, 86_399L),
            builtIn = existing?.builtIn ?: builtIn,
            sortOrder = nextSortOrder,
            updatedAtWallMs = nowWallMs
        )
        presetDao.upsertPreset(saved.toEntity())
        preferencesRepository?.clearFlipPresetTombstone(targetId)
        return FlipPresetMutationResult.Saved(saved)
    }

    suspend fun upsertFromSync(preset: FlipTimerPreset) {
        val existing = presetDao.getPresetById(preset.presetId)?.toDomain()
        if (existing == null && presetDao.countPresets() >= FlipTimerPreset.MAX_TOTAL_PRESETS) {
            return
        }
        presetDao.upsertPreset(preset.toEntity())
        preferencesRepository?.clearFlipPresetTombstone(preset.presetId)
    }

    suspend fun deletePreset(presetId: String, nowWallMs: Long) {
        presetDao.deleteById(presetId)
        preferencesRepository?.recordFlipPresetTombstone(presetId, nowWallMs)
    }

    suspend fun restoreMissingBuiltInsWithinCap(nowWallMs: Long) {
        val current = presetDao.getAllPresets().map { it.toDomain() }.toMutableList()
        var nextSort = (current.maxOfOrNull { it.sortOrder } ?: current.size) + 1
        for (builtIn in DEFAULT_BUILT_IN_PRESETS) {
            if (current.size >= FlipTimerPreset.MAX_TOTAL_PRESETS) break
            if (current.none { it.presetId == builtIn.presetId }) {
                val restored = builtIn.copy(sortOrder = nextSort++, updatedAtWallMs = nowWallMs)
                presetDao.upsertPreset(restored.toEntity())
                preferencesRepository?.clearFlipPresetTombstone(restored.presetId)
                current += restored
            }
        }
    }

    companion object {
        val DEFAULT_BUILT_IN_PRESETS: List<FlipTimerPreset> = listOf(
            FlipTimerPreset("flip_builtin_1m", "1 Minute", 60L, builtIn = true, sortOrder = 0, updatedAtWallMs = 1L),
            FlipTimerPreset("flip_builtin_5m", "5 Minutes", 5L * 60L, builtIn = true, sortOrder = 1, updatedAtWallMs = 1L),
            FlipTimerPreset("flip_builtin_10m", "10 Minutes", 10L * 60L, builtIn = true, sortOrder = 2, updatedAtWallMs = 1L),
            FlipTimerPreset("flip_builtin_15m", "15 Minutes", 15L * 60L, builtIn = true, sortOrder = 3, updatedAtWallMs = 1L),
            FlipTimerPreset("flip_builtin_25m", "25 Minutes", 25L * 60L, builtIn = true, sortOrder = 4, updatedAtWallMs = 1L),
            FlipTimerPreset("flip_builtin_30m", "30 Minutes", 30L * 60L, builtIn = true, sortOrder = 5, updatedAtWallMs = 1L),
            FlipTimerPreset("flip_builtin_60m", "60 Minutes", 60L * 60L, builtIn = true, sortOrder = 6, updatedAtWallMs = 1L),
            FlipTimerPreset("flip_builtin_90m", "90 Minutes", 90L * 60L, builtIn = true, sortOrder = 7, updatedAtWallMs = 1L),
            FlipTimerPreset("flip_builtin_120m", "120 Minutes", 120L * 60L, builtIn = true, sortOrder = 8, updatedAtWallMs = 1L)
        )
    }
}
