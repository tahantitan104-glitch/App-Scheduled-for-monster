package com.example.platform

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.Build
import android.provider.Settings
import com.example.domain.model.ForceDarkModeApproach
import com.example.domain.model.ForceDarkSupportLevel

enum class DisplayModeAvailability(val badgeLabel: String) {
    DARK_MODE_AVAILABLE("Dark Mode Available"),
    DARK_MODE_UNAVAILABLE("Dark Mode Unavailable"),
    COLOR_INVERSION_AVAILABLE("Color Inversion Available"),
    COLOR_INVERSION_ACTIVE("Color Inversion Active"),
    NOT_SUPPORTED("Not Supported")
}

data class ForceDarkCapabilityReport(
    val androidSdkInt: Int,
    val forceDarkSupportLevel: ForceDarkSupportLevel,
    val colorInversionSupportLevel: ForceDarkSupportLevel,
    val isSystemNightModeActive: Boolean,
    val isColorInversionEnabledInOs: Boolean,
    val perAppDarkModeSupported: Boolean = false,
    val perAppColorInversionSupported: Boolean = false,
    val supportedDarkModeApps: List<String> = emptyList(),
    val supportedColorInversionApps: List<String> = emptyList(),
    val forceDarkSummary: String,
    val colorInversionSummary: String,
    val restoreInstructions: List<String>,
    val deviceManufacturer: String = Build.MANUFACTURER.orEmpty(),
    val deviceModel: String = Build.MODEL.orEmpty(),
    val androidRelease: String = Build.VERSION.RELEASE.orEmpty(),
    val inAppRenderEffectSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    val protectedSurfaceNote: String = DEFAULT_PROTECTED_SURFACE_NOTE
) {
    companion object {
        const val DEFAULT_PROTECTED_SURFACE_NOTE =
            "Protected/FLAG_SECURE windows, DRM video streams, and hardware overlay surfaces cannot be color-transformed by external apps on Android."
    }
}

sealed interface ModeOperationResult {
    data object Applied : ModeOperationResult
    data class Unsupported(val reason: String) : ModeOperationResult
}

/**
 * Inspects actual Android platform capabilities for:
 * 1) Dark Mode (per-app third-party override is unavailable on stock non-root Android)
 * 2) Genuine Mathematical Color Inversion (R'=255-R, G'=255-G, B'=255-B with alpha preserved),
 *    supporting live ColorMatrix transformation inside RESOLVE and honest Accessibility Color
 *    Inversion integration for system display output without overwriting pre-existing OS settings.
 */
object ForceDarkCapabilityHelper {

    /**
     * Standard 4x5 mathematical RGB inversion matrix:
     * R' = -R + 255, G' = -G + 255, B' = -B + 255, A' = A
     */
    val RGB_INVERSION_COLOR_MATRIX: FloatArray = floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )

    fun invertRgbChannel(channel: Int): Int = 255 - channel.coerceIn(0, 255)

    fun invertRgbTriplet(r: Int, g: Int, b: Int): Triple<Int, Int, Int> =
        Triple(invertRgbChannel(r), invertRgbChannel(g), invertRgbChannel(b))

    fun createInversionRenderEffect(): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val matrix = ColorMatrix(RGB_INVERSION_COLOR_MATRIX)
        return RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))
    }

    fun isAccessibilityInversionEnabled(context: Context): Boolean = runCatching {
        Settings.Secure.getInt(
            context.contentResolver,
            "accessibility_display_inversion_enabled",
            0
        ) == 1
    }.getOrDefault(false)

    fun inspect(context: Context): ForceDarkCapabilityReport {
        val sdk = Build.VERSION.SDK_INT
        val uiModeManager = runCatching {
            context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        }.getOrNull()

        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isNightActive = nightMask == Configuration.UI_MODE_NIGHT_YES ||
            uiModeManager?.nightMode == UiModeManager.MODE_NIGHT_YES

        val forceDarkLevel = when {
            sdk >= Build.VERSION_CODES.Q -> ForceDarkSupportLevel.PARTIALLY_SUPPORTED
            else -> ForceDarkSupportLevel.UNSUPPORTED
        }

        val forceDarkSummary =
            "RESOLVE always stays in its native Dark Mode interface. For other third-party apps on Android ${Build.VERSION.RELEASE.orEmpty()} (API $sdk), non-root apps cannot force Dark Mode into another app; only apps with native Dark Theme follow System Display Settings."

        val isInversionOn = isAccessibilityInversionEnabled(context)

        val inversionSummary =
            "True mathematical RGB inversion (R'=255-R, G'=255-G, B'=255-B) is available as a live ColorMatrix transformation inside RESOLVE, and device-wide via authorized Android Accessibility Display Color Inversion."

        val restoreSteps = listOf(
            "1. Disable In-App Color Inversion in RESOLVE Modes anytime to restore normal colors immediately.",
            "2. If System Display Color Inversion was turned on in Android Accessibility Settings, toggle it back OFF there—RESOLVE never overwrites your pre-existing OS state."
        )

        return ForceDarkCapabilityReport(
            androidSdkInt = sdk,
            forceDarkSupportLevel = forceDarkLevel,
            colorInversionSupportLevel = ForceDarkSupportLevel.SUPPORTED,
            isSystemNightModeActive = isNightActive,
            isColorInversionEnabledInOs = isInversionOn,
            perAppDarkModeSupported = false,
            perAppColorInversionSupported = false,
            supportedDarkModeApps = emptyList(),
            supportedColorInversionApps = emptyList(),
            forceDarkSummary = forceDarkSummary,
            colorInversionSummary = inversionSummary,
            restoreInstructions = restoreSteps,
            deviceManufacturer = Build.MANUFACTURER.orEmpty(),
            deviceModel = Build.MODEL.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            inAppRenderEffectSupported = sdk >= Build.VERSION_CODES.S
        )
    }

    fun resolveDarkModeStatus(
        packageName: String,
        report: ForceDarkCapabilityReport
    ): DisplayModeAvailability {
        return if (report.supportedDarkModeApps.contains(packageName)) {
            DisplayModeAvailability.DARK_MODE_AVAILABLE
        } else {
            DisplayModeAvailability.NOT_SUPPORTED
        }
    }

    fun resolveColorInversionStatus(
        packageName: String,
        report: ForceDarkCapabilityReport,
        inAppPreviewActive: Boolean,
        isOwnAppPackage: Boolean
    ): DisplayModeAvailability {
        return when {
            isOwnAppPackage && (inAppPreviewActive || report.isColorInversionEnabledInOs) ->
                DisplayModeAvailability.COLOR_INVERSION_ACTIVE
            isOwnAppPackage ->
                DisplayModeAvailability.COLOR_INVERSION_AVAILABLE
            report.isColorInversionEnabledInOs ->
                DisplayModeAvailability.COLOR_INVERSION_ACTIVE
            report.supportedColorInversionApps.contains(packageName) ->
                DisplayModeAvailability.COLOR_INVERSION_AVAILABLE
            else ->
                DisplayModeAvailability.NOT_SUPPORTED
        }
    }

    fun tryApplyPerAppDarkMode(
        packageName: String,
        supportedApps: List<String> = emptyList()
    ): ModeOperationResult = if (supportedApps.contains(packageName)) {
        ModeOperationResult.Applied
    } else {
        ModeOperationResult.Unsupported("Not Supported — this app manages Dark Mode independently on Android.")
    }

    fun tryApplyPerAppColorInversion(
        packageName: String,
        supportedApps: List<String> = emptyList()
    ): ModeOperationResult = if (supportedApps.contains(packageName)) {
        ModeOperationResult.Applied
    } else {
        ModeOperationResult.Unsupported(
            "Not Supported per-app on non-root Android. Use In-App Inversion Preview or System Accessibility Color Inversion with consent."
        )
    }

    fun supportLabel(level: ForceDarkSupportLevel): String = when (level) {
        ForceDarkSupportLevel.SUPPORTED -> "Supported"
        ForceDarkSupportLevel.PARTIALLY_SUPPORTED -> "OS Settings Only"
        ForceDarkSupportLevel.UNSUPPORTED -> "Unsupported"
    }

    fun supportLevelForApproach(
        report: ForceDarkCapabilityReport,
        approach: ForceDarkModeApproach
    ): ForceDarkSupportLevel = when (approach) {
        ForceDarkModeApproach.FORCE_DARK -> report.forceDarkSupportLevel
        ForceDarkModeApproach.COLOR_INVERSION -> report.colorInversionSupportLevel
    }

    fun openDisplaySettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openAccessibilityDisplaySettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}

