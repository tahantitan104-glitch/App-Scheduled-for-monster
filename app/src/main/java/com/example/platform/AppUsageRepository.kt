package com.example.platform

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import com.example.domain.model.DailyAppUsageItem
import com.example.domain.model.DailyAppUsageSummary
import com.example.domain.time.ClockProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Reads genuine device-local screen time and per-app foreground usage from Android's
 * UsageStatsManager for the current local calendar day. Never fabricates usage data.
 */
class AppUsageRepository(
    private val context: Context,
    private val capabilityChecker: SystemCapabilityChecker,
    private val clockProvider: ClockProvider
) {
    suspend fun queryTodayUsageSummary(maxApps: Int = 6): DailyAppUsageSummary =
        withContext(Dispatchers.IO) {
            val nowWall = clockProvider.wallClockMs()
            if (!capabilityChecker.hasUsageStatsAccess()) {
                return@withContext DailyAppUsageSummary(
                    hasUsagePermission = false,
                    totalScreenTimeMs = 0L,
                    topApps = emptyList(),
                    refreshedAtWallMs = nowWall
                )
            }

            val zone = clockProvider.zoneId()
            val startOfDayWall = Instant.ofEpochMilli(nowWall)
                .atZone(zone)
                .toLocalDate()
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()

            val usageManager = runCatching {
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            }.getOrNull() ?: return@withContext DailyAppUsageSummary(
                hasUsagePermission = true,
                totalScreenTimeMs = 0L,
                topApps = emptyList(),
                refreshedAtWallMs = nowWall
            )

            val launchablePackages = resolveLaunchablePackages()
            val pm = context.packageManager

            val aggregated = runCatching {
                usageManager.queryAndAggregateUsageStats(startOfDayWall, nowWall)
            }.getOrNull().orEmpty()

            val fallbackStats = if (aggregated.isEmpty()) {
                runCatching {
                    usageManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        startOfDayWall,
                        nowWall
                    )
                }.getOrNull().orEmpty()
                    .groupBy { it.packageName }
                    .mapValues { (_, list) ->
                        list.sumOf { it.totalTimeInForeground.coerceAtLeast(0L) }
                    }
            } else {
                aggregated.mapValues { (_, stats) ->
                    stats.totalTimeInForeground.coerceAtLeast(0L)
                }
            }

            val selfPackage = context.packageName
            val appItems = fallbackStats.entries
                .asSequence()
                .filter { (pkg, timeMs) ->
                    pkg != selfPackage &&
                        timeMs >= MIN_REPORTABLE_MS &&
                        (launchablePackages.isEmpty() || launchablePackages.contains(pkg)) &&
                        !EXCLUDED_SYSTEM_PACKAGES.contains(pkg)
                }
                .map { (pkg, timeMs) ->
                    val label = runCatching {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(appInfo).toString()
                    }.getOrElse { pkg.substringAfterLast('.') }
                    DailyAppUsageItem(
                        packageName = pkg,
                        appName = label,
                        foregroundTimeMs = timeMs
                    )
                }
                .sortedByDescending { it.foregroundTimeMs }
                .toList()

            val totalMs = appItems.sumOf { it.foregroundTimeMs }
            DailyAppUsageSummary(
                hasUsagePermission = true,
                totalScreenTimeMs = totalMs,
                topApps = appItems.take(maxApps.coerceAtLeast(1)),
                refreshedAtWallMs = nowWall
            )
        }

    private fun resolveLaunchablePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return runCatching {
            context.packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    companion object {
        private const val MIN_REPORTABLE_MS = 60_000L
        private val EXCLUDED_SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
        )
    }
}
