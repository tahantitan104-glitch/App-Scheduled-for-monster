package com.example.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.domain.model.InstalledAppInfo
import com.example.telephony.PhoneAccessManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class InstalledAppsRepository(
    private val context: Context,
    private val phoneAccessManager: PhoneAccessManager
) {
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun loadLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            val selfPackage = context.packageName
            val dialerPackages = phoneAccessManager.resolveEssentialDialerPackages().toSet()

            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

            resolveInfos
                .asSequence()
                .mapNotNull { it.activityInfo?.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != selfPackage }
                .map { appInfo ->
                    val label = runCatching { pm.getApplicationLabel(appInfo)?.toString()?.trim() }
                        .getOrNull()
                        ?.takeIf { it.isNotEmpty() } ?: appInfo.packageName
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    InstalledAppInfo(
                        packageName = appInfo.packageName,
                        appName = label,
                        isSystemApp = isSystem,
                        isDialerOrTelecom = dialerPackages.contains(appInfo.packageName)
                    )
                }
                .sortedWith(
                    compareBy<InstalledAppInfo> { it.isDialerOrTelecom }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
                )
                .toList()
        }.getOrDefault(emptyList())
    }

    suspend fun filterExistingLaunchablePackages(packages: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            packages.distinct().filter { pkg ->
                pkg != context.packageName &&
                    runCatching { pm.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)
            }
        }

    suspend fun loadAppIcon(packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        iconCache[packageName]?.let { return@withContext it }
        runCatching {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val bitmap = drawableToBitmap(drawable, sizePx = 96).asImageBitmap()
            iconCache.putIfAbsent(packageName, bitmap) ?: bitmap
        }.getOrNull()
    }

    fun launchAllowedApp(packageName: String): Boolean {
        return runCatching {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val safeSize = sizePx.coerceAtLeast(1)
        if (drawable is BitmapDrawable) {
            val src = drawable.bitmap
            if (src != null && !src.isRecycled && src.width > 0 && src.height > 0) {
                return Bitmap.createScaledBitmap(src, safeSize, safeSize, true)
            }
        }
        val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
